package com.example.cacheservice.controller;

import com.example.cacheservice.dto.request.CacheSetRequest;
import com.example.cacheservice.exception.CacheKeyNotFoundException;
import com.example.cacheservice.exception.GlobalExceptionHandler;
import com.example.cacheservice.service.CacheService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Optional;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willDoNothing;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Web-layer unit tests for {@link CacheController}.
 * The service is mocked; no Redis connection is required.
 */
@WebMvcTest(CacheController.class)
@Import(GlobalExceptionHandler.class)
@DisplayName("CacheController – web layer tests")
class CacheControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private CacheService cacheService;

    private static final String BASE = "/api/v1/cache";

    // ─── GET ─────────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("GET /{key}")
    class GetKey {

        @Test
        @DisplayName("200 with value when key exists")
        void get_existingKey_returns200() throws Exception {
            given(cacheService.get("user:1")).willReturn(Optional.of("Alice"));
            given(cacheService.ttl("user:1")).willReturn(3600L);

            mockMvc.perform(get(BASE + "/user:1"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.success").value(true))
                    .andExpect(jsonPath("$.data.key").value("user:1"))
                    .andExpect(jsonPath("$.data.value").value("Alice"))
                    .andExpect(jsonPath("$.data.ttl_seconds").value(3600));
        }

        @Test
        @DisplayName("404 when key is absent")
        void get_absentKey_returns404() throws Exception {
            given(cacheService.get("missing")).willReturn(Optional.empty());

            mockMvc.perform(get(BASE + "/missing"))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.success").value(false))
                    .andExpect(jsonPath("$.error_code").value("KEY_NOT_FOUND"));
        }
    }

    // ─── PUT ─────────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("PUT /{key}")
    class PutKey {

        @Test
        @DisplayName("200 with stored entry when request is valid")
        void put_validRequest_returns200() throws Exception {
            CacheSetRequest req = new CacheSetRequest("Bob", 600L);
            willDoNothing().given(cacheService).set(eq("user:2"), eq("Bob"), eq(600L));

            mockMvc.perform(put(BASE + "/user:2")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(req)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.success").value(true))
                    .andExpect(jsonPath("$.data.key").value("user:2"))
                    .andExpect(jsonPath("$.data.value").value("Bob"));

            verify(cacheService).set("user:2", "Bob", 600L);
        }

        @Test
        @DisplayName("400 when value is blank")
        void put_blankValue_returns400() throws Exception {
            CacheSetRequest req = new CacheSetRequest("  ", 0L);

            mockMvc.perform(put(BASE + "/key")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(req)))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.error_code").value("VALIDATION_ERROR"));
        }
    }

    // ─── DELETE ───────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("DELETE /{key}")
    class DeleteKey {

        @Test
        @DisplayName("200 when key existed and was deleted")
        void delete_existingKey_returns200() throws Exception {
            given(cacheService.delete("del:key")).willReturn(true);

            mockMvc.perform(delete(BASE + "/del:key"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.success").value(true));
        }

        @Test
        @DisplayName("404 when key was absent")
        void delete_absentKey_returns404() throws Exception {
            given(cacheService.delete("gone")).willReturn(false);

            mockMvc.perform(delete(BASE + "/gone"))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.error_code").value("KEY_NOT_FOUND"));
        }
    }

    // ─── EXISTS ───────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("GET /{key}/exists")
    class ExistsKey {

        @Test
        @DisplayName("returns true in data payload when key exists")
        void exists_presentKey_returnsTrue() throws Exception {
            given(cacheService.exists("present")).willReturn(true);

            mockMvc.perform(get(BASE + "/present/exists"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data").value(true));
        }

        @Test
        @DisplayName("returns false in data payload when key is absent")
        void exists_absentKey_returnsFalse() throws Exception {
            given(cacheService.exists("absent")).willReturn(false);

            mockMvc.perform(get(BASE + "/absent/exists"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data").value(false));
        }
    }

    // ─── INCREMENT ────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("POST /{key}/increment")
    class IncrementKey {

        @Test
        @DisplayName("200 with new counter value")
        void increment_returnsNewValue() throws Exception {
            given(cacheService.increment("hits")).willReturn(42L);

            mockMvc.perform(post(BASE + "/hits/increment"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data").value(42));
        }
    }
}
