package com.example.cacheservice.infrastructure.repository;

import com.example.cacheservice.infrastructure.config.CacheSettings;
import com.example.cacheservice.infrastructure.repository.impl.LettuceCacheRepository;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.time.Duration;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.*;

/**
 * Integration tests for {@link LettuceCacheRepository} using a real Redis instance via Testcontainers.
 *
 * <p><b>Requirements:</b> Docker must be running to execute these tests.
 * <p>Run with: {@code mvn verify -P integration-test}
 *
 * <p>Tests verify actual Redis behavior including:
 * <ul>
 *   <li>Key expiration (TTL)</li>
 *   <li>SCAN-based key enumeration</li>
 *   <li>Atomic increment/decrement</li>
 *   <li>Bulk operations</li>
 * </ul>
 */
@SpringBootTest
@Testcontainers
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
@DisplayName("LettuceCacheRepository Integration Tests (requires Docker)")
class LettuceCacheRepositoryIntegrationTest {

    @Container
    static GenericContainer<?> redis = new GenericContainer<>(DockerImageName.parse("redis:7-alpine"))
        .withExposedPorts(6379)
        .withCommand("redis-server", "--save", "", "--appendonly", "no");

    @DynamicPropertySource
    static void redisProperties(DynamicPropertyRegistry registry) {
        registry.add("cache.redis.host", redis::getHost);
        registry.add("cache.redis.port", () -> redis.getMappedPort(6379));
        registry.add("spring.profiles.active", () -> "standalone");
    }

    @Autowired
    private StringRedisTemplate redisTemplate;

    private LettuceCacheRepository repository;

    @BeforeEach
    void setUp() {
        CacheSettings settings = new CacheSettings();
        settings.setNamespace("test");
        settings.setMaxKeyLength(512);
        settings.setMaxValueSize(1048576);

        repository = new LettuceCacheRepository(redisTemplate, settings, new SimpleMeterRegistry());

        // Clean up all test keys before each test
        repository.deleteByPattern("*");
    }

    @Test
    @Order(1)
    @DisplayName("should set and retrieve a value")
    void shouldSetAndGet() {
        repository.set("user:1", "{\"name\":\"Alice\"}", Duration.ofSeconds(60));

        Optional<String> result = repository.get("user:1");

        assertThat(result).isPresent();
        assertThat(result.get()).isEqualTo("{\"name\":\"Alice\"}");
    }

    @Test
    @Order(2)
    @DisplayName("should return empty for non-existent key")
    void shouldReturnEmpty_forMissingKey() {
        Optional<String> result = repository.get("missing:key");
        assertThat(result).isEmpty();
    }

    @Test
    @Order(3)
    @DisplayName("should report correct TTL after set with expiry")
    void shouldReportCorrectTtl() {
        repository.set("session:abc", "data", Duration.ofSeconds(120));

        long ttl = repository.getTtl("session:abc");

        // TTL should be between 118 and 120 seconds (accounting for execution time)
        assertThat(ttl).isBetween(118L, 120L);
    }

    @Test
    @Order(4)
    @DisplayName("should report TTL of -1 for persistent key")
    void shouldReportNegativeTtl_forPersistentKey() {
        repository.set("persistent:key", "value");

        long ttl = repository.getTtl("persistent:key");

        assertThat(ttl).isEqualTo(-1L);
    }

    @Test
    @Order(5)
    @DisplayName("should delete existing key and return true")
    void shouldDeleteKey_andReturnTrue() {
        repository.set("to:delete", "bye");

        boolean deleted = repository.delete("to:delete");

        assertThat(deleted).isTrue();
        assertThat(repository.exists("to:delete")).isFalse();
    }

    @Test
    @Order(6)
    @DisplayName("should return false when deleting non-existent key")
    void shouldReturnFalse_whenDeletingMissingKey() {
        boolean deleted = repository.delete("nonexistent:key");
        assertThat(deleted).isFalse();
    }

    @Test
    @Order(7)
    @DisplayName("should return matching keys via SCAN")
    void shouldScanKeys_withPattern() {
        repository.set("product:1", "p1", Duration.ofSeconds(60));
        repository.set("product:2", "p2", Duration.ofSeconds(60));
        repository.set("product:3", "p3", Duration.ofSeconds(60));
        repository.set("order:1", "o1", Duration.ofSeconds(60));

        Set<String> productKeys = repository.scan("product:*");

        assertThat(productKeys)
            .hasSize(3)
            .containsExactlyInAnyOrder("product:1", "product:2", "product:3");
    }

    @Test
    @Order(8)
    @DisplayName("should increment counter atomically")
    void shouldIncrementCounter() {
        long v1 = repository.increment("counter:views", 1L);
        long v2 = repository.increment("counter:views", 1L);
        long v3 = repository.increment("counter:views", 5L);

        assertThat(v1).isEqualTo(1L);
        assertThat(v2).isEqualTo(2L);
        assertThat(v3).isEqualTo(7L);
    }

    @Test
    @Order(9)
    @DisplayName("should bulk set and retrieve multiple keys")
    void shouldBulkSetAndGet() {
        Map<String, String> entries = Map.of(
            "bulk:k1", "v1",
            "bulk:k2", "v2",
            "bulk:k3", "v3"
        );

        repository.multiSet(entries, Duration.ofSeconds(60));

        Map<String, String> result = repository.multiGet(entries.keySet());

        assertThat(result).containsAllEntriesOf(entries);
    }

    @Test
    @Order(10)
    @DisplayName("should expire key and make it persistent")
    void shouldExpireKey_andMakePersistent() {
        repository.set("volatile:key", "value", Duration.ofSeconds(100));
        assertThat(repository.getTtl("volatile:key")).isGreaterThan(0);

        // Make persistent
        repository.expire("volatile:key", Duration.ZERO);
        assertThat(repository.getTtl("volatile:key")).isEqualTo(-1L);
    }

    @Test
    @Order(11)
    @DisplayName("should delete all matching keys by pattern")
    void shouldDeleteByPattern() {
        repository.set("temp:a", "1", Duration.ofSeconds(60));
        repository.set("temp:b", "2", Duration.ofSeconds(60));
        repository.set("temp:c", "3", Duration.ofSeconds(60));
        repository.set("keep:x", "x", Duration.ofSeconds(60));

        long deleted = repository.deleteByPattern("temp:*");

        assertThat(deleted).isEqualTo(3L);
        assertThat(repository.exists("keep:x")).isTrue();
        assertThat(repository.exists("temp:a")).isFalse();
    }
}
