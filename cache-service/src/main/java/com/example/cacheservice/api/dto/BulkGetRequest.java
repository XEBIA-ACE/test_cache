package com.example.cacheservice.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;

import java.util.List;

/**
 * Request body for bulk get operations.
 */
@Schema(description = "Request to retrieve multiple cache entries by key")
public record BulkGetRequest(

    @NotEmpty(message = "Keys list must not be empty")
    @Size(max = 500, message = "Cannot retrieve more than 500 keys in a single request")
    @Schema(
        description = "List of cache keys to retrieve",
        example = "[\"user:1\", \"user:2\", \"user:3\"]"
    )
    List<String> keys
) {}
