package com.example.cacheservice.business.service.impl;

import com.example.cacheservice.business.service.LockService;
import com.example.cacheservice.infrastructure.config.LockSettings;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.stereotype.Service;
import org.springframework.util.Assert;

import java.util.concurrent.TimeUnit;

/**
 * Redisson-backed implementation of {@link LockService}.
 *
 * <p>Uses Redisson's {@link RLock} which provides:
 * <ul>
 *   <li>Reentrant locking (same thread can acquire multiple times)</li>
 *   <li>Automatic lease expiry to prevent deadlocks if the holder crashes</li>
 *   <li>Watch dog for automatic lease renewal while the holder is alive</li>
 *   <li>Pub/Sub-based wait (no polling) for efficient contention handling</li>
 * </ul>
 *
 * <p>In cluster mode, Redisson uses a single-key lock (not Redlock), which means
 * lock availability follows the cluster shard for the lock key. This is sufficient
 * for most use cases. For true Redlock semantics across cluster shards, use
 * Redisson's {@code RedissonMultiLock}.
 */
@Slf4j
@Service
public class LockServiceImpl implements LockService {

    private static final String LOCK_KEY_PREFIX = "lock:";

    private final RedissonClient redissonClient;
    private final LockSettings lockSettings;

    public LockServiceImpl(RedissonClient redissonClient, LockSettings lockSettings) {
        this.redissonClient = redissonClient;
        this.lockSettings = lockSettings;
    }

    @Override
    public boolean tryAcquire(String lockKey, long waitTime, long leaseTime, TimeUnit unit) {
        validateLockKey(lockKey);

        String redisKey = LOCK_KEY_PREFIX + lockKey;
        RLock lock = redissonClient.getLock(redisKey);

        try {
            boolean acquired = lock.tryLock(waitTime, leaseTime, unit);
            if (acquired) {
                log.info("Lock ACQUIRED key={} thread={}", lockKey, Thread.currentThread().getName());
            } else {
                log.warn("Lock TIMEOUT key={} waitTime={} {}", lockKey, waitTime, unit);
            }
            return acquired;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.error("Lock acquisition interrupted for key={}", lockKey);
            return false;
        } catch (Exception e) {
            log.error("Lock acquisition failed for key={}: {}", lockKey, e.getMessage(), e);
            throw new RuntimeException("Failed to acquire lock: " + lockKey, e);
        }
    }

    @Override
    public void release(String lockKey) {
        validateLockKey(lockKey);

        String redisKey = LOCK_KEY_PREFIX + lockKey;
        RLock lock = redissonClient.getLock(redisKey);

        if (lock.isHeldByCurrentThread()) {
            lock.unlock();
            log.info("Lock RELEASED key={} thread={}", lockKey, Thread.currentThread().getName());
        } else {
            log.warn("Attempted to release lock not held by current thread: key={} thread={}",
                lockKey, Thread.currentThread().getName());
        }
    }

    @Override
    public boolean isLocked(String lockKey) {
        validateLockKey(lockKey);
        return redissonClient.getLock(LOCK_KEY_PREFIX + lockKey).isLocked();
    }

    @Override
    public boolean isHeldByCurrentThread(String lockKey) {
        validateLockKey(lockKey);
        return redissonClient.getLock(LOCK_KEY_PREFIX + lockKey).isHeldByCurrentThread();
    }

    @Override
    public int getHoldCount(String lockKey) {
        validateLockKey(lockKey);
        return redissonClient.getLock(LOCK_KEY_PREFIX + lockKey).getHoldCount();
    }

    private void validateLockKey(String lockKey) {
        Assert.hasText(lockKey, "Lock key must not be blank");
        Assert.isTrue(lockKey.length() <= 256, "Lock key exceeds maximum length of 256 characters");
    }
}
