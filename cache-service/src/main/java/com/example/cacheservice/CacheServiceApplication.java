package com.example.cacheservice;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

/**
 * Cache Service Application entry point.
 *
 * <p>Provides a RESTful API for Redis cache operations supporting three deployment modes:
 * <ul>
 *   <li><b>standalone</b> - Single Redis instance (development/simple deployments)</li>
 *   <li><b>sentinel</b>   - Redis Sentinel for high-availability with automatic failover</li>
 *   <li><b>cluster</b>    - Redis Cluster for horizontal scaling and data sharding</li>
 * </ul>
 *
 * <p>Uses two Redis clients:
 * <ul>
 *   <li><b>Lettuce</b>   - Primary client via Spring Data Redis for cache CRUD operations</li>
 *   <li><b>Redisson</b>  - Secondary client for distributed primitives (locks, rate limiters)</li>
 * </ul>
 *
 * <p>Start with: {@code SPRING_PROFILES_ACTIVE=standalone mvn spring-boot:run}
 */
@SpringBootApplication
@ConfigurationPropertiesScan("com.example.cacheservice.infrastructure.config")
public class CacheServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(CacheServiceApplication.class, args);
    }
}
