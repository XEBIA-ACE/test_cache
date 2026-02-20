package com.example.cacheservice.service;

import com.example.cacheservice.service.impl.LettuceCacheServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.util.Optional;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

/**
 * Unit tests for {@link LettuceCacheServiceImpl}.
 * Redis interactions are mocked so no live Redis instance is required.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("LettuceCacheServiceImpl – unit tests")
class LettuceCacheServiceImplTest {

    @Mock
    private RedisTemplate<String, Object> redisTemplate;

    @Mock
    private StringRedisTemplate stringRedisTemplate;

    @Mock
    private ValueOperations<String, String> valueOps;

    @InjectMocks
    private LettuceCacheServiceImpl cacheService;

    @BeforeEach
    void setUp() {
        given(stringRedisTemplate.opsForValue()).willReturn(valueOps);
    }

    // ─── GET ─────────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("get()")
    class GetTests {

        @Test
        @DisplayName("returns Optional with value when key exists")
        void get_existingKey_returnsValue() {
            given(valueOps.get("user:1")).willReturn("Alice");

            Optional<String> result = cacheService.get("user:1");

            assertThat(result).isPresent().contains("Alice");
        }

        @Test
        @DisplayName("returns Optional.empty() when key is absent")
        void get_absentKey_returnsEmpty() {
            given(valueOps.get("missing:key")).willReturn(null);

            Optional<String> result = cacheService.get("missing:key");

            assertThat(result).isEmpty();
        }
    }

    // ─── SET ─────────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("set()")
    class SetTests {

        @Test
        @DisplayName("calls SET with no expiry when ttlSeconds = 0")
        void set_noTtl_callsSimpleSet() {
            cacheService.set("key", "value");

            verify(valueOps).set("key", "value");
        }

        @Test
        @DisplayName("calls SET EX when ttlSeconds > 0")
        void set_withTtl_callsSetWithTtl() {
            cacheService.set("key", "value", 300L);

            verify(valueOps).set("key", "value", 300L, TimeUnit.SECONDS);
        }

        @Test
        @DisplayName("set with ttl = 0 falls through to plain SET")
        void set_zeroTtl_callsSimpleSet() {
            cacheService.set("key", "value", 0L);

            verify(valueOps).set("key", "value");
        }
    }

    // ─── SET IF ABSENT ────────────────────────────────────────────────────────

    @Nested
    @DisplayName("setIfAbsent()")
    class SetIfAbsentTests {

        @Test
        @DisplayName("returns true when key was written")
        void setIfAbsent_keyAbsent_returnsTrue() {
            given(valueOps.setIfAbsent("nx:key", "value", 60L, TimeUnit.SECONDS))
                    .willReturn(Boolean.TRUE);

            boolean result = cacheService.setIfAbsent("nx:key", "value", 60L);

            assertThat(result).isTrue();
        }

        @Test
        @DisplayName("returns false when key already exists")
        void setIfAbsent_keyExists_returnsFalse() {
            given(valueOps.setIfAbsent("nx:key", "value", 60L, TimeUnit.SECONDS))
                    .willReturn(Boolean.FALSE);

            boolean result = cacheService.setIfAbsent("nx:key", "value", 60L);

            assertThat(result).isFalse();
        }
    }

    // ─── DELETE ───────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("delete()")
    class DeleteTests {

        @Test
        @DisplayName("returns true when key existed and was deleted")
        void delete_existingKey_returnsTrue() {
            given(stringRedisTemplate.delete("del:key")).willReturn(Boolean.TRUE);

            boolean result = cacheService.delete("del:key");

            assertThat(result).isTrue();
        }

        @Test
        @DisplayName("returns false when key was absent")
        void delete_absentKey_returnsFalse() {
            given(stringRedisTemplate.delete("absent:key")).willReturn(Boolean.FALSE);

            boolean result = cacheService.delete("absent:key");

            assertThat(result).isFalse();
        }
    }

    // ─── EXISTS ───────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("exists()")
    class ExistsTests {

        @Test
        @DisplayName("returns true when key is present in Redis")
        void exists_keyPresent_returnsTrue() {
            given(stringRedisTemplate.hasKey("present")).willReturn(Boolean.TRUE);

            assertThat(cacheService.exists("present")).isTrue();
        }

        @Test
        @DisplayName("returns false when key is absent in Redis")
        void exists_keyAbsent_returnsFalse() {
            given(stringRedisTemplate.hasKey("absent")).willReturn(Boolean.FALSE);

            assertThat(cacheService.exists("absent")).isFalse();
        }
    }

    // ─── TTL ─────────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("ttl()")
    class TtlTests {

        @Test
        @DisplayName("returns the TTL in seconds from Redis")
        void ttl_keyWithExpiry_returnsTtlValue() {
            given(stringRedisTemplate.getExpire("ttl:key", TimeUnit.SECONDS)).willReturn(120L);

            assertThat(cacheService.ttl("ttl:key")).isEqualTo(120L);
        }

        @Test
        @DisplayName("returns -1 for keys with no expiry")
        void ttl_persistentKey_returnsMinusOne() {
            given(stringRedisTemplate.getExpire("persist:key", TimeUnit.SECONDS)).willReturn(-1L);

            assertThat(cacheService.ttl("persist:key")).isEqualTo(-1L);
        }

        @Test
        @DisplayName("returns -2 when key does not exist")
        void ttl_absentKey_returnsMinusTwo() {
            given(stringRedisTemplate.getExpire("gone:key", TimeUnit.SECONDS)).willReturn(-2L);

            assertThat(cacheService.ttl("gone:key")).isEqualTo(-2L);
        }
    }

    // ─── COUNTER ─────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("increment / decrement")
    class CounterTests {

        @Test
        @DisplayName("increment delegates to INCR and returns new value")
        void increment_callsIncrAndReturnsValue() {
            given(valueOps.increment("counter:1")).willReturn(5L);

            assertThat(cacheService.increment("counter:1")).isEqualTo(5L);
        }

        @Test
        @DisplayName("incrementBy delegates to INCRBY and returns new value")
        void incrementBy_callsIncrByAndReturnsValue() {
            given(valueOps.increment("counter:1", 10L)).willReturn(15L);

            assertThat(cacheService.incrementBy("counter:1", 10L)).isEqualTo(15L);
        }

        @Test
        @DisplayName("decrement delegates to DECR and returns new value")
        void decrement_callsDecrAndReturnsValue() {
            given(valueOps.decrement("counter:1")).willReturn(4L);

            assertThat(cacheService.decrement("counter:1")).isEqualTo(4L);
        }
    }
}
