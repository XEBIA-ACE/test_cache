package com.example.cacheservice.api.controller;

import com.example.cacheservice.api.dto.*;
import com.example.cacheservice.business.model.CacheEntry;
import com.example.cacheservice.business.service.CacheService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * REST controller for cache CRUD operations.
 *
 * <p>All endpoints follow REST semantics:
 * <ul>
 *   <li>GET  – Read (idempotent)</li>
 *   <li>PUT  – Create or update (idempotent)</li>
 *   <li>PATCH – Partial update (TTL only)</li>
 *   <li>DELETE – Delete</li>
 *   <li>HEAD – Existence check</li>
 *   <li>POST – Non-idempotent operations (bulk)</li>
 * </ul>
 *
 * <p>Base path: {@code /api/v1/cache}
 */
@Slf4j
@Validated
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/cache")
@Tag(name = "Cache", description = "Cache CRUD operations")
public class CacheController {

    private final CacheService cacheService;

    // ----------------------------------------------------------------
    // Single key operations
    // ----------------------------------------------------------------

    @Operation(summary = "Get a cached value by key")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Key found"),
        @ApiResponse(responseCode = "404", description = "Key not found",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
        @ApiResponse(responseCode = "503", description = "Cache unavailable",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    @GetMapping("/{key}")
    public ResponseEntity<CacheEntryResponse> get(
        @PathVariable
        @NotBlank
        @Size(max = 512)
        @Parameter(description = "The cache key", example = "user:42")
        String key
    ) {
        Optional<CacheEntry> entry = cacheService.get(key);
        return entry
            .map(e -> ResponseEntity.ok(CacheEntryResponse.from(e)))
            .orElse(ResponseEntity.notFound().build());
    }

    @Operation(summary = "Create or update a cache entry")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Entry stored successfully"),
        @ApiResponse(responseCode = "400", description = "Invalid request",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
        @ApiResponse(responseCode = "503", description = "Cache unavailable",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    @PutMapping("/{key}")
    public ResponseEntity<CacheEntryResponse> set(
        @PathVariable
        @NotBlank
        @Size(max = 512)
        @Parameter(description = "The cache key", example = "user:42")
        String key,

        @Valid @RequestBody CacheEntryRequest request
    ) {
        Duration ttl = Duration.ofSeconds(request.effectiveTtlSeconds());
        CacheEntry entry = cacheService.set(key, request.value(), ttl);
        return ResponseEntity.ok(CacheEntryResponse.from(entry));
    }

    @Operation(summary = "Delete a cached key")
    @ApiResponses({
        @ApiResponse(responseCode = "204", description = "Key deleted"),
        @ApiResponse(responseCode = "404", description = "Key not found",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    @DeleteMapping("/{key}")
    public ResponseEntity<Void> delete(
        @PathVariable
        @NotBlank
        @Parameter(description = "The cache key to delete", example = "user:42")
        String key
    ) {
        boolean deleted = cacheService.delete(key);
        return deleted
            ? ResponseEntity.noContent().build()
            : ResponseEntity.notFound().build();
    }

    @Operation(summary = "Check if a key exists (existence probe)")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Key exists"),
        @ApiResponse(responseCode = "404", description = "Key does not exist")
    })
    @GetMapping("/{key}/exists")
    public ResponseEntity<Void> exists(
        @PathVariable
        @NotBlank
        @Parameter(description = "The cache key to check", example = "user:42")
        String key
    ) {
        return cacheService.exists(key)
            ? ResponseEntity.ok().build()
            : ResponseEntity.notFound().build();
    }

    @Operation(summary = "Get the TTL (time-to-live) of a key")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "TTL retrieved"),
        @ApiResponse(responseCode = "404", description = "Key not found")
    })
    @GetMapping("/{key}/ttl")
    public ResponseEntity<Map<String, Long>> getTtl(
        @PathVariable
        @NotBlank
        @Parameter(description = "The cache key", example = "user:42")
        String key
    ) {
        long ttl = cacheService.getTtl(key);
        if (ttl == -2) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok(Map.of("ttlSeconds", ttl));
    }

    @Operation(
        summary = "Update the TTL of a key",
        description = "Use ttlSeconds=0 to make the key persistent (remove expiry)."
    )
    @ApiResponses({
        @ApiResponse(responseCode = "204", description = "TTL updated"),
        @ApiResponse(responseCode = "404", description = "Key not found"),
        @ApiResponse(responseCode = "400", description = "Invalid TTL value")
    })
    @PatchMapping("/{key}/ttl")
    public ResponseEntity<Void> updateTtl(
        @PathVariable
        @NotBlank
        @Parameter(description = "The cache key", example = "user:42")
        String key,

        @Valid @RequestBody TtlUpdateRequest request
    ) {
        Duration ttl = Duration.ofSeconds(request.ttlSeconds());
        boolean updated = cacheService.expire(key, ttl);
        return updated
            ? ResponseEntity.noContent().build()
            : ResponseEntity.notFound().build();
    }

    @Operation(summary = "Atomically increment (or decrement) a counter")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Counter updated"),
        @ApiResponse(responseCode = "400", description = "Invalid delta value")
    })
    @PostMapping("/{key}/increment")
    public ResponseEntity<Map<String, Long>> increment(
        @PathVariable
        @NotBlank
        @Parameter(description = "The counter key", example = "counter:views")
        String key,

        @RequestParam(defaultValue = "1")
        @Parameter(description = "Amount to increment (use negative to decrement)", example = "1")
        long delta
    ) {
        long newValue = cacheService.increment(key, delta);
        return ResponseEntity.ok(Map.of("value", newValue));
    }

    // ----------------------------------------------------------------
    // Key scanning
    // ----------------------------------------------------------------

    @Operation(
        summary = "Scan keys by pattern",
        description = "Returns all keys matching the glob pattern. Uses SCAN (non-blocking). " +
                      "Pattern examples: 'user:*', 'session:????', 'config:[abc]*'."
    )
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Keys matching the pattern"),
        @ApiResponse(responseCode = "400", description = "Invalid or too broad pattern")
    })
    @GetMapping
    public ResponseEntity<Map<String, Object>> scan(
        @RequestParam
        @NotBlank
        @Parameter(description = "Glob pattern to match keys", example = "user:*")
        String pattern
    ) {
        Set<String> keys = cacheService.keys(pattern);
        return ResponseEntity.ok(Map.of(
            "pattern", pattern,
            "count", keys.size(),
            "keys", keys
        ));
    }

    @Operation(
        summary = "Delete keys by pattern",
        description = "Deletes all keys matching the glob pattern. Use with caution."
    )
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Keys deleted"),
        @ApiResponse(responseCode = "400", description = "Invalid or too broad pattern")
    })
    @DeleteMapping
    public ResponseEntity<Map<String, Long>> deleteByPattern(
        @RequestParam
        @NotBlank
        @Parameter(description = "Glob pattern of keys to delete", example = "session:expired:*")
        String pattern
    ) {
        long deleted = cacheService.deleteByPattern(pattern);
        return ResponseEntity.ok(Map.of("deleted", deleted));
    }

    // ----------------------------------------------------------------
    // Bulk operations
    // ----------------------------------------------------------------

    @Operation(summary = "Retrieve multiple cache entries by key list")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Values retrieved (missing keys are omitted from response)"),
        @ApiResponse(responseCode = "400", description = "Invalid request")
    })
    @PostMapping("/bulk/get")
    public ResponseEntity<Map<String, CacheEntryResponse>> bulkGet(
        @Valid @RequestBody BulkGetRequest request
    ) {
        Map<String, CacheEntry> entries = cacheService.bulkGet(request.keys());
        Map<String, CacheEntryResponse> response = new java.util.LinkedHashMap<>();
        entries.forEach((k, v) -> response.put(k, CacheEntryResponse.from(v)));
        return ResponseEntity.ok(response);
    }

    @Operation(summary = "Store multiple cache entries in one request")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "All entries stored"),
        @ApiResponse(responseCode = "400", description = "Invalid request (too many entries, value too large, etc.)")
    })
    @PostMapping("/bulk/set")
    public ResponseEntity<List<CacheEntryResponse>> bulkSet(
        @Valid @RequestBody BulkSetRequest request
    ) {
        Duration ttl = Duration.ofSeconds(request.effectiveTtlSeconds());
        List<CacheEntry> entries = cacheService.bulkSet(request.entries(), ttl);
        List<CacheEntryResponse> response = entries.stream()
            .map(CacheEntryResponse::from)
            .toList();
        return ResponseEntity.ok(response);
    }

    @Operation(summary = "Delete multiple cache entries by key list")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Delete count returned"),
        @ApiResponse(responseCode = "400", description = "Invalid request")
    })
    @DeleteMapping("/bulk")
    public ResponseEntity<Map<String, Long>> bulkDelete(
        @Valid @RequestBody BulkDeleteRequest request
    ) {
        long deleted = cacheService.bulkDelete(request.keys());
        return ResponseEntity.ok(Map.of("deleted", deleted));
    }

    // ----------------------------------------------------------------
    // Stats
    // ----------------------------------------------------------------

    @Operation(summary = "Get total number of keys in the cache")
    @GetMapping("/stats/size")
    public ResponseEntity<Map<String, Long>> size() {
        return ResponseEntity.ok(Map.of("totalKeys", cacheService.size()));
    }
}
