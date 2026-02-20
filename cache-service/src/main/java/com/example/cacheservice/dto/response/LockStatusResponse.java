package com.example.cacheservice.dto.response;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Status response returned after lock operations.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Result of a distributed lock operation")
public class LockStatusResponse {

    @JsonProperty("lock_name")
    @Schema(description = "Logical lock name", example = "order-processing")
    private String lockName;

    @Schema(description = "Whether the lock is currently held", example = "true")
    private boolean locked;

    @Schema(description = "Whether the lock was acquired by this request", example = "true")
    private boolean acquired;

    @JsonProperty("lease_time_ms")
    @Schema(description = "Lease duration in milliseconds", example = "30000")
    private long leaseTimeMs;
}
