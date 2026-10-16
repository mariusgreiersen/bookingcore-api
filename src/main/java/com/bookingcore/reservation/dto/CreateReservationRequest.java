package com.bookingcore.reservation.dto;

import java.time.OffsetDateTime;
import java.util.UUID;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

@Schema(description = "Request to reserve a resource for the half-open interval [startTime, endTime)")
public record CreateReservationRequest(
        @Schema(example = "11111111-1111-1111-1111-111111111111")
        @NotNull
        UUID resourceId,

        @Schema(example = "Alex Jensen", maxLength = 100)
        @NotBlank @Size(max = 100)
        String customerName,

        @Schema(example = "alex@example.com", maxLength = 254)
        @NotBlank @Email @Size(max = 254)
        String customerEmail,

        @Schema(description = "ISO 8601 with explicit offset; must be in the future", example = "2027-03-15T10:00:00+01:00")
        @NotNull
        OffsetDateTime startTime,

        @Schema(description = "ISO 8601 with explicit offset; must be after startTime (exclusive end)", example = "2027-03-15T11:00:00+01:00")
        @NotNull
        OffsetDateTime endTime) {
}
