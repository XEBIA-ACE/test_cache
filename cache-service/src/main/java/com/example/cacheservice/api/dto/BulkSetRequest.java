package com.example.cacheservice.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;

import java.util.Map;

/**
 * Request body for bulk set operations.
 */
@Schema(description = "Request to set multiple cache entries in one operation")
public record BulkSetRequest(

    @NotEmpty(message = "Entries map must not be empty")
    @Size(max = 500, message = "Cannot set more than 500 entries in a single request")
    @Schema(
        description = "Map of key → value pairs to cache",
        example = "{\"user:1\": \"Alice\", \"user:2\": \"Bob\"}"
    )
    Map<String, String> entries,

    @Min(value = 0, message = "TTL must be non-negative (0 = no expiry)")
    @Schema(description = "TTL in seconds applied to all entries. 0 = no expiry.", example = "3600")
    Long ttlSeconds
) {
    public long effectiveTtlSeconds() {
        return ttlSeconds != null ? ttlSeconds : 0L;
    }
}
