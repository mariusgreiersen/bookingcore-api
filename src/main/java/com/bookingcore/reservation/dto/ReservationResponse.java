package com.bookingcore.reservation.dto;

import java.time.Instant;
import java.util.UUID;

import com.bookingcore.reservation.Reservation;
import com.bookingcore.reservation.ReservationStatus;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "A reservation. Timestamps are returned in UTC.")
public record ReservationResponse(
        @Schema(example = "7b0c2f9e-3d1a-4f7e-9a51-2c8d0e6b4a10") UUID id,
        @Schema(example = "11111111-1111-1111-1111-111111111111") UUID resourceId,
        @Schema(example = "Alex Jensen") String customerName,
        @Schema(example = "alex@example.com") String customerEmail,
        @Schema(example = "2027-03-15T09:00:00Z") Instant startTime,
        @Schema(example = "2027-03-15T10:00:00Z") Instant endTime,
        @Schema(example = "CONFIRMED") ReservationStatus status,
        @Schema(example = "2027-03-01T08:00:00Z") Instant createdAt,
        @Schema(example = "2027-03-01T08:00:00Z") Instant updatedAt) {

    public static ReservationResponse from(Reservation reservation) {
        return new ReservationResponse(
                reservation.getId(),
                reservation.getResourceId(),
                reservation.getCustomerName(),
                reservation.getCustomerEmail(),
                reservation.getStartTime(),
                reservation.getEndTime(),
                reservation.getStatus(),
                reservation.getCreatedAt(),
                reservation.getUpdatedAt());
    }
}
