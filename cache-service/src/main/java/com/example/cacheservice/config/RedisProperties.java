package com.example.cacheservice.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Strongly-typed configuration properties for Redis connectivity.
 * All values can be overridden via environment variables (e.g. SPRING_REDIS_MODE).
 */
@Data
@Component
@ConfigurationProperties(prefix = "spring.redis")
public class RedisProperties {

    /** Deployment mode: standalone | sentinel | cluster */
    private String mode = "standalone";

    // ── Standalone ───────────────────────────────────────────────────────────
    private String host = "localhost";
    private int port = 6379;
    private String password = "";
    private int database = 0;
    private long commandTimeout = 5000;

    // ── Sentinel ─────────────────────────────────────────────────────────────
    private Sentinel sentinel = new Sentinel();

    // ── Cluster ──────────────────────────────────────────────────────────────
    private Cluster cluster = new Cluster();

    // ── Connection Pool (Lettuce) ─────────────────────────────────────────────
    private Lettuce lettuce = new Lettuce();

    // ── Redisson-specific ────────────────────────────────────────────────────
    private Redisson redisson = new Redisson();

    @Data
    public static class Sentinel {
        /** Logical name of the Redis master monitored by Sentinel. */
        private String master = "mymaster";
        /** Comma-separated list of sentinel nodes: host1:port1,host2:port2 */
        private String nodes = "localhost:26379";
        private String password = "";
    }

    @Data
    public static class Cluster {
        /** Comma-separated list of cluster nodes: host1:port1,host2:port2 */
        private String nodes = "localhost:7000,localhost:7001,localhost:7002";
        private int maxRedirects = 3;
        private String password = "";
    }

    @Data
    public static class Lettuce {
        private Pool pool = new Pool();

        @Data
        public static class Pool {
            private int maxActive = 16;
            private int maxIdle = 8;
            private int minIdle = 2;
            /** -1 means wait indefinitely */
            private long maxWait = -1;
        }
    }

    @Data
    public static class Redisson {
        private int connectionMinimumIdleSize = 2;
        private int connectionPoolSize = 10;
        private int idleConnectionTimeout = 10000;
        private int connectTimeout = 10000;
        private int timeout = 3000;
        private int retryAttempts = 3;
        private int retryInterval = 1500;
    }
}
