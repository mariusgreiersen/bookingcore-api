package com.bookingcore.reservation.dto;

import java.time.OffsetDateTime;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/** Partial update: fields that are omitted or {@code null} are left unchanged. */
@Schema(description = "Partial update of a confirmed reservation. Omitted or null fields are left unchanged. "
        + "A changed interval is re-validated and re-checked for conflicts. The resource cannot be changed; "
        + "cancel and create a new reservation instead.")
public record UpdateReservationRequest(
        @Schema(example = "Alex Jensen", maxLength = 100)
        @Size(max = 100) @Pattern(regexp = "(?s).*\\S.*", message = "must not be blank")
        String customerName,

        @Schema(example = "alex.jensen@example.com", maxLength = 254)
        @Email @Size(max = 254) @Pattern(regexp = "(?s).*\\S.*", message = "must not be blank")
        String customerEmail,

        @Schema(description = "New start, ISO 8601 with explicit offset", example = "2027-03-15T11:00:00+01:00")
        OffsetDateTime startTime,

        @Schema(description = "New end, ISO 8601 with explicit offset", example = "2027-03-15T12:00:00+01:00")
        OffsetDateTime endTime) {
}
