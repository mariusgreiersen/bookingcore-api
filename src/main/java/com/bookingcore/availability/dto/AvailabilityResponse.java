package com.bookingcore.availability.dto;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Informational availability of a resource for an interval. A positive answer is not a "
        + "reservation and does not guarantee that a subsequent booking succeeds.")
public record AvailabilityResponse(
        @Schema(example = "11111111-1111-1111-1111-111111111111") UUID resourceId,
        @Schema(example = "2027-03-15T09:00:00Z") Instant startTime,
        @Schema(example = "2027-03-15T10:00:00Z") Instant endTime,
        @Schema(description = "True if a booking for this interval would currently pass all checks", example = "false")
        boolean available,
        @Schema(description = "Why the interval is unavailable; null when available",
                example = "OVERLAPPING_RESERVATION", nullable = true)
        UnavailabilityReason reason,
        @Schema(description = "Confirmed reservations overlapping the interval (times only, no customer data)")
        List<BusyInterval> conflicts) {

    public enum UnavailabilityReason {
        RESOURCE_INACTIVE,
        START_IN_PAST,
        OVERLAPPING_RESERVATION
    }

    @Schema(name = "BusyInterval")
    public record BusyInterval(
            @Schema(example = "2027-03-15T09:30:00Z") Instant startTime,
            @Schema(example = "2027-03-15T10:30:00Z") Instant endTime) {
    }
}
