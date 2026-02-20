package com.example.cacheservice.service;

import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Core cache operations backed by Redis (via Lettuce / Spring Data Redis).
 *
 * <p>All keys are UTF-8 strings. Values are serialised as JSON so that any
 * JSON-compatible type can be stored and retrieved without losing type
 * information (when the caller provides it).
 *
 * <p>Methods follow the principle of least surprise: they either return a
 * meaningful result, throw a checked domain exception, or use {@link Optional}
 * to make the absent-value contract explicit at compile time.
 */
public interface CacheService {

    // ─── String / Generic ────────────────────────────────────────────────────

    /** Returns the value stored at {@code key}, or {@link Optional#empty()} if absent. */
    Optional<String> get(String key);

    /** Stores {@code value} at {@code key} without an expiry. */
    void set(String key, String value);

    /**
     * Stores {@code value} at {@code key} with an explicit TTL in seconds.
     *
     * @param ttlSeconds positive; 0 means no expiry
     */
    void set(String key, String value, long ttlSeconds);

    /**
     * Atomically sets {@code value} only when the key does <em>not</em> exist
     * (SET NX EX). Returns {@code true} when the key was written.
     */
    boolean setIfAbsent(String key, String value, long ttlSeconds);

    /** Removes the key. Returns {@code true} if the key existed. */
    boolean delete(String key);

    /** Returns {@code true} if the key exists in Redis. */
    boolean exists(String key);

    /**
     * Returns the remaining TTL of the key in seconds.
     * {@code -1} means the key has no expiry; {@code -2} means the key does not exist.
     */
    long ttl(String key);

    /**
     * Updates the TTL of an existing key.
     * Returns {@code false} when the key does not exist.
     */
    boolean expire(String key, long ttlSeconds);

    /** Removes the TTL from a key, making it persistent. */
    boolean persist(String key);

    // ─── Bulk / Scan ─────────────────────────────────────────────────────────

    /**
     * Returns at most {@code limit} keys matching {@code pattern}.
     * Uses {@code SCAN} internally to avoid blocking Redis.
     *
     * @param pattern Redis glob pattern (e.g. {@code "user:*"})
     * @param limit   maximum number of keys to return; &lt;= 0 means unlimited
     */
    Set<String> scan(String pattern, int limit);

    /**
     * Fetches multiple keys in a single round-trip (MGET).
     * Keys that do not exist appear as {@code null} values in the result map.
     */
    Map<String, String> mget(Iterable<String> keys);

    /**
     * Stores multiple key/value pairs atomically (MSET).
     * Existing keys are overwritten.
     */
    void mset(Map<String, String> entries);

    /**
     * Stores multiple key/value pairs, each with the same TTL.
     * Implemented as a pipeline to keep round-trips to one.
     */
    void msetWithTtl(Map<String, String> entries, long ttlSeconds);

    /**
     * Deletes all keys matching {@code pattern} using SCAN + DEL batches.
     * Returns the total number of keys deleted.
     */
    long deleteByPattern(String pattern);

    // ─── Hash ────────────────────────────────────────────────────────────────

    /** Returns the value of {@code field} in the hash at {@code key}. */
    Optional<Object> hget(String key, String field);

    /** Returns all field-value pairs of the hash at {@code key}. */
    Map<Object, Object> hgetAll(String key);

    /** Sets {@code field} in the hash at {@code key} to {@code value}. */
    void hset(String key, String field, Object value);

    /**
     * Sets multiple fields in the hash at {@code key} atomically (HMSET).
     */
    void hmset(String key, Map<String, Object> fields);

    /** Deletes {@code field} from the hash at {@code key}. Returns {@code true} if the field existed. */
    boolean hdel(String key, String field);

    // ─── Counter ─────────────────────────────────────────────────────────────

    /** Increments the integer value at {@code key} by 1 (INCR). */
    long increment(String key);

    /** Increments the integer value at {@code key} by {@code delta} (INCRBY). */
    long incrementBy(String key, long delta);

    /** Decrements the integer value at {@code key} by 1 (DECR). */
    long decrement(String key);

    // ─── Info / Stats ─────────────────────────────────────────────────────────

    /**
     * Returns a subset of the Redis {@code INFO} output as a structured map:
     * connected_clients, used_memory_human, keyspace_hits, keyspace_misses, etc.
     */
    Map<String, String> getServerInfo();
}
