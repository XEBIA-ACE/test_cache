package com.example.cacheservice.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Min;

/**
 * Request body for acquiring a distributed lock.
 */
@Schema(description = "Request to acquire a distributed lock")
public record LockRequest(

    @Min(value = 0, message = "Wait time must be non-negative")
    @Schema(
        description = "Maximum time to wait for the lock in milliseconds. 0 = fail immediately if not available.",
        example = "5000",
        defaultValue = "5000"
    )
    Long waitTimeMs,

    @Min(value = 1, message = "Lease time must be at least 1ms")
    @Schema(
        description = "Maximum time the lock will be held in milliseconds (auto-release on crash).",
        example = "30000",
        defaultValue = "30000"
    )
    Long leaseTimeMs
) {
    public long effectiveWaitTimeMs() {
        return waitTimeMs != null ? waitTimeMs : 5000L;
    }

    public long effectiveLeaseTimeMs() {
        return leaseTimeMs != null ? leaseTimeMs : 30000L;
    }
}
