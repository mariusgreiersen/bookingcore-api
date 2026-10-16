package com.bookingcore.resource.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

@Schema(description = "Request to create a reservable resource")
public record CreateResourceRequest(
        @Schema(description = "Display name", example = "Room A", maxLength = 100)
        @NotBlank @Size(max = 100)
        String name,

        @Schema(description = "Optional description", example = "Small huddle room with a wall-mounted screen", maxLength = 1000)
        @Size(max = 1000)
        String description,

        @Schema(description = "Optional location", example = "Floor 1", maxLength = 200)
        @Size(max = 200)
        String location,

        @Schema(description = "Maximum number of attendees", example = "4", minimum = "1", maximum = "10000")
        @NotNull @Positive @Max(10_000)
        Integer capacity) {
}
