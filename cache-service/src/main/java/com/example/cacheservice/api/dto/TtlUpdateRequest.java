package com.example.cacheservice.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

/**
 * Request body for updating the TTL of an existing cache key.
 */
@Schema(description = "Request to update the TTL of a cache entry")
public record TtlUpdateRequest(

    @NotNull(message = "TTL seconds must be provided")
    @Min(value = 0, message = "TTL must be non-negative (0 = remove expiry)")
    @Schema(
        description = "New TTL in seconds. Use 0 to make the key persistent (remove expiry).",
        example = "7200"
    )
    Long ttlSeconds
) {}
