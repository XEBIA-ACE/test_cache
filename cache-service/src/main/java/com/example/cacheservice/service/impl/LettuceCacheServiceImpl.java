package com.example.cacheservice.service.impl;

import com.example.cacheservice.service.CacheService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.connection.RedisConnection;
import org.springframework.data.redis.core.Cursor;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ScanOptions;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.util.CollectionUtils;

import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

/**
 * {@link CacheService} implementation using <b>Lettuce</b> via Spring Data Redis.
 *
 * <p>Design decisions:
 * <ul>
 *   <li>Keys are stored as plain UTF-8 strings; values are serialised as JSON
 *       (configured in {@link com.example.cacheservice.config.RedisConfig}).</li>
 *   <li>All scan operations use {@code SCAN} (not {@code KEYS}) to avoid
 *       blocking the Redis event loop in production.</li>
 *   <li>Bulk writes use pipeline mode for minimal round-trips.</li>
 * </ul>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class LettuceCacheServiceImpl implements CacheService {

    /** General template: keys = String, values = JSON Object */
    private final RedisTemplate<String, Object> redisTemplate;

    /** Thin String-only template used for simple string values and INFO parsing */
    private final StringRedisTemplate stringRedisTemplate;

    private static final int SCAN_BATCH_SIZE = 200;
    private static final String LOCK_KEY_PREFIX = "lock:";

    // ─── String / Generic ────────────────────────────────────────────────────

    @Override
    public Optional<String> get(String key) {
        log.debug("GET key={}", key);
        String value = stringRedisTemplate.opsForValue().get(key);
        return Optional.ofNullable(value);
    }

    @Override
    public void set(String key, String value) {
        log.debug("SET key={}", key);
        stringRedisTemplate.opsForValue().set(key, value);
    }

    @Override
    public void set(String key, String value, long ttlSeconds) {
        log.debug("SET key={} ttl={}s", key, ttlSeconds);
        if (ttlSeconds <= 0) {
            stringRedisTemplate.opsForValue().set(key, value);
        } else {
            stringRedisTemplate.opsForValue().set(key, value, ttlSeconds, TimeUnit.SECONDS);
        }
    }

    @Override
    public boolean setIfAbsent(String key, String value, long ttlSeconds) {
        log.debug("SETNX key={} ttl={}s", key, ttlSeconds);
        Boolean result = ttlSeconds > 0
                ? stringRedisTemplate.opsForValue().setIfAbsent(key, value, ttlSeconds, TimeUnit.SECONDS)
                : stringRedisTemplate.opsForValue().setIfAbsent(key, value);
        return Boolean.TRUE.equals(result);
    }

    @Override
    public boolean delete(String key) {
        log.debug("DEL key={}", key);
        Boolean deleted = stringRedisTemplate.delete(key);
        return Boolean.TRUE.equals(deleted);
    }

    @Override
    public boolean exists(String key) {
        Boolean exists = stringRedisTemplate.hasKey(key);
        return Boolean.TRUE.equals(exists);
    }

    @Override
    public long ttl(String key) {
        Long ttl = stringRedisTemplate.getExpire(key, TimeUnit.SECONDS);
        return ttl != null ? ttl : -2L;
    }

    @Override
    public boolean expire(String key, long ttlSeconds) {
        Boolean ok = stringRedisTemplate.expire(key, ttlSeconds, TimeUnit.SECONDS);
        return Boolean.TRUE.equals(ok);
    }

    @Override
    public boolean persist(String key) {
        Boolean ok = stringRedisTemplate.persist(key);
        return Boolean.TRUE.equals(ok);
    }

    // ─── Bulk / Scan ─────────────────────────────────────────────────────────

    @Override
    public Set<String> scan(String pattern, int limit) {
        log.debug("SCAN pattern={} limit={}", pattern, limit);
        Set<String> keys = new LinkedHashSet<>();

        // Use connection-level SCAN to avoid KEYS command blocking Redis
        stringRedisTemplate.execute((RedisConnection connection) -> {
            ScanOptions options = ScanOptions.scanOptions()
                    .match(pattern)
                    .count(SCAN_BATCH_SIZE)
                    .build();

            try (Cursor<byte[]> cursor = connection.keyCommands().scan(options)) {
                while (cursor.hasNext()) {
                    String key = new String(cursor.next(), StandardCharsets.UTF_8);
                    keys.add(key);
                    if (limit > 0 && keys.size() >= limit) {
                        break;
                    }
                }
            }
            return null;
        });

        return Collections.unmodifiableSet(keys);
    }

    @Override
    public Map<String, String> mget(Iterable<String> keys) {
        List<String> keyList = new ArrayList<>();
        keys.forEach(keyList::add);

        if (keyList.isEmpty()) {
            return Collections.emptyMap();
        }

        List<String> values = stringRedisTemplate.opsForValue().multiGet(keyList);
        Map<String, String> result = new LinkedHashMap<>(keyList.size());

        for (int i = 0; i < keyList.size(); i++) {
            result.put(keyList.get(i), values != null ? values.get(i) : null);
        }
        return Collections.unmodifiableMap(result);
    }

    @Override
    public void mset(Map<String, String> entries) {
        if (CollectionUtils.isEmpty(entries)) {
            return;
        }
        log.debug("MSET {} keys", entries.size());
        stringRedisTemplate.opsForValue().multiSet(entries);
    }

    @Override
    public void msetWithTtl(Map<String, String> entries, long ttlSeconds) {
        if (CollectionUtils.isEmpty(entries)) {
            return;
        }
        log.debug("MSET+EXPIRE {} keys ttl={}s", entries.size(), ttlSeconds);

        // Pipeline: SET + EXPIRE for each entry in one connection round-trip
        stringRedisTemplate.executePipelined((RedisConnection connection) -> {
            entries.forEach((key, value) -> {
                byte[] keyBytes = key.getBytes(StandardCharsets.UTF_8);
                byte[] valueBytes = value.getBytes(StandardCharsets.UTF_8);
                connection.stringCommands().set(keyBytes, valueBytes);
                if (ttlSeconds > 0) {
                    connection.keyCommands().expire(keyBytes, ttlSeconds);
                }
            });
            return null;
        });
    }

    @Override
    public long deleteByPattern(String pattern) {
        log.info("deleteByPattern pattern={}", pattern);
        long deleted = 0;

        // Collect keys via SCAN, then DEL in batches of 500
        Set<String> keys = scan(pattern, 0);
        if (keys.isEmpty()) {
            return 0;
        }

        List<String> keyList = new ArrayList<>(keys);
        int batchSize = 500;
        for (int i = 0; i < keyList.size(); i += batchSize) {
            List<String> batch = keyList.subList(i, Math.min(i + batchSize, keyList.size()));
            Long count = stringRedisTemplate.delete(batch);
            deleted += count != null ? count : 0;
        }

        log.info("deleteByPattern deleted={} pattern={}", deleted, pattern);
        return deleted;
    }

    // ─── Hash ────────────────────────────────────────────────────────────────

    @Override
    public Optional<Object> hget(String key, String field) {
        log.debug("HGET key={} field={}", key, field);
        Object value = redisTemplate.opsForHash().get(key, field);
        return Optional.ofNullable(value);
    }

    @Override
    public Map<Object, Object> hgetAll(String key) {
        log.debug("HGETALL key={}", key);
        return redisTemplate.opsForHash().entries(key);
    }

    @Override
    public void hset(String key, String field, Object value) {
        log.debug("HSET key={} field={}", key, field);
        redisTemplate.opsForHash().put(key, field, value);
    }

    @Override
    public void hmset(String key, Map<String, Object> fields) {
        log.debug("HMSET key={} fields={}", key, fields.size());
        redisTemplate.opsForHash().putAll(key, fields);
    }

    @Override
    public boolean hdel(String key, String field) {
        log.debug("HDEL key={} field={}", key, field);
        Long count = redisTemplate.opsForHash().delete(key, (Object) field);
        return count != null && count > 0;
    }

    // ─── Counter ─────────────────────────────────────────────────────────────

    @Override
    public long increment(String key) {
        Long value = stringRedisTemplate.opsForValue().increment(key);
        return value != null ? value : 0L;
    }

    @Override
    public long incrementBy(String key, long delta) {
        Long value = stringRedisTemplate.opsForValue().increment(key, delta);
        return value != null ? value : 0L;
    }

    @Override
    public long decrement(String key) {
        Long value = stringRedisTemplate.opsForValue().decrement(key);
        return value != null ? value : 0L;
    }

    // ─── Info / Stats ─────────────────────────────────────────────────────────

    @Override
    public Map<String, String> getServerInfo() {
        Properties info = stringRedisTemplate.execute((RedisConnection connection) ->
                connection.serverCommands().info());

        if (info == null) {
            return Collections.emptyMap();
        }

        // Extract a meaningful subset of Redis INFO fields
        Set<String> relevantKeys = Set.of(
                "redis_version", "uptime_in_seconds", "connected_clients",
                "used_memory_human", "used_memory_peak_human",
                "total_commands_processed", "keyspace_hits", "keyspace_misses",
                "expired_keys", "evicted_keys", "total_connections_received",
                "role", "cluster_enabled"
        );

        Map<String, String> result = new LinkedHashMap<>();
        info.stringPropertyNames().stream()
                .filter(relevantKeys::contains)
                .sorted()
                .forEach(k -> result.put(k, info.getProperty(k)));
        return Collections.unmodifiableMap(result);
    }
}
