package com.example.cacheservice.dto.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

/**
 * Uniform envelope wrapping every API response.
 *
 * <p>Fields with {@code null} values are omitted from the serialised JSON to
 * keep payloads lean ({@link JsonInclude.Include#NON_NULL}).
 *
 * @param <T> the type of the {@code data} payload
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
@Schema(description = "Uniform API response envelope")
public class ApiResponse<T> {

    @Schema(description = "Whether the operation succeeded", example = "true")
    private boolean success;

    @Schema(description = "Human-readable message", example = "Key stored successfully")
    private String message;

    @Schema(description = "Response payload; null for void operations")
    private T data;

    @JsonProperty("error_code")
    @Schema(description = "Machine-readable error code; present only on failure", example = "KEY_NOT_FOUND")
    private String errorCode;

    @Schema(description = "ISO-8601 UTC timestamp of the response")
    @Builder.Default
    private Instant timestamp = Instant.now();

    // ─── Factory helpers ─────────────────────────────────────────────────────

    public static <T> ApiResponse<T> ok(T data, String message) {
        return ApiResponse.<T>builder()
                .success(true)
                .message(message)
                .data(data)
                .timestamp(Instant.now())
                .build();
    }

    public static <T> ApiResponse<T> ok(String message) {
        return ApiResponse.<T>builder()
                .success(true)
                .message(message)
                .timestamp(Instant.now())
                .build();
    }

    public static <T> ApiResponse<T> error(String message, String errorCode) {
        return ApiResponse.<T>builder()
                .success(false)
                .message(message)
                .errorCode(errorCode)
                .timestamp(Instant.now())
                .build();
    }
}
