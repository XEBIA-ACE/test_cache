package com.example.cacheservice.exception;

/**
 * Thrown when a distributed lock could not be acquired within the configured
 * wait window.
 */
public class LockAcquisitionException extends RuntimeException {

    private final String lockName;

    public LockAcquisitionException(String lockName) {
        super("Could not acquire distributed lock: " + lockName);
        this.lockName = lockName;
    }

    public LockAcquisitionException(String lockName, Throwable cause) {
        super("Could not acquire distributed lock: " + lockName, cause);
        this.lockName = lockName;
    }

    public String getLockName() {
        return lockName;
    }
}
