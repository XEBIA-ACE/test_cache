package com.example.cacheservice.infrastructure.repository;

import java.time.Duration;
import java.util.Collection;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Port interface (Data Access abstraction) for Redis cache operations.
 *
 * <p>Defines the contract that repository implementations must fulfill.
 * The primary implementation uses Lettuce via Spring Data Redis.
 * Following Clean Architecture, the business layer depends on this interface,
 * not on any concrete Redis client.
 *
 * <p><b>Cluster compatibility notes:</b>
 * <ul>
 *   <li>{@link #scan(String)} uses SCAN instead of KEYS for non-blocking, cluster-safe key enumeration</li>
 *   <li>{@link #deleteByPattern(String)} iterates with SCAN to delete across all cluster shards</li>
 *   <li>The {@code database} parameter is ignored in cluster mode (cluster always uses DB 0)</li>
 * </ul>
 */
public interface CacheRepository {

    /**
     * Retrieves the value for the given key.
     *
     * @param key the cache key (namespaced internally)
     * @return an Optional containing the value, or empty if key does not exist
     */
    Optional<String> get(String key);

    /**
     * Retrieves values for multiple keys in a single round-trip (mget).
     *
     * @param keys collection of cache keys
     * @return map of key → value for keys that exist; missing keys are omitted
     */
    Map<String, String> multiGet(Collection<String> keys);

    /**
     * Sets a key-value pair with the given TTL.
     *
     * @param key   the cache key
     * @param value the value to store
     * @param ttl   time-to-live; use {@link Duration#ZERO} for no expiry
     */
    void set(String key, String value, Duration ttl);

    /**
     * Sets a key-value pair without expiry.
     *
     * @param key   the cache key
     * @param value the value to store
     */
    void set(String key, String value);

    /**
     * Sets multiple key-value pairs atomically using a pipeline.
     *
     * @param entries map of key → value
     * @param ttl     TTL applied to all entries; use {@link Duration#ZERO} for no expiry
     */
    void multiSet(Map<String, String> entries, Duration ttl);

    /**
     * Deletes a single key.
     *
     * @param key the cache key
     * @return {@code true} if the key existed and was deleted
     */
    boolean delete(String key);

    /**
     * Deletes multiple keys in a single round-trip (del).
     *
     * @param keys collection of cache keys
     * @return number of keys actually deleted
     */
    long multiDelete(Collection<String> keys);

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
     * @return TTL in seconds; -1 if no expiry; -2 if key does not exist
     */
    long getTtl(String key);

    /**
     * Updates the TTL of an existing key.
     *
     * @param key the cache key
     * @param ttl new TTL; use {@link Duration#ZERO} to persist the key indefinitely
     * @return {@code true} if the key exists and TTL was set
     */
    boolean expire(String key, Duration ttl);

    /**
     * Scans for keys matching a glob-style pattern.
     *
     * <p>Uses SCAN (non-blocking) instead of KEYS. In cluster mode, scans all master nodes.
     *
     * @param pattern glob pattern (e.g., {@code user:*}, {@code session:?????})
     * @return set of matching keys (without the namespace prefix)
     */
    Set<String> scan(String pattern);

    /**
     * Deletes all keys matching the given pattern.
     *
     * @param pattern glob pattern
     * @return number of keys deleted
     */
    long deleteByPattern(String pattern);

    /**
     * Atomically increments the integer value of a key by delta.
     * Creates the key with value {@code delta} if it does not exist.
     *
     * @param key   the cache key
     * @param delta increment amount (can be negative to decrement)
     * @return the new value after the operation
     */
    long increment(String key, long delta);

    /**
     * Returns the total number of keys in the Redis database/namespace.
     * In cluster mode, aggregates across all shards.
     *
     * @return approximate key count
     */
    long size();
}
