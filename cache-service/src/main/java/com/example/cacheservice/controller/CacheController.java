package com.example.cacheservice.controller;

import com.example.cacheservice.dto.request.BulkSetRequest;
import com.example.cacheservice.dto.request.CacheSetRequest;
import com.example.cacheservice.dto.request.HashSetRequest;
import com.example.cacheservice.dto.response.ApiResponse;
import com.example.cacheservice.dto.response.CacheEntryResponse;
import com.example.cacheservice.exception.CacheKeyNotFoundException;
import com.example.cacheservice.service.CacheService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * REST controller for cache CRUD, bulk, hash, counter, and scan operations.
 *
 * <p>All cache operations are delegated to {@link CacheService} which is backed
 * by Lettuce (Spring Data Redis). The controller is responsible only for:
 * <ul>
 *   <li>HTTP binding and response-code mapping</li>
 *   <li>Input validation via Bean Validation annotations</li>
 *   <li>Structuring responses into the {@link ApiResponse} envelope</li>
 * </ul>
 */
@Slf4j
@Validated
@RestController
@RequestMapping("/api/v1/cache")
@RequiredArgsConstructor
@Tag(name = "Cache", description = "Cache CRUD, bulk, hash, and counter operations (Lettuce/Redis)")
public class CacheController {

    private final CacheService cacheService;

    // ═══════════════════════════════════════════════════════════════════════════
    // String / Generic operations
    // ═══════════════════════════════════════════════════════════════════════════

    @Operation(summary = "Get a cached value by key")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Key found"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "Key not found")
    })
    @GetMapping("/{key}")
    public ResponseEntity<ApiResponse<CacheEntryResponse>> get(
            @PathVariable @NotBlank @Parameter(description = "Cache key", example = "user:42") String key) {

        return cacheService.get(key)
                .map(value -> {
                    long ttl = cacheService.ttl(key);
                    CacheEntryResponse body = CacheEntryResponse.builder()
                            .key(key)
                            .value(value)
                            .ttlSeconds(ttl)
                            .exists(true)
                            .build();
                    return ResponseEntity.ok(ApiResponse.ok(body, "Key found"));
                })
                .orElseThrow(() -> new CacheKeyNotFoundException(key));
    }

    @Operation(summary = "Store a value (create or overwrite)")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Stored successfully"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "Invalid request")
    })
    @PutMapping("/{key}")
    public ResponseEntity<ApiResponse<CacheEntryResponse>> set(
            @PathVariable @NotBlank String key,
            @Valid @RequestBody CacheSetRequest request) {

        cacheService.set(key, request.getValue(), request.getTtlSeconds());

        CacheEntryResponse body = CacheEntryResponse.builder()
                .key(key)
                .value(request.getValue())
                .ttlSeconds(request.getTtlSeconds() > 0 ? request.getTtlSeconds() : -1L)
                .exists(true)
                .build();
        return ResponseEntity.ok(ApiResponse.ok(body, "Key stored successfully"));
    }

    @Operation(summary = "Store a value only if the key does NOT already exist (SET NX EX)")
    @PutMapping("/{key}/nx")
    public ResponseEntity<ApiResponse<Void>> setIfAbsent(
            @PathVariable @NotBlank String key,
            @Valid @RequestBody CacheSetRequest request) {

        boolean stored = cacheService.setIfAbsent(key, request.getValue(), request.getTtlSeconds());
        if (stored) {
            return ResponseEntity.status(HttpStatus.CREATED)
                    .body(ApiResponse.ok("Key created (was absent)"));
        } else {
            return ResponseEntity.status(HttpStatus.CONFLICT)
                    .body(ApiResponse.error("Key already exists", "KEY_EXISTS"));
        }
    }

    @Operation(summary = "Delete a cache key")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Key deleted"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "Key not found")
    })
    @DeleteMapping("/{key}")
    public ResponseEntity<ApiResponse<Void>> delete(
            @PathVariable @NotBlank String key) {

        boolean deleted = cacheService.delete(key);
        if (deleted) {
            return ResponseEntity.ok(ApiResponse.ok("Key deleted"));
        } else {
            throw new CacheKeyNotFoundException(key);
        }
    }

    @Operation(summary = "Check whether a key exists")
    @GetMapping("/{key}/exists")
    public ResponseEntity<ApiResponse<Boolean>> exists(
            @PathVariable @NotBlank String key) {
        boolean exists = cacheService.exists(key);
        return ResponseEntity.ok(ApiResponse.ok(exists, exists ? "Key exists" : "Key not found"));
    }

    @Operation(summary = "Get remaining TTL of a key in seconds (-1 = no expiry, -2 = absent)")
    @GetMapping("/{key}/ttl")
    public ResponseEntity<ApiResponse<Long>> getTtl(
            @PathVariable @NotBlank String key) {
        long ttl = cacheService.ttl(key);
        return ResponseEntity.ok(ApiResponse.ok(ttl, "TTL retrieved"));
    }

    @Operation(summary = "Update the TTL of an existing key")
    @PatchMapping("/{key}/ttl")
    public ResponseEntity<ApiResponse<Void>> updateTtl(
            @PathVariable @NotBlank String key,
            @RequestParam("seconds") @Min(1) long seconds) {

        boolean updated = cacheService.expire(key, seconds);
        if (updated) {
            return ResponseEntity.ok(ApiResponse.ok("TTL updated"));
        } else {
            throw new CacheKeyNotFoundException(key);
        }
    }

    @Operation(summary = "Remove expiry from a key (make it persistent)")
    @DeleteMapping("/{key}/ttl")
    public ResponseEntity<ApiResponse<Void>> removeTtl(
            @PathVariable @NotBlank String key) {

        boolean persisted = cacheService.persist(key);
        if (persisted) {
            return ResponseEntity.ok(ApiResponse.ok("Key made persistent"));
        } else {
            throw new CacheKeyNotFoundException(key);
        }
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // Scan / Bulk operations
    // ═══════════════════════════════════════════════════════════════════════════

    @Operation(summary = "Scan for keys matching a glob pattern (SCAN, not KEYS)")
    @GetMapping("/scan")
    public ResponseEntity<ApiResponse<Set<String>>> scan(
            @RequestParam(defaultValue = "*")
            @Pattern(regexp = "^[^\\r\\n]{1,512}$", message = "Pattern must be 1–512 characters")
            @Parameter(description = "Redis glob pattern", example = "user:*") String pattern,

            @RequestParam(defaultValue = "100") @Min(1) @Max(10_000)
            @Parameter(description = "Maximum keys to return") int limit) {

        Set<String> keys = cacheService.scan(pattern, limit);
        return ResponseEntity.ok(ApiResponse.ok(keys,
                "Found " + keys.size() + " key(s) matching '" + pattern + "'"));
    }

    @Operation(summary = "Delete all keys matching a pattern")
    @DeleteMapping("/scan")
    public ResponseEntity<ApiResponse<Long>> deleteByPattern(
            @RequestParam @Pattern(regexp = "^[^\\r\\n]{1,512}$") String pattern) {

        long deleted = cacheService.deleteByPattern(pattern);
        return ResponseEntity.ok(ApiResponse.ok(deleted, deleted + " key(s) deleted"));
    }

    @Operation(summary = "Bulk GET – fetch multiple keys in one request (MGET)")
    @PostMapping("/bulk/get")
    public ResponseEntity<ApiResponse<Map<String, String>>> bulkGet(
            @RequestBody List<@NotBlank String> keys) {

        if (keys.size() > 1000) {
            throw new IllegalArgumentException("At most 1000 keys per bulk-get request");
        }
        Map<String, String> result = cacheService.mget(keys);
        return ResponseEntity.ok(ApiResponse.ok(result, result.size() + " key(s) fetched"));
    }

    @Operation(summary = "Bulk SET – write multiple keys in one request (MSET / pipeline)")
    @PostMapping("/bulk/set")
    public ResponseEntity<ApiResponse<Void>> bulkSet(
            @Valid @RequestBody BulkSetRequest request) {

        if (request.getTtlSeconds() > 0) {
            cacheService.msetWithTtl(request.getEntries(), request.getTtlSeconds());
        } else {
            cacheService.mset(request.getEntries());
        }
        return ResponseEntity.ok(ApiResponse.ok(request.getEntries().size() + " key(s) stored"));
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // Hash operations
    // ═══════════════════════════════════════════════════════════════════════════

    @Operation(summary = "Get all fields of a hash")
    @GetMapping("/{key}/hash")
    public ResponseEntity<ApiResponse<Map<Object, Object>>> hgetAll(
            @PathVariable @NotBlank String key) {

        Map<Object, Object> fields = cacheService.hgetAll(key);
        if (fields.isEmpty()) {
            throw new CacheKeyNotFoundException(key);
        }
        return ResponseEntity.ok(ApiResponse.ok(fields, "Hash fields retrieved"));
    }

    @Operation(summary = "Get a single hash field")
    @GetMapping("/{key}/hash/{field}")
    public ResponseEntity<ApiResponse<Object>> hget(
            @PathVariable @NotBlank String key,
            @PathVariable @NotBlank String field) {

        return cacheService.hget(key, field)
                .map(value -> ResponseEntity.ok(ApiResponse.ok(value, "Hash field retrieved")))
                .orElseThrow(() -> new CacheKeyNotFoundException(key + " -> " + field));
    }

    @Operation(summary = "Set a single hash field")
    @PutMapping("/{key}/hash/{field}")
    public ResponseEntity<ApiResponse<Void>> hset(
            @PathVariable @NotBlank String key,
            @PathVariable @NotBlank String field,
            @Valid @RequestBody HashSetRequest request) {

        cacheService.hset(key, field, request.getValue());
        return ResponseEntity.ok(ApiResponse.ok("Hash field stored"));
    }

    @Operation(summary = "Delete a single hash field")
    @DeleteMapping("/{key}/hash/{field}")
    public ResponseEntity<ApiResponse<Void>> hdel(
            @PathVariable @NotBlank String key,
            @PathVariable @NotBlank String field) {

        boolean deleted = cacheService.hdel(key, field);
        if (deleted) {
            return ResponseEntity.ok(ApiResponse.ok("Hash field deleted"));
        } else {
            throw new CacheKeyNotFoundException(key + " -> " + field);
        }
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // Counter operations
    // ═══════════════════════════════════════════════════════════════════════════

    @Operation(summary = "Increment a counter by 1 (INCR)")
    @PostMapping("/{key}/increment")
    public ResponseEntity<ApiResponse<Long>> increment(
            @PathVariable @NotBlank String key) {
        long value = cacheService.increment(key);
        return ResponseEntity.ok(ApiResponse.ok(value, "Counter incremented"));
    }

    @Operation(summary = "Increment a counter by a given delta (INCRBY)")
    @PostMapping("/{key}/increment/{delta}")
    public ResponseEntity<ApiResponse<Long>> incrementBy(
            @PathVariable @NotBlank String key,
            @PathVariable long delta) {
        long value = cacheService.incrementBy(key, delta);
        return ResponseEntity.ok(ApiResponse.ok(value, "Counter incremented by " + delta));
    }

    @Operation(summary = "Decrement a counter by 1 (DECR)")
    @PostMapping("/{key}/decrement")
    public ResponseEntity<ApiResponse<Long>> decrement(
            @PathVariable @NotBlank String key) {
        long value = cacheService.decrement(key);
        return ResponseEntity.ok(ApiResponse.ok(value, "Counter decremented"));
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // Stats / Info
    // ═══════════════════════════════════════════════════════════════════════════

    @Operation(summary = "Get Redis server info (subset of INFO output)")
    @GetMapping("/info")
    public ResponseEntity<ApiResponse<Map<String, String>>> serverInfo() {
        Map<String, String> info = cacheService.getServerInfo();
        return ResponseEntity.ok(ApiResponse.ok(info, "Server info retrieved"));
    }
}
