package com.bookingcore.resource.dto;

import java.time.Instant;
import java.util.UUID;

import com.bookingcore.resource.Resource;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "A reservable resource")
public record ResourceResponse(
        @Schema(example = "11111111-1111-1111-1111-111111111111") UUID id,
        @Schema(example = "Room A") String name,
        @Schema(example = "Small huddle room with a wall-mounted screen") String description,
        @Schema(example = "Floor 1") String location,
        @Schema(description = "Maximum number of attendees", example = "4") int capacity,
        @Schema(description = "Whether the resource accepts new reservations", example = "true") boolean active,
        @Schema(example = "2027-03-01T08:00:00Z") Instant createdAt,
        @Schema(example = "2027-03-01T08:00:00Z") Instant updatedAt) {

    public static ResourceResponse from(Resource resource) {
        return new ResourceResponse(
                resource.getId(),
                resource.getName(),
                resource.getDescription(),
                resource.getLocation(),
                resource.getCapacity(),
                resource.isActive(),
                resource.getCreatedAt(),
                resource.getUpdatedAt());
    }
}
