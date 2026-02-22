package com.example.cacheservice.infrastructure.health;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.data.redis.connection.RedisConnection;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.connection.RedisServerCommands;
import org.springframework.stereotype.Component;

import java.util.Properties;

/**
 * Custom Redis health indicator that provides detailed connectivity information.
 *
 * <p>Exposed at {@code GET /actuator/health/redis} and aggregated in {@code GET /actuator/health}.
 *
 * <p>Includes Redis server properties in the health response:
 * <ul>
 *   <li>Redis version</li>
 *   <li>Connected clients</li>
 *   <li>Used memory</li>
 *   <li>Uptime</li>
 * </ul>
 */
@Slf4j
@Component("redis")
@RequiredArgsConstructor
public class RedisHealthIndicator implements HealthIndicator {

    private final RedisConnectionFactory connectionFactory;

    @Override
    public Health health() {
        try (RedisConnection connection = connectionFactory.getConnection()) {
            // PING to verify connectivity
            String ping = connection.ping();

            if (!"PONG".equalsIgnoreCase(ping)) {
                return Health.down()
                    .withDetail("error", "Unexpected PING response: " + ping)
                    .build();
            }

            // Collect server info
            Properties info = connection.serverCommands().info("server");
            Properties memInfo = connection.serverCommands().info("memory");
            Properties clientInfo = connection.serverCommands().info("clients");

            Health.Builder builder = Health.up()
                .withDetail("ping", ping);

            if (info != null) {
                builder
                    .withDetail("version", info.getProperty("redis_version", "unknown"))
                    .withDetail("uptime_seconds", info.getProperty("uptime_in_seconds", "unknown"))
                    .withDetail("mode", info.getProperty("redis_mode", "unknown"));
            }
            if (memInfo != null) {
                builder.withDetail("used_memory_human", memInfo.getProperty("used_memory_human", "unknown"));
            }
            if (clientInfo != null) {
                builder.withDetail("connected_clients", clientInfo.getProperty("connected_clients", "unknown"));
            }

            return builder.build();

        } catch (Exception e) {
            log.warn("Redis health check failed: {}", e.getMessage());
            return Health.down()
                .withDetail("error", e.getMessage())
                .withException(e)
                .build();
        }
    }
}
