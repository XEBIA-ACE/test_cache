package com.example.cacheservice.infrastructure.config;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.redisson.Redisson;
import org.redisson.api.RedissonClient;
import org.redisson.config.Config;
import org.redisson.config.ReadMode;
import org.redisson.config.SubscriptionMode;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.util.StringUtils;

import java.util.List;

/**
 * Redisson client configuration for distributed operations.
 *
 * <p>Redisson is used exclusively for distributed primitives that Lettuce does not natively support:
 * <ul>
 *   <li>Distributed locks ({@code RLock})</li>
 *   <li>Rate limiters ({@code RRateLimiter})</li>
 *   <li>Pub/Sub messaging ({@code RTopic})</li>
 *   <li>Distributed collections ({@code RMap}, {@code RSet})</li>
 * </ul>
 *
 * <p>Redisson is intentionally kept separate from the Lettuce-based {@link LettuceConfig}
 * to allow each client to be optimized for its specific use case without conflict.
 */
@Slf4j
@Configuration
@RequiredArgsConstructor
public class RedissonConfig {

    private final RedisProperties redisProperties;

    /**
     * Creates and configures the Redisson client for the active Redis topology.
     * The bean is destroyed gracefully on application shutdown.
     */
    @Bean(destroyMethod = "shutdown")
    public RedissonClient redissonClient() {
        Config config = new Config();

        log.info("Configuring Redisson client in {} mode", redisProperties.getMode());

        switch (redisProperties.getMode()) {
            case SENTINEL -> configureSentinel(config);
            case CLUSTER -> configureCluster(config);
            default -> configureStandalone(config);
        }

        return Redisson.create(config);
    }

    // ----------------------------------------------------------------
    // Private configuration helpers
    // ----------------------------------------------------------------

    private void configureStandalone(Config config) {
        String address = formatRedisAddress(redisProperties.getHost(), redisProperties.getPort());
        log.debug("Redisson standalone config: {}", address);

        config.useSingleServer()
            .setAddress(address)
            .setDatabase(redisProperties.getDatabase())
            .setTimeout((int) redisProperties.getTimeout())
            .setConnectTimeout((int) redisProperties.getTimeout())
            .setConnectionMinimumIdleSize(redisProperties.getPool().getMinIdle())
            .setConnectionPoolSize(redisProperties.getPool().getMaxActive())
            .setRetryAttempts(3)
            .setRetryInterval(1500)
            .setPassword(nullIfEmpty(redisProperties.getPassword()));
    }

    private void configureSentinel(Config config) {
        RedisProperties.SentinelProperties sentinel = redisProperties.getSentinel();
        List<String> sentinelAddresses = sentinel.getNodes().stream()
            .map(node -> {
                String[] parts = node.split(":");
                return formatRedisAddress(parts[0], Integer.parseInt(parts[1]));
            })
            .toList();

        log.debug("Redisson sentinel config: master={}, sentinels={}", sentinel.getMaster(), sentinelAddresses);

        config.useSentinelServers()
            .setMasterName(sentinel.getMaster())
            .addSentinelAddress(sentinelAddresses.toArray(new String[0]))
            .setDatabase(redisProperties.getDatabase())
            .setTimeout((int) redisProperties.getTimeout())
            .setConnectTimeout((int) redisProperties.getTimeout())
            .setMasterConnectionMinimumIdleSize(redisProperties.getPool().getMinIdle())
            .setMasterConnectionPoolSize(redisProperties.getPool().getMaxActive())
            .setSlaveConnectionMinimumIdleSize(2)
            .setSlaveConnectionPoolSize(8)
            .setReadMode(ReadMode.MASTER_SLAVE)
            .setSubscriptionMode(SubscriptionMode.MASTER)
            .setRetryAttempts(3)
            .setRetryInterval(1500)
            .setPassword(nullIfEmpty(redisProperties.getPassword()))
            .setSentinelPassword(nullIfEmpty(sentinel.getPassword()));
    }

    private void configureCluster(Config config) {
        RedisProperties.ClusterProperties cluster = redisProperties.getCluster();
        List<String> nodeAddresses = cluster.getNodes().stream()
            .map(node -> {
                String[] parts = node.split(":");
                return formatRedisAddress(parts[0], Integer.parseInt(parts[1]));
            })
            .toList();

        log.debug("Redisson cluster config: nodes={}", nodeAddresses);

        config.useClusterServers()
            .addNodeAddress(nodeAddresses.toArray(new String[0]))
            .setTimeout((int) redisProperties.getTimeout())
            .setConnectTimeout((int) redisProperties.getTimeout())
            .setMasterConnectionMinimumIdleSize(redisProperties.getPool().getMinIdle())
            .setMasterConnectionPoolSize(redisProperties.getPool().getMaxActive())
            .setSlaveConnectionMinimumIdleSize(2)
            .setSlaveConnectionPoolSize(8)
            .setReadMode(ReadMode.MASTER_SLAVE)
            .setSubscriptionMode(SubscriptionMode.MASTER)
            .setRetryAttempts(3)
            .setRetryInterval(1500)
            .setScanInterval(2000)  // Topology scan interval in ms
            .setPassword(nullIfEmpty(redisProperties.getPassword()));
    }

    // ----------------------------------------------------------------
    // Utilities
    // ----------------------------------------------------------------

    private String formatRedisAddress(String host, int port) {
        return "redis://" + host + ":" + port;
    }

    private String nullIfEmpty(String value) {
        return StringUtils.hasText(value) ? value : null;
    }
}
