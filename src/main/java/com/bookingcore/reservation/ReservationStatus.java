package com.bookingcore.reservation;

/**
 * Reservation lifecycle. The only transition is {@code CONFIRMED -> CANCELLED}; cancelled reservations are kept
 * for history and are ignored by the overlap constraint.
 */
public enum ReservationStatus {
    CONFIRMED,
    CANCELLED
}
