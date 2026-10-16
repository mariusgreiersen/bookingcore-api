package com.bookingcore.reservation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.util.UUID;

import org.junit.jupiter.api.Test;

class ReservationTest {

    private static final Instant NOW = Instant.parse("2027-03-01T08:00:00Z");
    private static final TimeSlot SLOT = new TimeSlot(
            Instant.parse("2027-03-15T10:00:00Z"), Instant.parse("2027-03-15T11:00:00Z"));

    private final Reservation reservation =
            Reservation.confirmed(UUID.randomUUID(), "Alex", "alex@example.com", SLOT, NOW);

    @Test
    void newReservationIsConfirmed() {
        assertThat(reservation.getStatus()).isEqualTo(ReservationStatus.CONFIRMED);
        assertThat(reservation.slot()).isEqualTo(SLOT);
        assertThat(reservation.getCreatedAt()).isEqualTo(NOW);
    }

    @Test
    void cancelIsIdempotent() {
        Instant first = NOW.plusSeconds(60);
        Instant second = NOW.plusSeconds(120);

        assertThat(reservation.cancel(first)).isTrue();
        assertThat(reservation.cancel(second)).isFalse();

        assertThat(reservation.getStatus()).isEqualTo(ReservationStatus.CANCELLED);
        assertThat(reservation.getUpdatedAt()).as("second cancel changes nothing").isEqualTo(first);
    }

    @Test
    void cancelledReservationCannotBeModified() {
        reservation.cancel(NOW);

        assertThatThrownBy(() -> reservation.reschedule(SLOT, NOW)).isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> reservation.updateCustomer("x", null, NOW)).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void hasEndedIsExclusiveOfEnd() {
        assertThat(reservation.hasEndedBy(SLOT.end().minusNanos(1000))).isFalse();
        assertThat(reservation.hasEndedBy(SLOT.end())).isTrue();
    }
}
