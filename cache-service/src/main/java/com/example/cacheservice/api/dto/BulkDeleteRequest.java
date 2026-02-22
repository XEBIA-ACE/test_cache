package com.example.cacheservice.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;

import java.util.List;

/**
 * Request body for bulk delete operations.
 */
@Schema(description = "Request to delete multiple cache entries by key")
public record BulkDeleteRequest(

    @NotEmpty(message = "Keys list must not be empty")
    @Size(max = 500, message = "Cannot delete more than 500 keys in a single request")
    @Schema(
        description = "List of cache keys to delete",
        example = "[\"session:abc\", \"session:def\"]"
    )
    List<String> keys
) {}
