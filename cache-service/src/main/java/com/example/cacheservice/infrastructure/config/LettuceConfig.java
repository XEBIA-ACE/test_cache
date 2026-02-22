package com.example.cacheservice.infrastructure.config;

import io.lettuce.core.ClientOptions;
import io.lettuce.core.SocketOptions;
import io.lettuce.core.TimeoutOptions;
import io.lettuce.core.cluster.ClusterClientOptions;
import io.lettuce.core.cluster.ClusterTopologyRefreshOptions;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.pool2.impl.GenericObjectPoolConfig;
import org.springframework.boot.autoconfigure.data.redis.RedisAutoConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.data.redis.connection.*;
import org.springframework.data.redis.connection.lettuce.LettuceClientConfiguration;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.connection.lettuce.LettucePoolingClientConfiguration;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.serializer.StringRedisSerializer;
import org.springframework.util.StringUtils;

import java.time.Duration;
import java.util.List;

/**
 * Lettuce Redis client configuration.
 *
 * <p>Configures a connection factory supporting three deployment modes:
 * <ul>
 *   <li><b>Standalone</b>: {@link RedisStandaloneConfiguration}</li>
 *   <li><b>Sentinel</b>:   {@link RedisSentinelConfiguration} with automatic failover</li>
 *   <li><b>Cluster</b>:    {@link RedisClusterConfiguration} with adaptive topology refresh</li>
 * </ul>
 *
 * <p>All modes use connection pooling via commons-pool2 for improved throughput
 * and reduced connection overhead.
 */
@Slf4j
@Configuration
@RequiredArgsConstructor
@Import(RedisAutoConfiguration.class)
public class LettuceConfig {

    private final RedisProperties redisProperties;

    /**
     * Creates a {@link LettuceConnectionFactory} configured for the active Redis topology.
     */
    @Bean
    public LettuceConnectionFactory lettuceConnectionFactory() {
        LettuceClientConfiguration clientConfig = buildClientConfiguration();
        AbstractRedisConfiguration serverConfig = buildServerConfiguration();

        log.info("Configuring Lettuce connection factory in {} mode", redisProperties.getMode());

        LettuceConnectionFactory factory = new LettuceConnectionFactory(serverConfig, clientConfig);
        factory.setValidateConnection(true);
        return factory;
    }

    /**
     * Primary Redis template using String serialization for both keys and values.
     * All cache values are stored as UTF-8 strings.
     */
    @Bean
    public StringRedisTemplate stringRedisTemplate(LettuceConnectionFactory connectionFactory) {
        StringRedisTemplate template = new StringRedisTemplate();
        template.setConnectionFactory(connectionFactory);
        template.setKeySerializer(new StringRedisSerializer());
        template.setValueSerializer(new StringRedisSerializer());
        template.setHashKeySerializer(new StringRedisSerializer());
        template.setHashValueSerializer(new StringRedisSerializer());
        template.afterPropertiesSet();
        return template;
    }

    // ----------------------------------------------------------------
    // Private helpers
    // ----------------------------------------------------------------

    private LettuceClientConfiguration buildClientConfiguration() {
        RedisProperties.PoolProperties pool = redisProperties.getPool();

        GenericObjectPoolConfig<Object> poolConfig = new GenericObjectPoolConfig<>();
        poolConfig.setMaxTotal(pool.getMaxActive());
        poolConfig.setMaxIdle(pool.getMaxIdle());
        poolConfig.setMinIdle(pool.getMinIdle());
        poolConfig.setMaxWait(Duration.ofMillis(pool.getMaxWait()));
        poolConfig.setTestOnBorrow(true);
        poolConfig.setTestWhileIdle(true);
        poolConfig.setTimeBetweenEvictionRuns(Duration.ofSeconds(30));

        Duration commandTimeout = Duration.ofMillis(redisProperties.getTimeout());

        if (redisProperties.getMode() == RedisProperties.Mode.CLUSTER) {
            // Cluster mode requires specific client options for topology refresh
            ClusterTopologyRefreshOptions topologyRefresh = ClusterTopologyRefreshOptions.builder()
                .enablePeriodicRefresh(Duration.ofSeconds(30))
                .enableAllAdaptiveRefreshTriggers()
                .adaptiveRefreshTriggersTimeout(Duration.ofSeconds(30))
                .build();

            ClusterClientOptions clusterOptions = ClusterClientOptions.builder()
                .topologyRefreshOptions(topologyRefresh)
                .maxRedirects(redisProperties.getCluster().getMaxRedirects())
                .validateClusterNodeMembership(true)
                .socketOptions(SocketOptions.builder()
                    .connectTimeout(commandTimeout)
                    .build())
                .timeoutOptions(TimeoutOptions.enabled(commandTimeout))
                .build();

            return LettucePoolingClientConfiguration.builder()
                .poolConfig(poolConfig)
                .commandTimeout(commandTimeout)
                .clientOptions(clusterOptions)
                .build();
        }

        ClientOptions clientOptions = ClientOptions.builder()
            .socketOptions(SocketOptions.builder()
                .connectTimeout(commandTimeout)
                .build())
            .timeoutOptions(TimeoutOptions.enabled(commandTimeout))
            .autoReconnect(true)
            .disconnectedBehavior(ClientOptions.DisconnectedBehavior.REJECT_COMMANDS)
            .build();

        return LettucePoolingClientConfiguration.builder()
            .poolConfig(poolConfig)
            .commandTimeout(commandTimeout)
            .clientOptions(clientOptions)
            .build();
    }

    private AbstractRedisConfiguration buildServerConfiguration() {
        return switch (redisProperties.getMode()) {
            case SENTINEL -> buildSentinelConfiguration();
            case CLUSTER -> buildClusterConfiguration();
            default -> buildStandaloneConfiguration();
        };
    }

    private RedisStandaloneConfiguration buildStandaloneConfiguration() {
        log.debug("Building standalone Redis config: {}:{}", redisProperties.getHost(), redisProperties.getPort());

        RedisStandaloneConfiguration config = new RedisStandaloneConfiguration(
            redisProperties.getHost(), redisProperties.getPort());
        config.setDatabase(redisProperties.getDatabase());
        if (StringUtils.hasText(redisProperties.getPassword())) {
            config.setPassword(redisProperties.getPassword());
        }
        return config;
    }

    private RedisSentinelConfiguration buildSentinelConfiguration() {
        RedisProperties.SentinelProperties sentinel = redisProperties.getSentinel();
        log.debug("Building Sentinel Redis config: master={}, nodes={}", sentinel.getMaster(), sentinel.getNodes());

        RedisSentinelConfiguration config = new RedisSentinelConfiguration();
        config.master(sentinel.getMaster());
        config.setDatabase(redisProperties.getDatabase());

        // Parse "host:port" strings into sentinel nodes
        sentinel.getNodes().stream()
            .map(this::parseHostPort)
            .forEach(hp -> config.sentinel(hp[0], Integer.parseInt(hp[1])));

        if (StringUtils.hasText(redisProperties.getPassword())) {
            config.setPassword(redisProperties.getPassword());
        }
        if (StringUtils.hasText(sentinel.getPassword())) {
            config.setSentinelPassword(sentinel.getPassword());
        }
        return config;
    }

    private RedisClusterConfiguration buildClusterConfiguration() {
        RedisProperties.ClusterProperties cluster = redisProperties.getCluster();
        log.debug("Building Cluster Redis config: nodes={}", cluster.getNodes());

        RedisClusterConfiguration config = new RedisClusterConfiguration(cluster.getNodes());
        config.setMaxRedirects(cluster.getMaxRedirects());

        if (StringUtils.hasText(redisProperties.getPassword())) {
            config.setPassword(redisProperties.getPassword());
        }
        return config;
    }

    /**
     * Parses a "host:port" string into a two-element array.
     */
    private String[] parseHostPort(String hostPort) {
        int lastColon = hostPort.lastIndexOf(':');
        if (lastColon < 0) {
            throw new IllegalArgumentException("Invalid host:port format: " + hostPort);
        }
        return new String[]{hostPort.substring(0, lastColon), hostPort.substring(lastColon + 1)};
    }

    /**
     * Resolves comma-separated node strings from environment variables into a List.
     */
    public static List<String> parseNodeList(String nodesEnv) {
        return List.of(nodesEnv.split(",")).stream()
            .map(String::trim)
            .filter(s -> !s.isEmpty())
            .toList();
    }
}
