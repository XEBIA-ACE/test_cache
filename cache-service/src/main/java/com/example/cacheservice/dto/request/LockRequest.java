package com.example.cacheservice.dto.request;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Positive;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Request body for acquiring a distributed lock.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Parameters for distributed lock acquisition")
public class LockRequest {

    /**
     * Maximum time (ms) to wait before giving up on acquiring the lock.
     * 0 means try-and-return-immediately (non-blocking).
     */
    @Min(value = 0, message = "waitTimeMs must be >= 0")
    @JsonProperty("wait_time_ms")
    @Schema(description = "Max wait time in ms (0 = non-blocking)", example = "0", defaultValue = "0")
    private long waitTimeMs = 0;

    /**
     * How long (ms) to hold the lock before it is auto-released by Redis.
     * Redisson's watch-dog extends this automatically for blocking locks.
     */
    @Positive(message = "leaseTimeMs must be > 0")
    @JsonProperty("lease_time_ms")
    @Schema(description = "Lock lease duration in ms", example = "30000", defaultValue = "30000")
    private long leaseTimeMs = 30_000;
}
