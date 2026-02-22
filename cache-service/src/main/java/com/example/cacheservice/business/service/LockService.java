package com.example.cacheservice.business.service;

import java.util.concurrent.TimeUnit;

/**
 * Service interface for distributed lock operations.
 *
 * <p>Backed by Redisson's {@link org.redisson.api.RLock}, which uses Redis' Lua scripts
 * for atomic lock acquisition and release (equivalent to Redlock algorithm for standalone/sentinel).
 *
 * <p>Distributed locks are scoped per application instance. A thread holding a lock
 * must release it explicitly or wait for the lease to expire.
 *
 * <p><b>Usage pattern:</b>
 * <pre>{@code
 * boolean acquired = lockService.tryAcquire("order:123", 5, 30, TimeUnit.SECONDS);
 * if (acquired) {
 *     try {
 *         // critical section
 *     } finally {
 *         lockService.release("order:123");
 *     }
 * }
 * }</pre>
 */
public interface LockService {

    /**
     * Attempts to acquire a distributed lock.
     *
     * @param lockKey   unique identifier for the lock
     * @param waitTime  maximum time to wait for lock acquisition
     * @param leaseTime maximum time the lock will be held (auto-releases after this)
     * @param unit      time unit for both waitTime and leaseTime
     * @return {@code true} if the lock was acquired; {@code false} if it timed out
     * @throws IllegalArgumentException if lockKey is blank
     * @throws RuntimeException if the lock operation fails (e.g., Redis unavailable)
     */
    boolean tryAcquire(String lockKey, long waitTime, long leaseTime, TimeUnit unit);

    /**
     * Releases a previously acquired lock.
     *
     * <p>Only the thread that acquired the lock can release it.
     * Calling release on a lock not held by the current thread is a no-op.
     *
     * @param lockKey the lock identifier
     */
    void release(String lockKey);

    /**
     * Checks whether a lock is currently held by any thread/process.
     *
     * @param lockKey the lock identifier
     * @return {@code true} if the lock is currently held
     */
    boolean isLocked(String lockKey);

    /**
     * Checks whether the current thread holds the specified lock.
     *
     * @param lockKey the lock identifier
     * @return {@code true} if this thread holds the lock
     */
    boolean isHeldByCurrentThread(String lockKey);

    /**
     * Returns the number of times the current thread has acquired the lock (reentrant count).
     *
     * @param lockKey the lock identifier
     * @return hold count; 0 if not held by current thread
     */
    int getHoldCount(String lockKey);
}
