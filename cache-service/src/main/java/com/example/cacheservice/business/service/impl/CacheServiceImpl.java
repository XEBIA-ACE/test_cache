package com.example.cacheservice.business.service.impl;

import com.example.cacheservice.business.model.CacheEntry;
import com.example.cacheservice.business.service.CacheService;
import com.example.cacheservice.infrastructure.config.CacheSettings;
import com.example.cacheservice.infrastructure.repository.CacheRepository;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.Assert;

import java.time.Duration;
import java.util.*;

/**
 * Business-layer implementation of {@link CacheService}.
 *
 * <p>Enforces business constraints before delegating to the repository:
 * <ul>
 *   <li>Key validation: non-blank, max length, no illegal characters</li>
 *   <li>Value size enforcement (configurable max in bytes)</li>
 *   <li>TTL bounds (non-negative)</li>
 *   <li>Pattern safety (prevents unbounded scans via bare {@code *})</li>
 * </ul>
 */
@Slf4j
@Service
public class CacheServiceImpl implements CacheService {

    private static final String METRIC_HIT = "cache.hits";
    private static final String METRIC_MISS = "cache.misses";

    private final CacheRepository repository;
    private final CacheSettings settings;
    private final MeterRegistry meterRegistry;

    public CacheServiceImpl(CacheRepository repository,
                             CacheSettings settings,
                             MeterRegistry meterRegistry) {
        this.repository = repository;
        this.settings = settings;
        this.meterRegistry = meterRegistry;
    }

    // ----------------------------------------------------------------
    // Read operations
    // ----------------------------------------------------------------

    @Override
    public Optional<CacheEntry> get(String key) {
        validateKey(key);

        Optional<String> value = repository.get(key);

        if (value.isEmpty()) {
            meterRegistry.counter(METRIC_MISS).increment();
            log.debug("Cache MISS for key={}", key);
            return Optional.empty();
        }

        meterRegistry.counter(METRIC_HIT).increment();
        long ttl = repository.getTtl(key);
        log.debug("Cache HIT for key={} ttl={}", key, ttl);

        return Optional.of(CacheEntry.of(key, value.get(), ttl));
    }

    @Override
    public boolean exists(String key) {
        validateKey(key);
        return repository.exists(key);
    }

    @Override
    public long getTtl(String key) {
        validateKey(key);
        return repository.getTtl(key);
    }

    @Override
    public Set<String> keys(String pattern) {
        validatePattern(pattern);
        log.debug("Scanning keys with pattern={}", pattern);
        return repository.scan(pattern);
    }

    @Override
    public Map<String, CacheEntry> bulkGet(Collection<String> keys) {
        Assert.notNull(keys, "Keys collection must not be null");
        keys.forEach(this::validateKey);

        Map<String, String> rawValues = repository.multiGet(keys);

        Map<String, CacheEntry> result = new LinkedHashMap<>();
        rawValues.forEach((k, v) -> result.put(k, CacheEntry.of(k, v)));
        return result;
    }

    @Override
    public long size() {
        return repository.size();
    }

    // ----------------------------------------------------------------
    // Write operations
    // ----------------------------------------------------------------

    @Override
    public CacheEntry set(String key, String value, Duration ttl) {
        validateKey(key);
        validateValue(value);
        validateTtl(ttl);

        Duration effectiveTtl = (ttl == null || ttl.isNegative()) ? Duration.ZERO : ttl;
        repository.set(key, value, effectiveTtl);

        log.info("Cache SET key={} valueBytes={} ttl={}", key, value.getBytes().length, effectiveTtl);
        return CacheEntry.of(key, value, effectiveTtl.isZero() ? -1 : effectiveTtl.toSeconds());
    }

    @Override
    public boolean expire(String key, Duration ttl) {
        validateKey(key);
        validateTtl(ttl);

        Duration effectiveTtl = (ttl == null || ttl.isNegative()) ? Duration.ZERO : ttl;
        boolean updated = repository.expire(key, effectiveTtl);
        log.debug("Cache EXPIRE key={} ttl={} updated={}", key, effectiveTtl, updated);
        return updated;
    }

    @Override
    public List<CacheEntry> bulkSet(Map<String, String> entries, Duration ttl) {
        Assert.notNull(entries, "Entries map must not be null");
        Assert.isTrue(!entries.isEmpty(), "Entries map must not be empty");

        entries.forEach((k, v) -> {
            validateKey(k);
            validateValue(v);
        });
        validateTtl(ttl);

        Duration effectiveTtl = (ttl == null || ttl.isNegative()) ? Duration.ZERO : ttl;
        repository.multiSet(entries, effectiveTtl);

        log.info("Cache bulk SET count={} ttl={}", entries.size(), effectiveTtl);

        return entries.entrySet().stream()
            .map(e -> CacheEntry.of(e.getKey(), e.getValue(),
                effectiveTtl.isZero() ? -1 : effectiveTtl.toSeconds()))
            .toList();
    }

    @Override
    public long increment(String key, long delta) {
        validateKey(key);
        long newValue = repository.increment(key, delta);
        log.debug("Cache INCR key={} delta={} newValue={}", key, delta, newValue);
        return newValue;
    }

    // ----------------------------------------------------------------
    // Delete operations
    // ----------------------------------------------------------------

    @Override
    public boolean delete(String key) {
        validateKey(key);
        boolean deleted = repository.delete(key);
        log.info("Cache DELETE key={} deleted={}", key, deleted);
        return deleted;
    }

    @Override
    public long bulkDelete(Collection<String> keys) {
        Assert.notNull(keys, "Keys collection must not be null");
        keys.forEach(this::validateKey);

        long count = repository.multiDelete(keys);
        log.info("Cache bulk DELETE count={} deleted={}", keys.size(), count);
        return count;
    }

    @Override
    public long deleteByPattern(String pattern) {
        validatePattern(pattern);
        long count = repository.deleteByPattern(pattern);
        log.info("Cache DELETE by pattern={} deleted={}", pattern, count);
        return count;
    }

    // ----------------------------------------------------------------
    // Input validation (business rules)
    // ----------------------------------------------------------------

    private void validateKey(String key) {
        Assert.hasText(key, "Cache key must not be blank");
        Assert.isTrue(key.length() <= settings.getMaxKeyLength(),
            "Cache key exceeds max length of " + settings.getMaxKeyLength() + " chars");
        // Reject keys with control characters
        Assert.isTrue(!key.contains("\n") && !key.contains("\r"),
            "Cache key must not contain newline characters");
    }

    private void validateValue(String value) {
        Assert.notNull(value, "Cache value must not be null");
        int bytes = value.getBytes().length;
        Assert.isTrue(bytes <= settings.getMaxValueSize(),
            "Cache value size " + bytes + " bytes exceeds max of " + settings.getMaxValueSize() + " bytes");
    }

    private void validateTtl(Duration ttl) {
        if (ttl != null) {
            Assert.isTrue(!ttl.isNegative() || ttl.isZero(),
                "TTL must be non-negative (use Duration.ZERO for no expiry)");
        }
    }

    private void validatePattern(String pattern) {
        Assert.hasText(pattern, "Pattern must not be blank");
        // Require at least some specificity - bare wildcard would scan everything
        Assert.isTrue(pattern.length() > 1 || !pattern.equals("*"),
            "Pattern '*' is too broad; use a more specific pattern like 'prefix:*'");
    }
}
