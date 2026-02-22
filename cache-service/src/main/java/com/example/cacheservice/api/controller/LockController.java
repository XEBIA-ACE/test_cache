package com.example.cacheservice.api.controller;

import com.example.cacheservice.api.dto.ErrorResponse;
import com.example.cacheservice.api.dto.LockRequest;
import com.example.cacheservice.business.service.LockService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * REST controller for distributed lock operations.
 *
 * <p>Uses Redisson-backed reentrant locks stored in Redis.
 * Locks are automatically released after the configured lease time,
 * preventing deadlocks if the client crashes.
 *
 * <p>Base path: {@code /api/v1/locks}
 */
@Slf4j
@Validated
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/locks")
@Tag(name = "Distributed Locks", description = "Distributed lock management via Redisson")
public class LockController {

    private final LockService lockService;

    @Operation(
        summary = "Acquire a distributed lock",
        description = """
            Attempts to acquire a lock for the given key.
            Returns 200 if acquired, 409 if the lock is already held and wait timed out.
            The lock auto-releases after `leaseTimeMs` milliseconds to prevent deadlocks.
            """
    )
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Lock acquired successfully"),
        @ApiResponse(responseCode = "409", description = "Lock not available (held by another process)",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
        @ApiResponse(responseCode = "503", description = "Redis unavailable",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    @PostMapping("/{lockKey}/acquire")
    public ResponseEntity<Map<String, Object>> acquire(
        @PathVariable
        @NotBlank
        @Parameter(description = "Unique lock identifier", example = "order:processing:123")
        String lockKey,

        @Valid @RequestBody(required = false) LockRequest request
    ) {
        LockRequest effectiveRequest = request != null ? request : new LockRequest(null, null);

        boolean acquired = lockService.tryAcquire(
            lockKey,
            effectiveRequest.effectiveWaitTimeMs(),
            effectiveRequest.effectiveLeaseTimeMs(),
            TimeUnit.MILLISECONDS
        );

        if (acquired) {
            log.info("Lock acquired via API: key={}", lockKey);
            return ResponseEntity.ok(Map.of(
                "lockKey", lockKey,
                "acquired", true,
                "leaseTimeMs", effectiveRequest.effectiveLeaseTimeMs()
            ));
        } else {
            log.warn("Lock acquisition failed via API: key={}", lockKey);
            return ResponseEntity
                .status(HttpStatus.CONFLICT)
                .body(Map.of(
                    "lockKey", lockKey,
                    "acquired", false,
                    "message", "Lock is currently held by another process. Try again later."
                ));
        }
    }

    @Operation(
        summary = "Release a distributed lock",
        description = "Releases the lock for the given key. Only the thread that acquired the lock can release it."
    )
    @ApiResponses({
        @ApiResponse(responseCode = "204", description = "Lock released successfully"),
        @ApiResponse(responseCode = "404", description = "Lock not held")
    })
    @DeleteMapping("/{lockKey}/release")
    public ResponseEntity<Void> release(
        @PathVariable
        @NotBlank
        @Parameter(description = "The lock key to release", example = "order:processing:123")
        String lockKey
    ) {
        lockService.release(lockKey);
        return ResponseEntity.noContent().build();
    }

    @Operation(summary = "Check lock status")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Lock status returned")
    })
    @GetMapping("/{lockKey}/status")
    public ResponseEntity<Map<String, Object>> status(
        @PathVariable
        @NotBlank
        @Parameter(description = "The lock key to check", example = "order:processing:123")
        String lockKey
    ) {
        boolean locked = lockService.isLocked(lockKey);
        boolean heldByCurrentThread = lockService.isHeldByCurrentThread(lockKey);
        int holdCount = lockService.getHoldCount(lockKey);

        return ResponseEntity.ok(Map.of(
            "lockKey", lockKey,
            "locked", locked,
            "heldByCurrentThread", heldByCurrentThread,
            "holdCount", holdCount
        ));
    }
}
