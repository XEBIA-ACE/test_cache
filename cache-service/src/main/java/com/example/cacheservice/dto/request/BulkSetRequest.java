package com.example.cacheservice.dto.request;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;

/**
 * Request body for bulk cache write operations.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Payload for writing multiple cache entries in one request")
public class BulkSetRequest {

    @NotEmpty(message = "Entries map must not be empty")
    @Size(max = 1000, message = "At most 1000 entries per bulk request")
    @Schema(
        description = "Map of key→value pairs to store",
        example = "{\"user:1\": \"Alice\", \"user:2\": \"Bob\"}"
    )
    private Map<String, String> entries;

    /**
     * TTL applied to every entry. 0 means no expiry.
     */
    @Min(value = 0, message = "ttlSeconds must be >= 0")
    @JsonProperty("ttl_seconds")
    @Schema(description = "TTL in seconds applied to all entries; 0 means no expiry",
            example = "3600", defaultValue = "0")
    private long ttlSeconds = 0;
}
