package com.example.cacheservice.service.impl;

import com.example.cacheservice.service.LockService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.stereotype.Service;

import java.util.concurrent.TimeUnit;

/**
 * {@link LockService} implementation using <b>Redisson</b>'s distributed,
 * fair {@link RLock}.
 *
 * <p>Redisson's {@code RLock} is a Redis-backed reentrant lock that:
 * <ul>
 *   <li>Extends the lease automatically (watch-dog mechanism) while the
 *       owning thread is alive, preventing premature expiry under long operations.</li>
 *   <li>Releases the lock atomically via a Lua script to avoid race conditions.</li>
 *   <li>Works transparently in both single-node and cluster topologies.</li>
 * </ul>
 *
 * <p>Lock keys in Redis are prefixed with {@code lock:} to separate them from
 * application cache keys in the same keyspace.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RedissonLockServiceImpl implements LockService {

    private final RedissonClient redissonClient;

    private static final String LOCK_PREFIX = "lock:";

    // ─── Acquire ─────────────────────────────────────────────────────────────

    @Override
    public boolean tryLock(String lockName, long leaseTime, TimeUnit unit) {
        RLock lock = getLock(lockName);
        try {
            boolean acquired = lock.tryLock(0, leaseTime, unit);
            log.debug("tryLock name={} leaseTime={}{} acquired={}",
                    lockName, leaseTime, unit, acquired);
            return acquired;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.warn("tryLock interrupted name={}", lockName);
            return false;
        }
    }

    @Override
    public boolean lock(String lockName, long waitTime, long leaseTime, TimeUnit unit)
            throws InterruptedException {
        RLock lock = getLock(lockName);
        boolean acquired = lock.tryLock(waitTime, leaseTime, unit);
        log.debug("lock name={} waitTime={} leaseTime={}{} acquired={}",
                lockName, waitTime, leaseTime, unit, acquired);
        return acquired;
    }

    // ─── Release ─────────────────────────────────────────────────────────────

    @Override
    public void unlock(String lockName) {
        RLock lock = getLock(lockName);
        if (lock.isHeldByCurrentThread()) {
            lock.unlock();
            log.debug("unlock name={}", lockName);
        } else {
            log.warn("unlock called but lock not held by current thread name={}", lockName);
            throw new IllegalMonitorStateException(
                    "Cannot release lock '" + lockName + "': not held by current thread");
        }
    }

    // ─── Status queries ───────────────────────────────────────────────────────

    @Override
    public boolean isLocked(String lockName) {
        return getLock(lockName).isLocked();
    }

    @Override
    public boolean isHeldByCurrentThread(String lockName) {
        return getLock(lockName).isHeldByCurrentThread();
    }

    // ─── Administrative ──────────────────────────────────────────────────────

    @Override
    public boolean forceUnlock(String lockName) {
        log.warn("forceUnlock name={}", lockName);
        return getLock(lockName).forceUnlock();
    }

    // ─── Helper ──────────────────────────────────────────────────────────────

    private RLock getLock(String lockName) {
        return redissonClient.getLock(LOCK_PREFIX + lockName);
    }
}
