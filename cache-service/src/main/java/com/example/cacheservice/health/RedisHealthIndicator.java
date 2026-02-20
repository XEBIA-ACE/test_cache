package com.example.cacheservice.health;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.util.Properties;

/**
 * Custom {@link HealthIndicator} that performs a lightweight Redis PING and
 * enriches the health payload with key Redis INFO fields (version, role,
 * memory usage). Registered under the name {@code redis} in the Actuator
 * health endpoint.
 */
@Slf4j
@Component("redisCustom")
@RequiredArgsConstructor
public class RedisHealthIndicator implements HealthIndicator {

    private final StringRedisTemplate stringRedisTemplate;
    private final RedisConnectionFactory connectionFactory;

    @Override
    public Health health() {
        try {
            String pong = stringRedisTemplate.execute(connection -> {
                connection.ping();
                return "PONG";
            }, true);

            if (!"PONG".equals(pong)) {
                return Health.down().withDetail("ping", "unexpected response: " + pong).build();
            }

            // Collect a minimal INFO snapshot to surface in health details
            Properties info = stringRedisTemplate.execute(connection ->
                    connection.serverCommands().info("server", "memory", "replication"), true);

            Health.Builder builder = Health.up()
                    .withDetail("ping", "PONG");

            if (info != null) {
                builder
                        .withDetail("redis_version", info.getProperty("redis_version", "unknown"))
                        .withDetail("role",           info.getProperty("role", "unknown"))
                        .withDetail("used_memory",    info.getProperty("used_memory_human", "unknown"))
                        .withDetail("connected_clients", info.getProperty("connected_clients", "unknown"));
            }

            return builder.build();

        } catch (Exception ex) {
            log.error("Redis health check failed", ex);
            return Health.down(ex).withDetail("error", ex.getMessage()).build();
        }
    }
}
