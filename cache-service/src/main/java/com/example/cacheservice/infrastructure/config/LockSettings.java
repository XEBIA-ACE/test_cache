package com.example.cacheservice.infrastructure.config;

import jakarta.validation.constraints.Min;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * Configuration for distributed lock behavior.
 * Bound to {@code cache.lock.*} properties.
 */
@Data
@Validated
@ConfigurationProperties(prefix = "cache.lock")
public class LockSettings {

    /** Default maximum wait time for lock acquisition in milliseconds. */
    @Min(0)
    private long defaultWaitTime = 5000;

    /** Default lease time (lock auto-release TTL) in milliseconds. */
    @Min(1)
    private long defaultLeaseTime = 30000;
}
