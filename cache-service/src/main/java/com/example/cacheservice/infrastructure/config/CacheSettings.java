package com.example.cacheservice.infrastructure.config;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * Application-level cache settings (separate from Redis connectivity).
 * Bound to {@code cache.settings.*} properties.
 */
@Data
@Validated
@ConfigurationProperties(prefix = "cache.settings")
public class CacheSettings {

    /** Default TTL in seconds (0 = no expiry). */
    @Min(0)
    private long defaultTtl = 3600;

    /** Maximum allowed key length in characters. */
    @Min(1)
    private int maxKeyLength = 512;

    /** Maximum allowed value size in bytes (default 1MB). */
    @Min(1)
    private int maxValueSize = 1048576;

    /** Namespace prefix added to all Redis keys to avoid collisions. */
    @NotBlank
    private String namespace = "cache-service";
}
