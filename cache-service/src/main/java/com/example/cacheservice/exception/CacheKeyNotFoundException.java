package com.example.cacheservice.exception;

/**
 * Thrown when a requested cache key does not exist in Redis.
 */
public class CacheKeyNotFoundException extends RuntimeException {

    private final String key;

    public CacheKeyNotFoundException(String key) {
        super("Cache key not found: " + key);
        this.key = key;
    }

    public String getKey() {
        return key;
    }
}
