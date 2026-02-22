package com.example.cacheservice.infrastructure.config;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import java.util.List;

/**
 * Typed configuration properties for all Redis connectivity options.
 *
 * <p>Supports three deployment modes controlled by {@code cache.redis.mode}:
 * <ul>
 *   <li>{@code standalone} - Single Redis instance</li>
 *   <li>{@code sentinel}   - Redis Sentinel cluster</li>
 *   <li>{@code cluster}    - Redis Cluster</li>
 * </ul>
 */
@Data
@Validated
@ConfigurationProperties(prefix = "cache.redis")
public class RedisProperties {

    /** Deployment topology mode. */
    @NotNull
    private Mode mode = Mode.STANDALONE;

    /** Redis host for standalone mode. */
    private String host = "localhost";

    /** Redis port for standalone mode. */
    @Min(1)
    private int port = 6379;

    /** Redis AUTH password (optional). */
    private String password;

    /** Database index for standalone/sentinel mode (0–15). */
    @Min(0)
    private int database = 0;

    /** Command timeout in milliseconds. */
    @Min(100)
    private long timeout = 2000;

    /** Connection pool settings (Lettuce). */
    @Valid
    private PoolProperties pool = new PoolProperties();

    /** Sentinel-specific configuration. */
    @Valid
    private SentinelProperties sentinel = new SentinelProperties();

    /** Cluster-specific configuration. */
    @Valid
    private ClusterProperties cluster = new ClusterProperties();

    // ----------------------------------------------------------------
    // Nested configuration classes
    // ----------------------------------------------------------------

    public enum Mode {
        STANDALONE, SENTINEL, CLUSTER
    }

    @Data
    public static class PoolProperties {
        /** Maximum number of connections in the pool. */
        @Min(1)
        private int maxActive = 16;

        /** Maximum number of idle connections. */
        @Min(0)
        private int maxIdle = 8;

        /** Minimum number of idle connections to maintain. */
        @Min(0)
        private int minIdle = 2;

        /** Maximum wait time (ms) when pool is exhausted. -1 = wait indefinitely. */
        private long maxWait = 1000;
    }

    @Data
    public static class SentinelProperties {
        /** Name of the Redis master monitored by Sentinels. */
        @NotBlank
        private String master = "mymaster";

        /** List of sentinel node addresses (host:port). */
        private List<String> nodes = List.of("localhost:26379");

        /** Sentinel AUTH password (optional, separate from Redis password). */
        private String password;
    }

    @Data
    public static class ClusterProperties {
        /** List of known cluster node addresses (host:port). */
        private List<String> nodes = List.of("localhost:7000");

        /** Maximum number of cluster redirections to follow (MOVED/ASK). */
        @Min(0)
        private int maxRedirects = 3;
    }

    // ----------------------------------------------------------------
    // Application-level cache settings
    // ----------------------------------------------------------------

    @Data
    @ConfigurationProperties(prefix = "cache.settings")
    public static class CacheSettings {
        /** Default TTL in seconds (0 = no expiry). */
        @Min(0)
        private long defaultTtl = 3600;

        /** Maximum allowed key length in characters. */
        @Min(1)
        private int maxKeyLength = 512;

        /** Maximum allowed value size in bytes. */
        @Min(1)
        private int maxValueSize = 1048576; // 1MB

        /** Namespace prefix added to all keys to avoid collisions. */
        @NotBlank
        private String namespace = "cache-service";
    }

    @Data
    @ConfigurationProperties(prefix = "cache.lock")
    public static class LockSettings {
        /** Default wait time for lock acquisition in milliseconds. */
        @Min(0)
        private long defaultWaitTime = 5000;

        /** Default lease time (lock TTL) in milliseconds. */
        @Min(1)
        private long defaultLeaseTime = 30000;
    }
}
