package com.example.cacheservice.business.service;

import com.example.cacheservice.business.model.CacheEntry;

import java.time.Duration;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Business service interface for cache operations.
 *
 * <p>This is the primary use-case boundary. It adds:
 * <ul>
 *   <li>Input validation (key format, value size, TTL bounds)</li>
 *   <li>Business rules (namespace scoping, max size enforcement)</li>
 *   <li>Structured logging at the business level</li>
 *   <li>Metric recording</li>
 * </ul>
 *
 * <p>The API layer calls this interface. It delegates to {@link com.example.cacheservice.infrastructure.repository.CacheRepository}.
 */
public interface CacheService {

    /**
     * Retrieves a cached entry by key.
     *
     * @param key the cache key
     * @return Optional with the entry, or empty if not found
     * @throws IllegalArgumentException if the key is invalid
     */
    Optional<CacheEntry> get(String key);

    /**
     * Stores or updates a key-value pair.
     *
     * @param key   the cache key
     * @param value the value to store
     * @param ttl   time-to-live; {@link Duration#ZERO} means no expiry
     * @return the stored cache entry
     * @throws IllegalArgumentException if inputs violate business constraints
     */
    CacheEntry set(String key, String value, Duration ttl);

    /**
     * Deletes a key.
     *
     * @param key the cache key
     * @return {@code true} if the key was found and deleted
     */
    boolean delete(String key);

    /**
     * Checks whether a key exists.
     *
     * @param key the cache key
     * @return {@code true} if the key exists
     */
    boolean exists(String key);

    /**
     * Returns the remaining TTL for a key.
     *
     * @param key the cache key
     * @return TTL in seconds; -1 if no expiry; -2 if not found
     */
    long getTtl(String key);

    /**
     * Updates the expiry for an existing key.
     *
     * @param key the cache key
     * @param ttl new TTL; {@link Duration#ZERO} removes the expiry
     * @return {@code true} if the key was found and TTL was updated
     */
    boolean expire(String key, Duration ttl);

    /**
     * Returns all keys matching the given pattern.
     *
     * <p>Uses SCAN internally for cluster safety. Pattern supports glob syntax:
     * {@code *} (any chars), {@code ?} (single char), {@code [abc]} (character class).
     *
     * @param pattern glob-style key pattern
     * @return set of matching keys (without namespace)
     */
    Set<String> keys(String pattern);

    /**
     * Stores multiple key-value pairs in bulk.
     *
     * @param entries map of key → value
     * @param ttl     TTL applied to all entries
     * @return list of stored entries
     */
    List<CacheEntry> bulkSet(Map<String, String> entries, Duration ttl);

    /**
     * Retrieves multiple values in bulk (single round-trip).
     *
     * @param keys collection of keys
     * @return map of key → CacheEntry for keys that exist
     */
    Map<String, CacheEntry> bulkGet(Collection<String> keys);

    /**
     * Deletes multiple keys in bulk.
     *
     * @param keys collection of keys to delete
     * @return number of keys actually deleted
     */
    long bulkDelete(Collection<String> keys);

    /**
     * Deletes all keys matching the given pattern.
     *
     * @param pattern glob-style pattern
     * @return number of keys deleted
     */
    long deleteByPattern(String pattern);

    /**
     * Atomically increments the integer value of a key.
     *
     * @param key   the cache key
     * @param delta increment amount (use negative value to decrement)
     * @return the new value after increment
     */
    long increment(String key, long delta);

    /**
     * Returns the approximate total number of keys in the cache.
     *
     * @return key count
     */
    long size();
}
