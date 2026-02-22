package com.example.cacheservice.api.controller;

import com.example.cacheservice.api.dto.CacheEntryRequest;
import com.example.cacheservice.business.model.CacheEntry;
import com.example.cacheservice.business.service.CacheService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Duration;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultHandlers.print;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Web layer unit tests for {@link CacheController}.
 *
 * <p>Uses {@code @WebMvcTest} to load only the web layer (controller + exception handler).
 * The {@link CacheService} is mocked with Mockito.
 */
@WebMvcTest(CacheController.class)
@DisplayName("CacheController")
class CacheControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private CacheService cacheService;

    private static final String BASE_URL = "/api/v1/cache";

    private CacheEntry sampleEntry(String key, String value) {
        return CacheEntry.of(key, value, 3600L);
    }

    @Nested
    @DisplayName("GET /{key}")
    class GetTests {

        @Test
        @DisplayName("should return 200 with entry when key exists")
        void shouldReturn200_whenKeyExists() throws Exception {
            when(cacheService.get("user:42")).thenReturn(Optional.of(sampleEntry("user:42", "{\"name\":\"Alice\"}")));

            mockMvc.perform(get(BASE_URL + "/user:42"))
                .andDo(print())
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.key").value("user:42"))
                .andExpect(jsonPath("$.value").value("{\"name\":\"Alice\"}"))
                .andExpect(jsonPath("$.ttlSeconds").value(3600));
        }

        @Test
        @DisplayName("should return 404 when key does not exist")
        void shouldReturn404_whenKeyAbsent() throws Exception {
            when(cacheService.get("user:99")).thenReturn(Optional.empty());

            mockMvc.perform(get(BASE_URL + "/user:99"))
                .andExpect(status().isNotFound());
        }
    }

    @Nested
    @DisplayName("PUT /{key}")
    class PutTests {

        @Test
        @DisplayName("should return 200 with stored entry")
        void shouldReturn200_onSuccessfulSet() throws Exception {
            CacheEntryRequest request = new CacheEntryRequest("hello world", 3600L);
            when(cacheService.set(eq("greeting"), eq("hello world"), eq(Duration.ofSeconds(3600))))
                .thenReturn(sampleEntry("greeting", "hello world"));

            mockMvc.perform(put(BASE_URL + "/greeting")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.key").value("greeting"))
                .andExpect(jsonPath("$.value").value("hello world"));
        }

        @Test
        @DisplayName("should return 400 when value is null")
        void shouldReturn400_whenValueIsNull() throws Exception {
            String requestJson = "{\"ttlSeconds\": 100}"; // no value field

            mockMvc.perform(put(BASE_URL + "/somekey")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(requestJson))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("VALIDATION_FAILED"));
        }

        @Test
        @DisplayName("should return 400 when TTL is negative")
        void shouldReturn400_whenTtlNegative() throws Exception {
            String requestJson = "{\"value\": \"test\", \"ttlSeconds\": -1}";

            mockMvc.perform(put(BASE_URL + "/somekey")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(requestJson))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("VALIDATION_FAILED"));
        }
    }

    @Nested
    @DisplayName("DELETE /{key}")
    class DeleteTests {

        @Test
        @DisplayName("should return 204 when key was deleted")
        void shouldReturn204_whenDeleted() throws Exception {
            when(cacheService.delete("user:1")).thenReturn(true);

            mockMvc.perform(delete(BASE_URL + "/user:1"))
                .andExpect(status().isNoContent());
        }

        @Test
        @DisplayName("should return 404 when key did not exist")
        void shouldReturn404_whenKeyAbsent() throws Exception {
            when(cacheService.delete("user:999")).thenReturn(false);

            mockMvc.perform(delete(BASE_URL + "/user:999"))
                .andExpect(status().isNotFound());
        }
    }

    @Nested
    @DisplayName("GET /{key}/ttl")
    class GetTtlTests {

        @Test
        @DisplayName("should return TTL for existing key")
        void shouldReturnTtl_forExistingKey() throws Exception {
            when(cacheService.getTtl("config:x")).thenReturn(900L);

            mockMvc.perform(get(BASE_URL + "/config:x/ttl"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.ttlSeconds").value(900));
        }

        @Test
        @DisplayName("should return 404 when key does not exist")
        void shouldReturn404_whenKeyAbsent() throws Exception {
            when(cacheService.getTtl("missing")).thenReturn(-2L);

            mockMvc.perform(get(BASE_URL + "/missing/ttl"))
                .andExpect(status().isNotFound());
        }
    }

    @Nested
    @DisplayName("GET ?pattern=")
    class ScanTests {

        @Test
        @DisplayName("should return matching keys")
        void shouldReturnMatchingKeys() throws Exception {
            when(cacheService.keys("user:*")).thenReturn(Set.of("user:1", "user:2"));

            mockMvc.perform(get(BASE_URL).param("pattern", "user:*"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.pattern").value("user:*"))
                .andExpect(jsonPath("$.count").value(2));
        }
    }

    @Nested
    @DisplayName("POST /bulk/get")
    class BulkGetTests {

        @Test
        @DisplayName("should return map of found entries")
        void shouldReturnFoundEntries() throws Exception {
            when(cacheService.bulkGet(anyList()))
                .thenReturn(Map.of(
                    "k1", sampleEntry("k1", "v1"),
                    "k2", sampleEntry("k2", "v2")
                ));

            String body = "{\"keys\": [\"k1\", \"k2\", \"k3\"]}";

            mockMvc.perform(post(BASE_URL + "/bulk/get")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.k1.value").value("v1"))
                .andExpect(jsonPath("$.k2.value").value("v2"));
        }

        @Test
        @DisplayName("should return 400 when keys list is empty")
        void shouldReturn400_whenKeysEmpty() throws Exception {
            mockMvc.perform(post(BASE_URL + "/bulk/get")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"keys\": []}"))
                .andExpect(status().isBadRequest());
        }
    }

    @Nested
    @DisplayName("GET /stats/size")
    class SizeTests {

        @Test
        @DisplayName("should return total key count")
        void shouldReturnTotalKeyCount() throws Exception {
            when(cacheService.size()).thenReturn(42L);

            mockMvc.perform(get(BASE_URL + "/stats/size"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalKeys").value(42));
        }
    }
}
