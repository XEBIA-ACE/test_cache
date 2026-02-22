package com.example.cacheservice.api.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;
import java.util.List;
import java.util.Map;

/**
 * Standard API error response body.
 *
 * <p>Follows RFC 7807 (Problem Details for HTTP APIs) conventions.
 */
@Schema(description = "Standard error response")
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ErrorResponse(

    @Schema(description = "HTTP status code", example = "404")
    int status,

    @Schema(description = "Short error code", example = "NOT_FOUND")
    String error,

    @Schema(description = "Human-readable error message", example = "Cache key 'user:42' not found")
    String message,

    @Schema(description = "Request path that caused the error", example = "/api/v1/cache/user:42")
    String path,

    @Schema(description = "Timestamp of the error")
    Instant timestamp,

    @Schema(description = "Field-level validation errors (present only for 400 responses)")
    List<FieldError> fieldErrors
) {
    /**
     * Factory for simple error responses without field errors.
     */
    public static ErrorResponse of(int status, String error, String message, String path) {
        return new ErrorResponse(status, error, message, path, Instant.now(), null);
    }

    /**
     * Factory for validation error responses with field-level details.
     */
    public static ErrorResponse ofValidation(int status, String path, List<FieldError> fieldErrors) {
        return new ErrorResponse(status, "VALIDATION_FAILED",
            "Request validation failed. See 'fieldErrors' for details.",
            path, Instant.now(), fieldErrors);
    }

    /**
     * Represents a single field validation failure.
     */
    @Schema(description = "A single field-level validation error")
    public record FieldError(
        @Schema(description = "The field that failed validation", example = "ttlSeconds")
        String field,

        @Schema(description = "The rejected value", example = "-1")
        Object rejectedValue,

        @Schema(description = "The validation error message", example = "TTL must be non-negative")
        String message
    ) {}
}
