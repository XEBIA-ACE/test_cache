package com.example.cacheservice.controller;

import com.example.cacheservice.dto.request.LockRequest;
import com.example.cacheservice.dto.response.ApiResponse;
import com.example.cacheservice.dto.response.LockStatusResponse;
import com.example.cacheservice.exception.LockAcquisitionException;
import com.example.cacheservice.service.LockService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.concurrent.TimeUnit;

/**
 * REST controller exposing distributed lock operations backed by Redisson.
 *
 * <p><b>Important note on HTTP statelessness:</b><br>
 * Distributed locks are inherently tied to the acquiring thread/process.
 * Over HTTP each request may land on a different JVM thread, so callers
 * must:
 * <ul>
 *   <li>Use the <em>non-blocking</em> ({@code waitTimeMs = 0}) strategy by
 *       default and retry in application logic.</li>
 *   <li>Set a reasonable {@code leaseTimeMs} so locks are auto-released if
 *       the client crashes before calling {@code DELETE}.</li>
 * </ul>
 */
@Slf4j
@Validated
@RestController
@RequestMapping("/api/v1/locks")
@RequiredArgsConstructor
@Tag(name = "Distributed Locks", description = "Distributed lock operations powered by Redisson")
public class LockController {

    private final LockService lockService;

    @Operation(summary = "Acquire a distributed lock",
               description = "Returns 200 if acquired, 409 if the lock is already held")
    @PostMapping("/{lockName}")
    public ResponseEntity<ApiResponse<LockStatusResponse>> acquire(
            @PathVariable @NotBlank String lockName,
            @Valid @RequestBody(required = false) LockRequest request) {

        if (request == null) {
            request = new LockRequest(0, 30_000); // sensible defaults
        }

        try {
            boolean acquired;
            if (request.getWaitTimeMs() > 0) {
                acquired = lockService.lock(lockName,
                        request.getWaitTimeMs(), request.getLeaseTimeMs(), TimeUnit.MILLISECONDS);
            } else {
                acquired = lockService.tryLock(lockName,
                        request.getLeaseTimeMs(), TimeUnit.MILLISECONDS);
            }

            if (acquired) {
                LockStatusResponse body = LockStatusResponse.builder()
                        .lockName(lockName)
                        .locked(true)
                        .acquired(true)
                        .leaseTimeMs(request.getLeaseTimeMs())
                        .build();
                return ResponseEntity.ok(ApiResponse.ok(body, "Lock acquired"));
            } else {
                LockStatusResponse body = LockStatusResponse.builder()
                        .lockName(lockName)
                        .locked(true)
                        .acquired(false)
                        .leaseTimeMs(request.getLeaseTimeMs())
                        .build();
                return ResponseEntity.status(HttpStatus.CONFLICT)
                        .body(ApiResponse.error("Lock is already held", "LOCK_NOT_AVAILABLE"));
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new LockAcquisitionException(lockName, e);
        }
    }

    @Operation(summary = "Release a distributed lock",
               description = "Returns 403 if the lock is not held by the current thread")
    @DeleteMapping("/{lockName}")
    public ResponseEntity<ApiResponse<Void>> release(
            @PathVariable @NotBlank String lockName) {

        lockService.unlock(lockName);
        return ResponseEntity.ok(ApiResponse.ok("Lock released"));
    }

    @Operation(summary = "Check the status of a lock")
    @GetMapping("/{lockName}")
    public ResponseEntity<ApiResponse<LockStatusResponse>> status(
            @PathVariable @NotBlank String lockName) {

        boolean locked = lockService.isLocked(lockName);
        LockStatusResponse body = LockStatusResponse.builder()
                .lockName(lockName)
                .locked(locked)
                .acquired(false)
                .build();
        return ResponseEntity.ok(ApiResponse.ok(body, locked ? "Lock is held" : "Lock is free"));
    }

    @Operation(summary = "Force-release a lock regardless of owner (admin use only)")
    @DeleteMapping("/{lockName}/force")
    public ResponseEntity<ApiResponse<Void>> forceRelease(
            @PathVariable @NotBlank String lockName) {

        log.warn("Force-releasing lock: {}", lockName);
        boolean released = lockService.forceUnlock(lockName);
        if (released) {
            return ResponseEntity.ok(ApiResponse.ok("Lock force-released"));
        } else {
            return ResponseEntity.ok(ApiResponse.ok("Lock was not held"));
        }
    }
}
