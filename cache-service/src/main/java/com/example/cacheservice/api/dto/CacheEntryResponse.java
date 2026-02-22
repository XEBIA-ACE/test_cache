package com.example.cacheservice.api.dto;

import com.example.cacheservice.business.model.CacheEntry;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;

/**
 * Response body for a single cache entry.
 */
@Schema(description = "A cached key-value entry with metadata")
public record CacheEntryResponse(

    @Schema(description = "The cache key", example = "user:42")
    String key,

    @Schema(description = "The cached value", example = "{\"userId\": 42, \"name\": \"Alice\"}")
    String value,

    @Schema(description = "Remaining TTL in seconds. -1 = no expiry, -2 = key not found", example = "3598")
    long ttlSeconds,

    @Schema(description = "Whether this key has an expiry configured", example = "true")
    boolean hasExpiry,

    @Schema(description = "Timestamp when the entry was read", example = "2024-01-15T10:30:00Z")
    Instant retrievedAt
) {
    /**
     * Converts a domain {@link CacheEntry} to the API response record.
     */
    public static CacheEntryResponse from(CacheEntry entry) {
        return new CacheEntryResponse(
            entry.getKey(),
            entry.getValue(),
            entry.getTtlSeconds(),
            entry.hasExpiry(),
            entry.getRetrievedAt()
        );
    }
}
