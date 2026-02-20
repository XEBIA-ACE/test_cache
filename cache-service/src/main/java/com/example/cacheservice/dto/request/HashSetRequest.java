package com.example.cacheservice.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Request body for setting a single hash field.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Payload for writing a single hash field")
public class HashSetRequest {

    @NotBlank(message = "Value must not be blank")
    @Schema(description = "Value to store in the hash field", example = "active",
            requiredMode = Schema.RequiredMode.REQUIRED)
    private String value;
}
