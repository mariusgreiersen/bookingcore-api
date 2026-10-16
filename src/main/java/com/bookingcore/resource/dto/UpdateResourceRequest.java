package com.bookingcore.resource.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

/** Partial update: fields that are omitted or {@code null} are left unchanged. */
@Schema(description = "Partial update of a resource. Omitted or null fields are left unchanged. "
        + "Set active=false to deactivate a resource; existing reservations are kept, but no new ones are accepted.")
public record UpdateResourceRequest(
        @Schema(description = "Display name", example = "Room A (renovated)", maxLength = 100)
        @Size(max = 100) @Pattern(regexp = "(?s).*\\S.*", message = "must not be blank")
        String name,

        @Schema(description = "Description", example = "Now with a whiteboard", maxLength = 1000)
        @Size(max = 1000)
        String description,

        @Schema(description = "Location", example = "Floor 2", maxLength = 200)
        @Size(max = 200)
        String location,

        @Schema(description = "Maximum number of attendees", example = "6", minimum = "1", maximum = "10000")
        @Positive @Max(10_000)
        Integer capacity,

        @Schema(description = "Whether the resource accepts new reservations", example = "false")
        Boolean active) {
}
