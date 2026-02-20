package com.example.cacheservice.integration;

import com.example.cacheservice.service.CacheService;
import com.redis.testcontainers.RedisContainer;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration tests that spin up a real Redis instance via Testcontainers.
 *
 * <p>These tests verify end-to-end behaviour across the full Spring context
 * including serialisation, connection pooling, and actual Redis commands.
 *
 * <p>They are excluded from the default Surefire run and executed only by
 * Maven Failsafe ({@code mvn verify}) to keep the standard build fast.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Testcontainers
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
@DisplayName("Cache Service – Integration Tests (Testcontainers)")
class CacheIntegrationTest {

    @Container
    static final RedisContainer REDIS = new RedisContainer(DockerImageName.parse("redis:7-alpine"))
            .withExposedPorts(6379);

    /** Wire the Testcontainers Redis port into Spring's Redis configuration. */
    @DynamicPropertySource
    static void redisProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.redis.host", REDIS::getHost);
        registry.add("spring.redis.port", () -> REDIS.getMappedPort(6379));
        registry.add("spring.redis.mode", () -> "standalone");
        registry.add("spring.redis.password", () -> "");
    }

    @Autowired
    private CacheService cacheService;

    // ─── String operations ────────────────────────────────────────────────────

    @Test
    @Order(1)
    @DisplayName("set and get round-trip works for a plain string value")
    void setAndGet_plainString_roundTrips() {
        cacheService.set("int:key", "hello-world");

        Optional<String> result = cacheService.get("int:key");

        assertThat(result).isPresent().contains("hello-world");
    }

    @Test
    @Order(2)
    @DisplayName("set with TTL expires after the configured duration")
    void setWithTtl_keyHasTtl() {
        cacheService.set("ttl:key", "ephemeral", 300L);

        long ttl = cacheService.ttl("ttl:key");

        assertThat(ttl).isGreaterThan(0).isLessThanOrEqualTo(300L);
    }

    @Test
    @Order(3)
    @DisplayName("setIfAbsent succeeds when key does not exist")
    void setIfAbsent_newKey_returnsTrue() {
        boolean created = cacheService.setIfAbsent("nx:unique", "value", 60L);

        assertThat(created).isTrue();
    }

    @Test
    @Order(4)
    @DisplayName("setIfAbsent fails when key already exists")
    void setIfAbsent_existingKey_returnsFalse() {
        cacheService.set("nx:taken", "original");

        boolean created = cacheService.setIfAbsent("nx:taken", "override", 60L);

        assertThat(created).isFalse();
        assertThat(cacheService.get("nx:taken")).contains("original");
    }

    @Test
    @Order(5)
    @DisplayName("delete removes the key from Redis")
    void delete_existingKey_keyGone() {
        cacheService.set("del:me", "bye");
        assertThat(cacheService.exists("del:me")).isTrue();

        boolean deleted = cacheService.delete("del:me");

        assertThat(deleted).isTrue();
        assertThat(cacheService.exists("del:me")).isFalse();
    }

    // ─── Bulk operations ──────────────────────────────────────────────────────

    @Test
    @Order(6)
    @DisplayName("mset and mget round-trip works for multiple keys")
    void msetAndMget_multipleKeys_roundTrip() {
        Map<String, String> entries = Map.of(
                "bulk:a", "alpha",
                "bulk:b", "beta",
                "bulk:c", "gamma"
        );
        cacheService.mset(entries);

        Map<String, String> result = cacheService.mget(entries.keySet());

        assertThat(result)
                .containsEntry("bulk:a", "alpha")
                .containsEntry("bulk:b", "beta")
                .containsEntry("bulk:c", "gamma");
    }

    @Test
    @Order(7)
    @DisplayName("mget returns null for absent keys without throwing")
    void mget_absentKeys_returnsNullValues() {
        Map<String, String> result = cacheService.mget(Set.of("absent:x", "absent:y"));

        assertThat(result).containsKey("absent:x");
        assertThat(result.get("absent:x")).isNull();
    }

    // ─── Scan ─────────────────────────────────────────────────────────────────

    @Test
    @Order(8)
    @DisplayName("scan with pattern returns matching keys")
    void scan_patternMatch_returnsMatchingKeys() {
        cacheService.set("scan:1", "one");
        cacheService.set("scan:2", "two");
        cacheService.set("scan:3", "three");
        cacheService.set("other:1", "nope");

        Set<String> keys = cacheService.scan("scan:*", 100);

        assertThat(keys).containsExactlyInAnyOrder("scan:1", "scan:2", "scan:3");
    }

    @Test
    @Order(9)
    @DisplayName("deleteByPattern removes all matching keys and returns count")
    void deleteByPattern_matchingKeys_deletedCount() {
        cacheService.set("del-pat:a", "1");
        cacheService.set("del-pat:b", "2");

        long deleted = cacheService.deleteByPattern("del-pat:*");

        assertThat(deleted).isGreaterThanOrEqualTo(2);
        assertThat(cacheService.exists("del-pat:a")).isFalse();
    }

    // ─── Hash operations ──────────────────────────────────────────────────────

    @Test
    @Order(10)
    @DisplayName("hset and hget round-trip for a hash field")
    void hsetAndHget_singleField_roundTrips() {
        cacheService.hset("hash:user:1", "name", "Carol");

        Optional<Object> result = cacheService.hget("hash:user:1", "name");

        assertThat(result).isPresent();
        assertThat(result.get().toString()).isEqualTo("Carol");
    }

    @Test
    @Order(11)
    @DisplayName("hgetAll returns all fields of the hash")
    void hgetAll_existingHash_returnsAllFields() {
        cacheService.hset("hash:obj:1", "a", "alpha");
        cacheService.hset("hash:obj:1", "b", "beta");

        Map<Object, Object> fields = cacheService.hgetAll("hash:obj:1");

        assertThat(fields).containsKeys("a", "b");
    }

    @Test
    @Order(12)
    @DisplayName("hdel removes the specified field from the hash")
    void hdel_existingField_fieldRemoved() {
        cacheService.hset("hash:del:1", "to-remove", "value");

        boolean deleted = cacheService.hdel("hash:del:1", "to-remove");

        assertThat(deleted).isTrue();
        assertThat(cacheService.hget("hash:del:1", "to-remove")).isEmpty();
    }

    // ─── Counter operations ───────────────────────────────────────────────────

    @Test
    @Order(13)
    @DisplayName("increment/decrement maintains correct value sequence")
    void counterOperations_sequence() {
        String key = "counter:seq";
        cacheService.delete(key); // ensure clean state

        assertThat(cacheService.increment(key)).isEqualTo(1L);
        assertThat(cacheService.increment(key)).isEqualTo(2L);
        assertThat(cacheService.incrementBy(key, 8L)).isEqualTo(10L);
        assertThat(cacheService.decrement(key)).isEqualTo(9L);
    }

    // ─── Server info ──────────────────────────────────────────────────────────

    @Test
    @Order(14)
    @DisplayName("getServerInfo returns a non-empty map with redis_version")
    void getServerInfo_returnsUsefulFields() {
        Map<String, String> info = cacheService.getServerInfo();

        assertThat(info).isNotEmpty();
        assertThat(info).containsKey("redis_version");
    }
}
