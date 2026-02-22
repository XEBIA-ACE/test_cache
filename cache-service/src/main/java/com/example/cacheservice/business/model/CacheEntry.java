package com.example.cacheservice.business.model;

import lombok.Builder;
import lombok.Value;

import java.time.Instant;

/**
 * Immutable domain model representing a cached key-value entry.
 *
 * <p>This is the core domain object passed between the business and API layers.
 * It is never directly serialized to/from Redis (the repository layer handles that).
 */
@Value
@Builder
public class CacheEntry {

    /** The cache key (without namespace prefix). */
    String key;

    /** The cached value. */
    String value;

    /**
     * Remaining TTL in seconds.
     * <ul>
     *   <li>{@code -1} – Key has no expiry (persistent)</li>
     *   <li>{@code -2} – Key does not exist</li>
     *   <li>{@code >= 0} – Remaining seconds until expiry</li>
     * </ul>
     */
    long ttlSeconds;

    /** Timestamp when this entry was read from Redis. */
    Instant retrievedAt;

    /** {@code true} if the key has an expiry set. */
    public boolean hasExpiry() {
        return ttlSeconds >= 0;
    }

    /** {@code true} if the key does not exist in Redis. */
    public boolean doesNotExist() {
        return ttlSeconds == -2;
    }

    /**
     * Factory method for a simple entry without TTL info (e.g., after a write).
     */
    public static CacheEntry of(String key, String value) {
        return CacheEntry.builder()
            .key(key)
            .value(value)
            .ttlSeconds(-1)
            .retrievedAt(Instant.now())
            .build();
    }

    /**
     * Factory method including TTL information (after a read).
     */
    public static CacheEntry of(String key, String value, long ttlSeconds) {
        return CacheEntry.builder()
            .key(key)
            .value(value)
            .ttlSeconds(ttlSeconds)
            .retrievedAt(Instant.now())
            .build();
    }
}
