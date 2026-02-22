package com.example.cacheservice.infrastructure.repository.impl;

import com.example.cacheservice.infrastructure.config.CacheSettings;
import com.example.cacheservice.infrastructure.repository.CacheRepository;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.connection.RedisConnection;
import org.springframework.data.redis.core.Cursor;
import org.springframework.data.redis.core.ScanOptions;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Repository;

import java.time.Duration;
import java.util.*;

/**
 * Lettuce-backed implementation of {@link CacheRepository}.
 *
 * <p>Uses Spring Data Redis {@link StringRedisTemplate} which delegates to Lettuce.
 * All keys are namespaced with a prefix ({@code namespace:key}) to avoid collisions
 * between different services sharing the same Redis instance.
 *
 * <p>Key design decisions:
 * <ul>
 *   <li>SCAN replaces KEYS everywhere for cluster compatibility and non-blocking behavior</li>
 *   <li>Pipeline is used for bulk writes to minimize round-trips</li>
 *   <li>All operations are instrumented with Micrometer for Prometheus metrics</li>
 * </ul>
 */
@Slf4j
@Repository
public class LettuceCacheRepository implements CacheRepository {

    private static final int SCAN_COUNT = 200;  // Keys per SCAN page
    private static final String METRIC_PREFIX = "cache.redis.operation";

    private final StringRedisTemplate redisTemplate;
    private final CacheSettings settings;
    private final MeterRegistry meterRegistry;

    public LettuceCacheRepository(StringRedisTemplate redisTemplate,
                                   CacheSettings settings,
                                   MeterRegistry meterRegistry) {
        this.redisTemplate = redisTemplate;
        this.settings = settings;
        this.meterRegistry = meterRegistry;
    }

    // ----------------------------------------------------------------
    // Read operations
    // ----------------------------------------------------------------

    @Override
    public Optional<String> get(String key) {
        return timed("get", () -> {
            String value = redisTemplate.opsForValue().get(namespacedKey(key));
            log.debug("GET key={} found={}", key, value != null);
            return Optional.ofNullable(value);
        });
    }

    @Override
    public Map<String, String> multiGet(Collection<String> keys) {
        if (keys.isEmpty()) return Collections.emptyMap();

        return timed("mget", () -> {
            List<String> namespacedKeys = keys.stream().map(this::namespacedKey).toList();
            List<String> values = redisTemplate.opsForValue().multiGet(namespacedKeys);

            Map<String, String> result = new LinkedHashMap<>();
            if (values != null) {
                List<String> keyList = new ArrayList<>(keys);
                for (int i = 0; i < keyList.size(); i++) {
                    String value = values.get(i);
                    if (value != null) {
                        result.put(keyList.get(i), value);
                    }
                }
            }
            log.debug("MGET keys={} found={}", keys.size(), result.size());
            return result;
        });
    }

    @Override
    public boolean exists(String key) {
        return timed("exists", () -> {
            Boolean result = redisTemplate.hasKey(namespacedKey(key));
            return Boolean.TRUE.equals(result);
        });
    }

    @Override
    public long getTtl(String key) {
        return timed("ttl", () -> {
            Long ttl = redisTemplate.getExpire(namespacedKey(key));
            return ttl != null ? ttl : -2L;
        });
    }

    @Override
    public Set<String> scan(String pattern) {
        return timed("scan", () -> {
            String namespacedPattern = namespacedKey(pattern);
            Set<String> keys = new HashSet<>();

            ScanOptions options = ScanOptions.scanOptions()
                .match(namespacedPattern)
                .count(SCAN_COUNT)
                .build();

            // Execute SCAN via RedisConnection for full control
            redisTemplate.execute((RedisConnection connection) -> {
                try (Cursor<byte[]> cursor = connection.scan(options)) {
                    while (cursor.hasNext()) {
                        String rawKey = new String(cursor.next());
                        // Strip namespace prefix before returning to caller
                        keys.add(stripNamespace(rawKey));
                    }
                }
                return null;
            });

            log.debug("SCAN pattern={} found={} keys", pattern, keys.size());
            return keys;
        });
    }

    @Override
    public long size() {
        return timed("dbsize", () -> {
            Long size = redisTemplate.execute(RedisConnection::dbSize);
            return size != null ? size : 0L;
        });
    }

    // ----------------------------------------------------------------
    // Write operations
    // ----------------------------------------------------------------

    @Override
    public void set(String key, String value, Duration ttl) {
        timed("set", () -> {
            if (ttl.isZero() || ttl.isNegative()) {
                redisTemplate.opsForValue().set(namespacedKey(key), value);
            } else {
                redisTemplate.opsForValue().set(namespacedKey(key), value, ttl);
            }
            log.debug("SET key={} ttl={}", key, ttl);
            return null;
        });
    }

    @Override
    public void set(String key, String value) {
        set(key, value, Duration.ZERO);
    }

    @Override
    public void multiSet(Map<String, String> entries, Duration ttl) {
        if (entries.isEmpty()) return;

        timed("mset", () -> {
            // Namespace all keys
            Map<String, String> namespacedEntries = new LinkedHashMap<>();
            entries.forEach((k, v) -> namespacedEntries.put(namespacedKey(k), v));

            // Use pipeline for efficiency
            redisTemplate.executePipelined((RedisConnection connection) -> {
                namespacedEntries.forEach((k, v) -> {
                    byte[] keyBytes = k.getBytes();
                    byte[] valueBytes = v.getBytes();
                    if (ttl.isZero() || ttl.isNegative()) {
                        connection.stringCommands().set(keyBytes, valueBytes);
                    } else {
                        connection.stringCommands().setEx(keyBytes, ttl.toSeconds(), valueBytes);
                    }
                });
                return null;
            });

            log.debug("MSET count={} ttl={}", entries.size(), ttl);
            return null;
        });
    }

    @Override
    public boolean expire(String key, Duration ttl) {
        return timed("expire", () -> {
            Boolean result;
            if (ttl.isZero() || ttl.isNegative()) {
                // Remove expiry - persist the key
                result = redisTemplate.persist(namespacedKey(key));
            } else {
                result = redisTemplate.expire(namespacedKey(key), ttl);
            }
            return Boolean.TRUE.equals(result);
        });
    }

    @Override
    public long increment(String key, long delta) {
        return timed("incrby", () -> {
            Long result = redisTemplate.opsForValue().increment(namespacedKey(key), delta);
            return result != null ? result : 0L;
        });
    }

    // ----------------------------------------------------------------
    // Delete operations
    // ----------------------------------------------------------------

    @Override
    public boolean delete(String key) {
        return timed("del", () -> {
            Boolean result = redisTemplate.delete(namespacedKey(key));
            log.debug("DEL key={} deleted={}", key, result);
            return Boolean.TRUE.equals(result);
        });
    }

    @Override
    public long multiDelete(Collection<String> keys) {
        if (keys.isEmpty()) return 0L;

        return timed("mdel", () -> {
            List<String> namespacedKeys = keys.stream().map(this::namespacedKey).toList();
            Long count = redisTemplate.delete(namespacedKeys);
            log.debug("DEL keys={} deleted={}", keys.size(), count);
            return count != null ? count : 0L;
        });
    }

    @Override
    public long deleteByPattern(String pattern) {
        return timed("del_pattern", () -> {
            Set<String> keys = scan(pattern);
            if (keys.isEmpty()) return 0L;
            return multiDelete(keys);
        });
    }

    // ----------------------------------------------------------------
    // Key namespacing helpers
    // ----------------------------------------------------------------

    /**
     * Prepends the configured namespace to the key to avoid collisions.
     * Format: {@code {namespace}:{key}}
     */
    private String namespacedKey(String key) {
        return settings.getNamespace() + ":" + key;
    }

    /**
     * Removes the namespace prefix from a raw Redis key.
     */
    private String stripNamespace(String rawKey) {
        String prefix = settings.getNamespace() + ":";
        if (rawKey.startsWith(prefix)) {
            return rawKey.substring(prefix.length());
        }
        return rawKey;
    }

    // ----------------------------------------------------------------
    // Metrics helpers
    // ----------------------------------------------------------------

    /**
     * Wraps an operation in a Micrometer timer and records success/failure counters.
     */
    private <T> T timed(String operation, TimedOperation<T> action) {
        Timer.Sample sample = Timer.start(meterRegistry);
        boolean success = true;
        try {
            return action.execute();
        } catch (DataAccessException e) {
            success = false;
            meterRegistry.counter(METRIC_PREFIX + ".errors",
                "operation", operation,
                "error", e.getClass().getSimpleName()
            ).increment();
            log.error("Redis operation '{}' failed: {}", operation, e.getMessage(), e);
            throw e;
        } finally {
            sample.stop(Timer.builder(METRIC_PREFIX)
                .tag("operation", operation)
                .tag("success", String.valueOf(success))
                .register(meterRegistry));
        }
    }

    @FunctionalInterface
    private interface TimedOperation<T> {
        T execute();
    }
}
