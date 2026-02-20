package com.example.cacheservice.service;

import java.util.concurrent.TimeUnit;

/**
 * Distributed locking contract backed by Redisson.
 *
 * <p>All lock names are treated as Redis keys with the prefix
 * {@code lock:}. Callers are responsible for matching acquire/release
 * calls – a lock <em>not</em> explicitly released will expire after
 * the configured {@code leaseTime}.
 *
 * <p>Two locking strategies are exposed:
 * <ul>
 *   <li><b>tryLock</b>  – non-blocking; returns immediately with a boolean</li>
 *   <li><b>lock</b>     – blocking; waits up to {@code waitTime} for the lock</li>
 * </ul>
 */
public interface LockService {

    /**
     * Attempts to acquire a fair distributed lock without waiting.
     *
     * @param lockName  logical lock name (becomes Redis key {@code lock:<lockName>})
     * @param leaseTime how long to hold the lock before auto-release
     * @param unit      time unit for {@code leaseTime}
     * @return {@code true} if the lock was acquired
     */
    boolean tryLock(String lockName, long leaseTime, TimeUnit unit);

    /**
     * Attempts to acquire the lock, waiting up to {@code waitTime}.
     *
     * @param lockName  logical lock name
     * @param waitTime  maximum time to wait for the lock
     * @param leaseTime how long to hold the lock before auto-release
     * @param unit      time unit for both durations
     * @return {@code true} if the lock was acquired within the wait window
     * @throws InterruptedException if the current thread is interrupted while waiting
     */
    boolean lock(String lockName, long waitTime, long leaseTime, TimeUnit unit)
            throws InterruptedException;

    /**
     * Releases the lock held by the current thread.
     *
     * @param lockName logical lock name
     * @throws IllegalMonitorStateException if the current thread does not hold the lock
     */
    void unlock(String lockName);

    /**
     * Returns whether the named lock is currently held by any thread / process.
     *
     * @param lockName logical lock name
     */
    boolean isLocked(String lockName);

    /**
     * Returns whether the lock is held by the <em>current</em> thread.
     *
     * @param lockName logical lock name
     */
    boolean isHeldByCurrentThread(String lockName);

    /**
     * Forces the lock to be released regardless of ownership.
     * Use with care – intended for administrative / maintenance scenarios only.
     *
     * @param lockName logical lock name
     * @return {@code true} if the lock existed and was deleted
     */
    boolean forceUnlock(String lockName);
}
