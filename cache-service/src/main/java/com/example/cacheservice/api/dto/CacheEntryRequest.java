package com.example.cacheservice.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * Request body for creating or updating a single cache entry.
 */
@Schema(description = "Request to set a cache entry")
public record CacheEntryRequest(

    @NotNull(message = "Value must not be null")
    @Size(max = 1048576, message = "Value must not exceed 1MB")
    @Schema(description = "The value to cache", example = "{\"userId\": 42, \"name\": \"Alice\"}")
    String value,

    @Min(value = 0, message = "TTL must be non-negative (0 = no expiry)")
    @Schema(
        description = "Time-to-live in seconds. 0 or omitted means no expiry.",
        example = "3600",
        defaultValue = "0"
    )
    Long ttlSeconds
) {
    /** Returns the effective TTL in seconds, defaulting to 0 (no expiry) if not specified. */
    public long effectiveTtlSeconds() {
        return ttlSeconds != null ? ttlSeconds : 0L;
    }
}
