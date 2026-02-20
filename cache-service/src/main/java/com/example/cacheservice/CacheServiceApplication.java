package com.example.cacheservice;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cache.annotation.EnableCaching;

/**
 * Entry point for the Cache Service.
 *
 * <p>Supports three Redis deployment modes controlled by the {@code spring.redis.mode} property:
 * <ul>
 *   <li><b>standalone</b> – single Redis node (default, great for dev/test)</li>
 *   <li><b>sentinel</b>   – Redis Sentinel for automatic failover (HA)</li>
 *   <li><b>cluster</b>    – Redis Cluster for horizontal sharding</li>
 * </ul>
 *
 * <p>Two Redis client libraries are wired:
 * <ul>
 *   <li><b>Lettuce</b>   – primary client via Spring Data Redis for all cache CRUD operations</li>
 *   <li><b>Redisson</b>  – secondary client for distributed locks and advanced data structures</li>
 * </ul>
 */
@SpringBootApplication
@EnableCaching
public class CacheServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(CacheServiceApplication.class, args);
    }
}
