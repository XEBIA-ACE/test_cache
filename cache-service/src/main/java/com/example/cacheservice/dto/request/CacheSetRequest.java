package com.example.cacheservice.dto.request;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Request body for single-key cache write operations.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Payload for writing a single cache entry")
public class CacheSetRequest {

    @NotBlank(message = "Value must not be blank")
    @Size(max = 10_485_760, message = "Value must not exceed 10 MB")
    @Schema(description = "The value to store", example = "John Doe", requiredMode = Schema.RequiredMode.REQUIRED)
    private String value;

    /**
     * Time-to-live in seconds. 0 or negative means no expiry (persist forever).
     */
    @Min(value = 0, message = "ttlSeconds must be >= 0")
    @JsonProperty("ttl_seconds")
    @Schema(description = "TTL in seconds; 0 means no expiry", example = "3600", defaultValue = "0")
    private long ttlSeconds = 0;
}
