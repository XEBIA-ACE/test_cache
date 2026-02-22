package com.example.cacheservice.api.exception;

import com.example.cacheservice.api.dto.ErrorResponse;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataAccessException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

import java.util.List;

/**
 * Centralized exception handling for all REST controllers.
 *
 * <p>Maps exceptions to RFC 7807-style problem detail responses with consistent
 * HTTP status codes and structured error bodies.
 */
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    /**
     * Handles Jakarta Bean Validation failures (e.g., @NotNull, @Size violations).
     */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> handleValidation(
        MethodArgumentNotValidException ex,
        HttpServletRequest request
    ) {
        List<ErrorResponse.FieldError> fieldErrors = ex.getBindingResult()
            .getFieldErrors()
            .stream()
            .map(fe -> new ErrorResponse.FieldError(
                fe.getField(),
                fe.getRejectedValue(),
                fe.getDefaultMessage()
            ))
            .toList();

        log.warn("Validation failed for request {}: {} field errors", request.getRequestURI(), fieldErrors.size());

        return ResponseEntity
            .badRequest()
            .body(ErrorResponse.ofValidation(400, request.getRequestURI(), fieldErrors));
    }

    /**
     * Handles business rule violations (e.g., invalid key format, value too large).
     */
    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ErrorResponse> handleIllegalArgument(
        IllegalArgumentException ex,
        HttpServletRequest request
    ) {
        log.warn("Invalid input for request {}: {}", request.getRequestURI(), ex.getMessage());

        return ResponseEntity
            .badRequest()
            .body(ErrorResponse.of(400, "INVALID_INPUT", ex.getMessage(), request.getRequestURI()));
    }

    /**
     * Handles malformed or unreadable JSON request bodies.
     */
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ErrorResponse> handleUnreadableBody(
        HttpMessageNotReadableException ex,
        HttpServletRequest request
    ) {
        log.warn("Unreadable request body for {}: {}", request.getRequestURI(), ex.getMessage());

        return ResponseEntity
            .badRequest()
            .body(ErrorResponse.of(400, "MALFORMED_REQUEST", "Request body is malformed or missing", request.getRequestURI()));
    }

    /**
     * Handles path variable or request parameter type mismatches.
     */
    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ErrorResponse> handleTypeMismatch(
        MethodArgumentTypeMismatchException ex,
        HttpServletRequest request
    ) {
        String message = String.format("Parameter '%s' has invalid value: '%s'", ex.getName(), ex.getValue());

        return ResponseEntity
            .badRequest()
            .body(ErrorResponse.of(400, "TYPE_MISMATCH", message, request.getRequestURI()));
    }

    /**
     * Handles Redis connectivity and command failures.
     */
    @ExceptionHandler(DataAccessException.class)
    public ResponseEntity<ErrorResponse> handleRedisError(
        DataAccessException ex,
        HttpServletRequest request
    ) {
        log.error("Redis error for request {}: {}", request.getRequestURI(), ex.getMessage(), ex);

        return ResponseEntity
            .status(HttpStatus.SERVICE_UNAVAILABLE)
            .body(ErrorResponse.of(503, "CACHE_UNAVAILABLE",
                "Cache service is temporarily unavailable. Please retry.",
                request.getRequestURI()));
    }

    /**
     * Handles distributed lock errors.
     */
    @ExceptionHandler(RuntimeException.class)
    public ResponseEntity<ErrorResponse> handleRuntimeException(
        RuntimeException ex,
        HttpServletRequest request
    ) {
        // Check if it's a lock-related error
        if (ex.getMessage() != null && ex.getMessage().contains("lock")) {
            log.error("Lock operation failed for request {}: {}", request.getRequestURI(), ex.getMessage(), ex);
            return ResponseEntity
                .status(HttpStatus.SERVICE_UNAVAILABLE)
                .body(ErrorResponse.of(503, "LOCK_ERROR",
                    "Distributed lock operation failed: " + ex.getMessage(),
                    request.getRequestURI()));
        }

        log.error("Unexpected error for request {}: {}", request.getRequestURI(), ex.getMessage(), ex);

        return ResponseEntity
            .status(HttpStatus.INTERNAL_SERVER_ERROR)
            .body(ErrorResponse.of(500, "INTERNAL_ERROR",
                "An unexpected error occurred",
                request.getRequestURI()));
    }

    /**
     * Catch-all for any unhandled exceptions.
     */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleGeneric(
        Exception ex,
        HttpServletRequest request
    ) {
        log.error("Unhandled exception for request {}: {}", request.getRequestURI(), ex.getMessage(), ex);

        return ResponseEntity
            .status(HttpStatus.INTERNAL_SERVER_ERROR)
            .body(ErrorResponse.of(500, "INTERNAL_ERROR",
                "An unexpected error occurred",
                request.getRequestURI()));
    }
}
