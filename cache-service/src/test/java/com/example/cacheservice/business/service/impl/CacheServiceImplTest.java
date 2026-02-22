package com.example.cacheservice.business.service.impl;

import com.example.cacheservice.business.model.CacheEntry;
import com.example.cacheservice.business.service.CacheService;
import com.example.cacheservice.infrastructure.config.CacheSettings;
import com.example.cacheservice.infrastructure.repository.CacheRepository;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Duration;
import java.util.*;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for {@link CacheServiceImpl}.
 *
 * <p>Uses Mockito to isolate the service from its Redis dependency.
 * All business rule validation is tested here without a real Redis instance.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("CacheServiceImpl")
class CacheServiceImplTest {

    @Mock
    private CacheRepository repository;

    private CacheService service;

    @BeforeEach
    void setUp() {
        CacheSettings settings = new CacheSettings();
        settings.setNamespace("test");
        settings.setMaxKeyLength(512);
        settings.setMaxValueSize(1024 * 1024); // 1MB
        settings.setDefaultTtl(3600);

        service = new CacheServiceImpl(repository, settings, new SimpleMeterRegistry());
    }

    @Nested
    @DisplayName("get()")
    class GetTests {

        @Test
        @DisplayName("should return entry when key exists")
        void shouldReturnEntry_whenKeyExists() {
            when(repository.get("user:42")).thenReturn(Optional.of("{\"name\":\"Alice\"}"));
            when(repository.getTtl("user:42")).thenReturn(3598L);

            Optional<CacheEntry> result = service.get("user:42");

            assertThat(result).isPresent();
            assertThat(result.get().getKey()).isEqualTo("user:42");
            assertThat(result.get().getValue()).isEqualTo("{\"name\":\"Alice\"}");
            assertThat(result.get().getTtlSeconds()).isEqualTo(3598L);
        }

        @Test
        @DisplayName("should return empty when key does not exist")
        void shouldReturnEmpty_whenKeyAbsent() {
            when(repository.get("user:99")).thenReturn(Optional.empty());

            Optional<CacheEntry> result = service.get("user:99");

            assertThat(result).isEmpty();
        }

        @Test
        @DisplayName("should throw when key is blank")
        void shouldThrow_whenKeyIsBlank() {
            assertThatIllegalArgumentException().isThrownBy(() -> service.get(""));
            assertThatIllegalArgumentException().isThrownBy(() -> service.get("   "));
        }

        @Test
        @DisplayName("should throw when key exceeds max length")
        void shouldThrow_whenKeyTooLong() {
            String longKey = "k".repeat(513);
            assertThatIllegalArgumentException().isThrownBy(() -> service.get(longKey));
        }
    }

    @Nested
    @DisplayName("set()")
    class SetTests {

        @Test
        @DisplayName("should store entry and return it with TTL")
        void shouldStoreEntry_andReturnWithTtl() {
            String key = "session:abc";
            String value = "{\"userId\":1}";
            Duration ttl = Duration.ofSeconds(300);

            doNothing().when(repository).set(eq(key), eq(value), eq(ttl));

            CacheEntry result = service.set(key, value, ttl);

            assertThat(result.getKey()).isEqualTo(key);
            assertThat(result.getValue()).isEqualTo(value);
            assertThat(result.getTtlSeconds()).isEqualTo(300L);
            verify(repository).set(key, value, ttl);
        }

        @Test
        @DisplayName("should store entry without expiry when TTL is zero")
        void shouldStoreWithNoExpiry_whenTtlIsZero() {
            doNothing().when(repository).set(anyString(), anyString(), eq(Duration.ZERO));

            CacheEntry result = service.set("key", "value", Duration.ZERO);

            assertThat(result.getTtlSeconds()).isEqualTo(-1L);
            assertThat(result.hasExpiry()).isFalse();
        }

        @Test
        @DisplayName("should throw when key contains newline")
        void shouldThrow_whenKeyContainsNewline() {
            assertThatIllegalArgumentException()
                .isThrownBy(() -> service.set("key\ninjection", "value", Duration.ZERO));
        }

        @Test
        @DisplayName("should throw when value exceeds max size")
        void shouldThrow_whenValueTooLarge() {
            String oversizedValue = "x".repeat(1024 * 1024 + 1);
            assertThatIllegalArgumentException()
                .isThrownBy(() -> service.set("key", oversizedValue, Duration.ZERO));
        }
    }

    @Nested
    @DisplayName("delete()")
    class DeleteTests {

        @Test
        @DisplayName("should return true when key was deleted")
        void shouldReturnTrue_whenDeleted() {
            when(repository.delete("user:1")).thenReturn(true);
            assertThat(service.delete("user:1")).isTrue();
        }

        @Test
        @DisplayName("should return false when key did not exist")
        void shouldReturnFalse_whenKeyAbsent() {
            when(repository.delete("user:999")).thenReturn(false);
            assertThat(service.delete("user:999")).isFalse();
        }
    }

    @Nested
    @DisplayName("expire()")
    class ExpireTests {

        @Test
        @DisplayName("should update TTL on existing key")
        void shouldUpdateTtl() {
            when(repository.expire("session:xyz", Duration.ofSeconds(60))).thenReturn(true);
            assertThat(service.expire("session:xyz", Duration.ofSeconds(60))).isTrue();
        }

        @Test
        @DisplayName("should return false when key does not exist")
        void shouldReturnFalse_whenKeyAbsent() {
            when(repository.expire(anyString(), any())).thenReturn(false);
            assertThat(service.expire("missing:key", Duration.ofSeconds(60))).isFalse();
        }
    }

    @Nested
    @DisplayName("keys()")
    class KeysTests {

        @Test
        @DisplayName("should return matching keys for valid pattern")
        void shouldReturnMatchingKeys() {
            Set<String> mockKeys = Set.of("user:1", "user:2", "user:3");
            when(repository.scan("user:*")).thenReturn(mockKeys);

            Set<String> result = service.keys("user:*");

            assertThat(result).containsExactlyInAnyOrderElementsOf(mockKeys);
        }

        @Test
        @DisplayName("should reject bare wildcard pattern")
        void shouldReject_bareWildcard() {
            assertThatIllegalArgumentException()
                .isThrownBy(() -> service.keys("*"))
                .withMessageContaining("too broad");
        }
    }

    @Nested
    @DisplayName("bulkSet()")
    class BulkSetTests {

        @Test
        @DisplayName("should store all entries and return list")
        void shouldStoreAllEntries() {
            Map<String, String> entries = Map.of("k1", "v1", "k2", "v2");
            doNothing().when(repository).multiSet(entries, Duration.ofSeconds(60));

            List<CacheEntry> result = service.bulkSet(entries, Duration.ofSeconds(60));

            assertThat(result).hasSize(2);
            assertThat(result).extracting(CacheEntry::getTtlSeconds).containsOnly(60L);
        }

        @Test
        @DisplayName("should throw when entries map is empty")
        void shouldThrow_whenEntriesEmpty() {
            assertThatIllegalArgumentException()
                .isThrownBy(() -> service.bulkSet(Collections.emptyMap(), Duration.ZERO));
        }
    }

    @Nested
    @DisplayName("increment()")
    class IncrementTests {

        @Test
        @DisplayName("should increment and return new value")
        void shouldIncrement() {
            when(repository.increment("counter:views", 1L)).thenReturn(42L);

            long result = service.increment("counter:views", 1L);

            assertThat(result).isEqualTo(42L);
        }

        @Test
        @DisplayName("should support negative delta for decrement")
        void shouldSupportNegativeDelta() {
            when(repository.increment("counter:stock", -5L)).thenReturn(95L);

            long result = service.increment("counter:stock", -5L);

            assertThat(result).isEqualTo(95L);
        }
    }
}
