package com.bookingcore.availability;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.bookingcore.availability.dto.AvailabilityResponse;
import com.bookingcore.availability.dto.AvailabilityResponse.UnavailabilityReason;
import com.bookingcore.common.exception.BusinessException;
import com.bookingcore.common.exception.ErrorCode;
import com.bookingcore.reservation.Reservation;
import com.bookingcore.reservation.ReservationRepository;
import com.bookingcore.reservation.ReservationStatus;
import com.bookingcore.reservation.TimeSlot;
import com.bookingcore.resource.Resource;
import com.bookingcore.resource.ResourceRepository;

@ExtendWith(MockitoExtension.class)
class AvailabilityServiceTest {

    private static final Instant NOW = Instant.parse("2027-03-01T08:00:00Z");
    private static final UUID ID = UUID.randomUUID();
    private static final OffsetDateTime START = OffsetDateTime.parse("2027-03-15T10:00:00Z");
    private static final OffsetDateTime END = START.plusHours(1);

    @Mock
    private ResourceRepository resourceRepository;
    @Mock
    private ReservationRepository reservationRepository;

    private AvailabilityService service;

    @BeforeEach
    void setUp() {
        service = new AvailabilityService(resourceRepository, reservationRepository, Clock.fixed(NOW, ZoneOffset.UTC));
    }

    private void givenResource(boolean active, List<Reservation> overlapping) {
        Resource resource = new Resource("Room", null, null, 4, NOW);
        resource.setActive(active);
        when(resourceRepository.findById(ID)).thenReturn(Optional.of(resource));
        when(reservationRepository.findOverlapping(eq(ID), eq(ReservationStatus.CONFIRMED), any(), any()))
                .thenReturn(overlapping);
    }

    @Test
    void availableWhenActiveFreeAndInTheFuture() {
        givenResource(true, List.of());

        AvailabilityResponse response = service.check(ID, START, END);

        assertThat(response.available()).isTrue();
        assertThat(response.reason()).isNull();
        assertThat(response.conflicts()).isEmpty();
    }

    @Test
    void unavailableWithConflictsListed() {
        Reservation other = Reservation.confirmed(ID, "X", "x@example.com",
                TimeSlot.of(START.plusMinutes(30), END.plusMinutes(30)), NOW);
        givenResource(true, List.of(other));

        AvailabilityResponse response = service.check(ID, START, END);

        assertThat(response.available()).isFalse();
        assertThat(response.reason()).isEqualTo(UnavailabilityReason.OVERLAPPING_RESERVATION);
        assertThat(response.conflicts()).singleElement()
                .satisfies(c -> assertThat(c.startTime()).isEqualTo(START.plusMinutes(30).toInstant()));
    }

    @Test
    void inactiveResourceIsUnavailable() {
        givenResource(false, List.of());

        assertThat(service.check(ID, START, END).reason()).isEqualTo(UnavailabilityReason.RESOURCE_INACTIVE);
    }

    @Test
    void pastIntervalIsUnavailable() {
        givenResource(true, List.of());
        OffsetDateTime past = NOW.minusSeconds(3600).atOffset(ZoneOffset.UTC);

        assertThat(service.check(ID, past, past.plusMinutes(30)).reason()).isEqualTo(UnavailabilityReason.START_IN_PAST);
    }

    @Test
    void rejectsInvalidAndOversizedWindows() {
        assertThatThrownBy(() -> service.check(ID, END, START))
                .satisfies(ex -> assertThat(((BusinessException) ex).errorCode()).isEqualTo(ErrorCode.INVALID_TIME_RANGE));
        assertThatThrownBy(() -> service.check(ID, START, START.plusDays(32)))
                .satisfies(ex -> assertThat(((BusinessException) ex).errorCode()).isEqualTo(ErrorCode.INVALID_TIME_RANGE));
        verifyNoInteractions(resourceRepository, reservationRepository);
    }
}
