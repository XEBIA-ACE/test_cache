package com.example.cacheservice.dto.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Represents a single cache entry returned from the API.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
@Schema(description = "A single cached key/value entry with metadata")
public class CacheEntryResponse {

    @Schema(description = "Cache key", example = "user:42")
    private String key;

    @Schema(description = "Cached value (null if key not found)", example = "John Doe")
    private String value;

    @JsonProperty("ttl_seconds")
    @Schema(description = "Remaining TTL in seconds; -1 = no expiry; -2 = key absent", example = "3540")
    private Long ttlSeconds;

    @JsonProperty("exists")
    @Schema(description = "Whether the key exists in the cache", example = "true")
    private boolean exists;
}
