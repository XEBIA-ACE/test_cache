package com.example.cacheservice.config;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.redisson.Redisson;
import org.redisson.api.RedissonClient;
import org.redisson.config.*;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.util.StringUtils;

import java.util.Arrays;

/**
 * Redisson client configuration.
 *
 * <p>Redisson is used alongside Lettuce (Spring Data Redis) for features that
 * require distributed semantics: distributed locks, rate limiters, and
 * pub/sub messaging. The mode mirrors {@link RedisConfig} – standalone,
 * sentinel, or cluster – so both clients point at the same Redis topology.
 *
 * <p><b>Why a separate client?</b><br>
 * Lettuce (via Spring Data Redis) covers low-level cache operations with
 * excellent throughput. Redisson adds a richer, cluster-aware implementation
 * of distributed primitives (RLock, RSemaphore, RRateLimiter, etc.) that
 * would require significant boilerplate to replicate with Lettuce alone.
 */
@Slf4j
@Configuration
@RequiredArgsConstructor
public class RedissonConfig {

    private final RedisProperties props;

    /**
     * Creates and returns a {@link RedissonClient} whose lifecycle is managed
     * by Spring. The {@code destroyMethod = "shutdown"} ensures a graceful
     * connection-pool teardown on application stop.
     */
    @Bean(destroyMethod = "shutdown")
    public RedissonClient redissonClient() {
        Config config = new Config();

        switch (props.getMode().toLowerCase()) {
            case "sentinel" -> configureSentinel(config);
            case "cluster"  -> configureCluster(config);
            default         -> configureSingleServer(config);
        }

        log.info("Redisson client initialised in '{}' mode", props.getMode());
        return Redisson.create(config);
    }

    // ─── Mode-specific builders ───────────────────────────────────────────────

    private void configureSingleServer(Config config) {
        SingleServerConfig server = config.useSingleServer()
                .setAddress(redisAddress(props.getHost(), props.getPort()))
                .setDatabase(props.getDatabase())
                .setConnectionMinimumIdleSize(props.getRedisson().getConnectionMinimumIdleSize())
                .setConnectionPoolSize(props.getRedisson().getConnectionPoolSize())
                .setIdleConnectionTimeout(props.getRedisson().getIdleConnectionTimeout())
                .setConnectTimeout(props.getRedisson().getConnectTimeout())
                .setTimeout(props.getRedisson().getTimeout())
                .setRetryAttempts(props.getRedisson().getRetryAttempts())
                .setRetryInterval(props.getRedisson().getRetryInterval());

        if (StringUtils.hasText(props.getPassword())) {
            server.setPassword(props.getPassword());
        }
    }

    private void configureSentinel(Config config) {
        SentinelServersConfig sentinel = config.useSentinelServers()
                .setMasterName(props.getSentinel().getMaster())
                .setDatabase(props.getDatabase())
                .setMasterConnectionMinimumIdleSize(props.getRedisson().getConnectionMinimumIdleSize())
                .setMasterConnectionPoolSize(props.getRedisson().getConnectionPoolSize())
                .setSlaveConnectionMinimumIdleSize(props.getRedisson().getConnectionMinimumIdleSize())
                .setSlaveConnectionPoolSize(props.getRedisson().getConnectionPoolSize())
                .setConnectTimeout(props.getRedisson().getConnectTimeout())
                .setTimeout(props.getRedisson().getTimeout())
                .setRetryAttempts(props.getRedisson().getRetryAttempts())
                .setRetryInterval(props.getRedisson().getRetryInterval());

        // Add each sentinel node
        Arrays.stream(props.getSentinel().getNodes().split(","))
                .map(String::trim)
                .filter(StringUtils::hasText)
                .map(node -> "redis://" + node)
                .forEach(sentinel::addSentinelAddress);

        if (StringUtils.hasText(props.getPassword())) {
            sentinel.setPassword(props.getPassword());
        }
        if (StringUtils.hasText(props.getSentinel().getPassword())) {
            sentinel.setSentinelPassword(props.getSentinel().getPassword());
        }
    }

    private void configureCluster(Config config) {
        ClusterServersConfig cluster = config.useClusterServers()
                .setMasterConnectionMinimumIdleSize(props.getRedisson().getConnectionMinimumIdleSize())
                .setMasterConnectionPoolSize(props.getRedisson().getConnectionPoolSize())
                .setSlaveConnectionMinimumIdleSize(props.getRedisson().getConnectionMinimumIdleSize())
                .setSlaveConnectionPoolSize(props.getRedisson().getConnectionPoolSize())
                .setConnectTimeout(props.getRedisson().getConnectTimeout())
                .setTimeout(props.getRedisson().getTimeout())
                .setRetryAttempts(props.getRedisson().getRetryAttempts())
                .setRetryInterval(props.getRedisson().getRetryInterval());

        // Add each cluster node
        Arrays.stream(props.getCluster().getNodes().split(","))
                .map(String::trim)
                .filter(StringUtils::hasText)
                .map(node -> "redis://" + node)
                .forEach(cluster::addNodeAddress);

        if (StringUtils.hasText(props.getCluster().getPassword())) {
            cluster.setPassword(props.getCluster().getPassword());
        } else if (StringUtils.hasText(props.getPassword())) {
            cluster.setPassword(props.getPassword());
        }
    }

    // ─── Utilities ────────────────────────────────────────────────────────────

    private static String redisAddress(String host, int port) {
        return String.format("redis://%s:%d", host, port);
    }
}
