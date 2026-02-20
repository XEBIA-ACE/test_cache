package com.example.cacheservice.config;

import io.lettuce.core.ClientOptions;
import io.lettuce.core.SocketOptions;
import io.lettuce.core.cluster.ClusterClientOptions;
import io.lettuce.core.cluster.ClusterTopologyRefreshOptions;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.pool2.impl.GenericObjectPoolConfig;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.data.redis.connection.*;
import org.springframework.data.redis.connection.lettuce.LettuceClientConfiguration;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.connection.lettuce.LettucePoolingClientConfiguration;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.serializer.GenericJackson2JsonRedisSerializer;
import org.springframework.data.redis.serializer.StringRedisSerializer;
import org.springframework.util.StringUtils;

import java.time.Duration;
import java.util.Arrays;
import java.util.stream.Collectors;

/**
 * Redis / Lettuce connection configuration.
 *
 * <p>Supports three deployment modes driven by {@code spring.redis.mode}:
 * <ul>
 *   <li><b>standalone</b> – single-node connection (default)</li>
 *   <li><b>sentinel</b>   – high-availability via Redis Sentinel</li>
 *   <li><b>cluster</b>    – horizontal sharding via Redis Cluster</li>
 * </ul>
 *
 * <p>Lettuce is configured with a commons-pool2 connection pool and adaptive
 * cluster topology refresh (when cluster mode is active).
 */
@Slf4j
@Configuration
@RequiredArgsConstructor
public class RedisConfig {

    private final RedisProperties props;

    // ─── Connection Factory ──────────────────────────────────────────────────

    /**
     * Primary {@link RedisConnectionFactory} backed by Lettuce.
     * The factory is @Primary so that Spring Data Redis auto-configuration
     * and Redisson each use the same pool settings without conflict.
     */
    @Bean
    @Primary
    public LettuceConnectionFactory redisConnectionFactory() {
        LettuceClientConfiguration clientConfig = buildLettuceClientConfiguration();

        LettuceConnectionFactory factory = switch (props.getMode().toLowerCase()) {
            case "sentinel" -> createSentinelFactory(clientConfig);
            case "cluster"  -> createClusterFactory(clientConfig);
            default         -> createStandaloneFactory(clientConfig);
        };

        log.info("Redis connection factory initialised in '{}' mode", props.getMode());
        return factory;
    }

    // ─── RedisTemplate ───────────────────────────────────────────────────────

    /**
     * General-purpose {@link RedisTemplate} that serialises keys as plain
     * UTF-8 strings and values as JSON (Jackson).
     */
    @Bean
    public RedisTemplate<String, Object> redisTemplate(LettuceConnectionFactory factory) {
        RedisTemplate<String, Object> template = new RedisTemplate<>();
        template.setConnectionFactory(factory);

        StringRedisSerializer stringSerializer = new StringRedisSerializer();
        GenericJackson2JsonRedisSerializer jsonSerializer = new GenericJackson2JsonRedisSerializer();

        template.setKeySerializer(stringSerializer);
        template.setHashKeySerializer(stringSerializer);
        template.setValueSerializer(jsonSerializer);
        template.setHashValueSerializer(jsonSerializer);
        template.setDefaultSerializer(jsonSerializer);
        template.afterPropertiesSet();

        return template;
    }

    /**
     * Convenience template for plain string key/value operations.
     */
    @Bean
    public StringRedisTemplate stringRedisTemplate(LettuceConnectionFactory factory) {
        return new StringRedisTemplate(factory);
    }

    // ─── Internal helpers ────────────────────────────────────────────────────

    private LettuceConnectionFactory createStandaloneFactory(LettuceClientConfiguration clientConfig) {
        RedisStandaloneConfiguration config = new RedisStandaloneConfiguration(
                props.getHost(), props.getPort());
        config.setDatabase(props.getDatabase());
        if (StringUtils.hasText(props.getPassword())) {
            config.setPassword(props.getPassword());
        }
        return new LettuceConnectionFactory(config, clientConfig);
    }

    private LettuceConnectionFactory createSentinelFactory(LettuceClientConfiguration clientConfig) {
        RedisSentinelConfiguration config = new RedisSentinelConfiguration();
        config.setMaster(props.getSentinel().getMaster());
        config.setDatabase(props.getDatabase());

        if (StringUtils.hasText(props.getPassword())) {
            config.setPassword(props.getPassword());
        }
        if (StringUtils.hasText(props.getSentinel().getPassword())) {
            config.setSentinelPassword(props.getSentinel().getPassword());
        }

        Arrays.stream(props.getSentinel().getNodes().split(","))
                .map(String::trim)
                .filter(StringUtils::hasText)
                .forEach(node -> {
                    String[] parts = node.split(":");
                    config.addSentinel(new RedisNode(parts[0].trim(),
                            Integer.parseInt(parts[1].trim())));
                });

        log.info("Redis Sentinel master='{}', sentinels={}",
                props.getSentinel().getMaster(), props.getSentinel().getNodes());
        return new LettuceConnectionFactory(config, clientConfig);
    }

    private LettuceConnectionFactory createClusterFactory(LettuceClientConfiguration clientConfig) {
        RedisClusterConfiguration config = new RedisClusterConfiguration();
        config.setMaxRedirects(props.getCluster().getMaxRedirects());

        if (StringUtils.hasText(props.getCluster().getPassword())) {
            config.setPassword(props.getCluster().getPassword());
        } else if (StringUtils.hasText(props.getPassword())) {
            config.setPassword(props.getPassword());
        }

        Arrays.stream(props.getCluster().getNodes().split(","))
                .map(String::trim)
                .filter(StringUtils::hasText)
                .forEach(node -> {
                    String[] parts = node.split(":");
                    config.addClusterNode(new RedisNode(parts[0].trim(),
                            Integer.parseInt(parts[1].trim())));
                });

        log.info("Redis Cluster nodes={}", props.getCluster().getNodes());
        return new LettuceConnectionFactory(config, clientConfig);
    }

    /**
     * Builds a pooled Lettuce client configuration with topology refresh
     * enabled for cluster mode.
     */
    private LettuceClientConfiguration buildLettuceClientConfiguration() {
        GenericObjectPoolConfig<?> poolConfig = new GenericObjectPoolConfig<>();
        poolConfig.setMaxTotal(props.getLettuce().getPool().getMaxActive());
        poolConfig.setMaxIdle(props.getLettuce().getPool().getMaxIdle());
        poolConfig.setMinIdle(props.getLettuce().getPool().getMinIdle());
        poolConfig.setMaxWait(Duration.ofMillis(props.getLettuce().getPool().getMaxWait()));
        poolConfig.setTestOnBorrow(true);
        poolConfig.setTestWhileIdle(true);

        LettucePoolingClientConfiguration.LettucePoolingClientConfigurationBuilder builder =
                LettucePoolingClientConfiguration.builder()
                        .poolConfig(poolConfig)
                        .commandTimeout(Duration.ofMillis(props.getCommandTimeout()))
                        .shutdownTimeout(Duration.ZERO);

        // For cluster mode, enable adaptive topology refresh so the client
        // automatically discovers new nodes and handles failovers.
        if ("cluster".equalsIgnoreCase(props.getMode())) {
            ClusterTopologyRefreshOptions topologyRefreshOptions =
                    ClusterTopologyRefreshOptions.builder()
                            .enableAdaptiveRefreshTrigger(
                                    ClusterTopologyRefreshOptions.RefreshTrigger.MOVED_REDIRECT,
                                    ClusterTopologyRefreshOptions.RefreshTrigger.PERSISTENT_RECONNECTS)
                            .adaptiveRefreshTriggersTimeout(Duration.ofSeconds(30))
                            .enablePeriodicRefresh(Duration.ofMinutes(1))
                            .build();

            ClusterClientOptions clusterClientOptions = ClusterClientOptions.builder()
                    .topologyRefreshOptions(topologyRefreshOptions)
                    .autoReconnect(true)
                    .build();

            builder.clientOptions(clusterClientOptions);
        } else {
            ClientOptions clientOptions = ClientOptions.builder()
                    .socketOptions(SocketOptions.builder()
                            .connectTimeout(Duration.ofMillis(props.getCommandTimeout()))
                            .build())
                    .autoReconnect(true)
                    .build();
            builder.clientOptions(clientOptions);
        }

        return builder.build();
    }
}
