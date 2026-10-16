package com.bookingcore.reservation;

import static com.bookingcore.support.PostgresExceptions.integrityViolation;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Pageable;

import com.bookingcore.common.exception.BusinessException;
import com.bookingcore.common.exception.ErrorCode;
import com.bookingcore.reservation.dto.CreateReservationRequest;
import com.bookingcore.reservation.dto.ReservationResponse;
import com.bookingcore.reservation.dto.UpdateReservationRequest;
import com.bookingcore.resource.Resource;
import com.bookingcore.resource.ResourceRepository;

@ExtendWith(MockitoExtension.class)
class ReservationServiceTest {

    private static final Instant NOW = Instant.parse("2027-03-01T08:00:00Z");
    private static final OffsetDateTime START = OffsetDateTime.parse("2027-03-15T10:00:00+01:00");
    private static final OffsetDateTime END = START.plusHours(1);
    private static final UUID RESOURCE_ID = UUID.fromString("11111111-1111-1111-1111-111111111111");

    @Mock
    private ReservationRepository reservationRepository;
    @Mock
    private ResourceRepository resourceRepository;

    private ReservationService service;

    @BeforeEach
    void setUp() {
        service = new ReservationService(reservationRepository, resourceRepository, Clock.fixed(NOW, ZoneOffset.UTC));
    }

    private static Resource resource(boolean active) {
        Resource resource = new Resource("Room A", null, "Floor 1", 4, NOW);
        resource.setActive(active);
        return resource;
    }

    private static CreateReservationRequest request(OffsetDateTime start, OffsetDateTime end) {
        return new CreateReservationRequest(RESOURCE_ID, "  Alex Jensen ", " alex@example.com ", start, end);
    }

    private static ErrorCode codeOf(Throwable ex) {
        return ((BusinessException) ex).errorCode();
    }

    @Nested
    @DisplayName("create")
    class Create {

        @Test
        void persistsConfirmedReservationAfterLockingTheResource() {
            when(resourceRepository.findByIdForBooking(RESOURCE_ID)).thenReturn(Optional.of(resource(true)));
            when(reservationRepository.saveAndFlush(any())).thenAnswer(inv -> inv.getArgument(0));

            ReservationResponse response = service.create(request(START, END));

            ArgumentCaptor<Reservation> saved = ArgumentCaptor.forClass(Reservation.class);
            verify(reservationRepository).saveAndFlush(saved.capture());
            assertThat(saved.getValue().getStatus()).isEqualTo(ReservationStatus.CONFIRMED);
            assertThat(saved.getValue().getCustomerName()).isEqualTo("Alex Jensen");
            assertThat(saved.getValue().getCustomerEmail()).isEqualTo("alex@example.com");
            assertThat(response.startTime()).isEqualTo(Instant.parse("2027-03-15T09:00:00Z"));
            assertThat(response.createdAt()).isEqualTo(NOW);
        }

        @Test
        void rejectsStartInThePastOrNow() {
            OffsetDateTime past = NOW.minusSeconds(1).atOffset(ZoneOffset.UTC);
            OffsetDateTime now = NOW.atOffset(ZoneOffset.UTC);

            assertThatThrownBy(() -> service.create(request(past, END)))
                    .satisfies(ex -> assertThat(codeOf(ex)).isEqualTo(ErrorCode.INVALID_TIME_RANGE));
            assertThatThrownBy(() -> service.create(request(now, END)))
                    .satisfies(ex -> assertThat(codeOf(ex)).isEqualTo(ErrorCode.INVALID_TIME_RANGE));
            verifyNoInteractions(reservationRepository, resourceRepository);
        }

        @Test
        void rejectsEndNotAfterStart() {
            assertThatThrownBy(() -> service.create(request(START, START)))
                    .satisfies(ex -> assertThat(codeOf(ex)).isEqualTo(ErrorCode.INVALID_TIME_RANGE));
            assertThatThrownBy(() -> service.create(request(END, START)))
                    .satisfies(ex -> assertThat(codeOf(ex)).isEqualTo(ErrorCode.INVALID_TIME_RANGE));
            verifyNoInteractions(reservationRepository);
        }

        @Test
        void rejectsUnknownResource() {
            when(resourceRepository.findByIdForBooking(RESOURCE_ID)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> service.create(request(START, END)))
                    .satisfies(ex -> assertThat(codeOf(ex)).isEqualTo(ErrorCode.RESOURCE_NOT_FOUND));
            verifyNoInteractions(reservationRepository);
        }

        @Test
        void rejectsInactiveResource() {
            when(resourceRepository.findByIdForBooking(RESOURCE_ID)).thenReturn(Optional.of(resource(false)));

            assertThatThrownBy(() -> service.create(request(START, END)))
                    .satisfies(ex -> assertThat(codeOf(ex)).isEqualTo(ErrorCode.RESOURCE_INACTIVE));
            verifyNoInteractions(reservationRepository);
        }

        @Test
        void translatesOverlapConstraintViolationToReservationConflict() {
            when(resourceRepository.findByIdForBooking(RESOURCE_ID)).thenReturn(Optional.of(resource(true)));
            when(reservationRepository.saveAndFlush(any()))
                    .thenThrow(integrityViolation("23P01", ReservationService.OVERLAP_CONSTRAINT));

            assertThatThrownBy(() -> service.create(request(START, END)))
                    .isInstanceOf(BusinessException.class)
                    .hasMessage("The selected resource is already booked during this time interval.")
                    .satisfies(ex -> assertThat(codeOf(ex)).isEqualTo(ErrorCode.RESERVATION_CONFLICT));
        }

        @Test
        void doesNotMislabelOtherIntegrityViolationsAsConflicts() {
            when(resourceRepository.findByIdForBooking(RESOURCE_ID)).thenReturn(Optional.of(resource(true)));
            DataIntegrityViolationException fkViolation = integrityViolation("23503", "fk_reservations_resource");
            when(reservationRepository.saveAndFlush(any())).thenThrow(fkViolation);

            assertThatThrownBy(() -> service.create(request(START, END))).isSameAs(fkViolation);
        }
    }

    @Nested
    @DisplayName("update")
    class Update {

        private Reservation existing;
        private UUID id;

        @BeforeEach
        void setUp() {
            id = UUID.randomUUID();
            existing = Reservation.confirmed(RESOURCE_ID, "Alex", "alex@example.com", TimeSlot.of(START, END), NOW);
            when(reservationRepository.findById(id)).thenReturn(Optional.of(existing));
        }

        @Test
        void reschedulingLocksResourceAndRevalidates() {
            when(resourceRepository.findByIdForBooking(RESOURCE_ID)).thenReturn(Optional.of(resource(true)));
            when(reservationRepository.saveAndFlush(existing)).thenReturn(existing);

            service.update(id, new UpdateReservationRequest(null, null, START.plusHours(2), END.plusHours(2)));

            assertThat(existing.slot()).isEqualTo(TimeSlot.of(START.plusHours(2), END.plusHours(2)));
            verify(resourceRepository).findByIdForBooking(RESOURCE_ID);
        }

        @Test
        void changingOnlyCustomerDetailsDoesNotLockResource() {
            when(reservationRepository.saveAndFlush(existing)).thenReturn(existing);

            service.update(id, new UpdateReservationRequest(" New Name ", null, null, null));

            assertThat(existing.getCustomerName()).isEqualTo("New Name");
            assertThat(existing.getCustomerEmail()).isEqualTo("alex@example.com");
            verifyNoInteractions(resourceRepository);
        }

        @Test
        void partialIntervalChangeIsValidatedAgainstTheRemainingBound() {
            assertThatThrownBy(() -> service.update(id, new UpdateReservationRequest(null, null, END.plusHours(1), null)))
                    .satisfies(ex -> assertThat(codeOf(ex)).isEqualTo(ErrorCode.INVALID_TIME_RANGE));
            verify(reservationRepository, never()).saveAndFlush(any());
        }

        @Test
        void rejectsMovingIntoThePast() {
            OffsetDateTime past = NOW.minusSeconds(3600).atOffset(ZoneOffset.UTC);

            assertThatThrownBy(() -> service.update(id, new UpdateReservationRequest(null, null, past, null)))
                    .satisfies(ex -> assertThat(codeOf(ex)).isEqualTo(ErrorCode.INVALID_TIME_RANGE));
        }

        @Test
        void translatesOverlapOnUpdateToConflict() {
            when(resourceRepository.findByIdForBooking(RESOURCE_ID)).thenReturn(Optional.of(resource(true)));
            when(reservationRepository.saveAndFlush(existing))
                    .thenThrow(integrityViolation("23P01", ReservationService.OVERLAP_CONSTRAINT));

            assertThatThrownBy(() -> service.update(id, new UpdateReservationRequest(null, null, null, END.plusHours(1))))
                    .satisfies(ex -> assertThat(codeOf(ex)).isEqualTo(ErrorCode.RESERVATION_CONFLICT));
        }

        @Test
        void rejectsUpdatingCancelledReservation() {
            existing.cancel(NOW);

            assertThatThrownBy(() -> service.update(id, new UpdateReservationRequest("X", null, null, null)))
                    .satisfies(ex -> assertThat(codeOf(ex)).isEqualTo(ErrorCode.INVALID_RESERVATION_STATE));
            verify(reservationRepository, never()).saveAndFlush(any());
        }
    }

    @Nested
    @DisplayName("cancel")
    class Cancel {

        @Test
        void cancelsConfirmedReservation() {
            UUID id = UUID.randomUUID();
            Reservation existing = Reservation.confirmed(RESOURCE_ID, "Alex", "a@example.com", TimeSlot.of(START, END), NOW);
            when(reservationRepository.findById(id)).thenReturn(Optional.of(existing));
            when(reservationRepository.saveAndFlush(existing)).thenReturn(existing);

            ReservationResponse response = service.cancel(id);

            assertThat(response.status()).isEqualTo(ReservationStatus.CANCELLED);
        }

        @Test
        void cancellingTwiceIsIdempotentAndWritesNothing() {
            UUID id = UUID.randomUUID();
            Reservation existing = Reservation.confirmed(RESOURCE_ID, "Alex", "a@example.com", TimeSlot.of(START, END), NOW);
            existing.cancel(NOW);
            when(reservationRepository.findById(id)).thenReturn(Optional.of(existing));

            ReservationResponse response = service.cancel(id);

            assertThat(response.status()).isEqualTo(ReservationStatus.CANCELLED);
            verify(reservationRepository, never()).saveAndFlush(any());
        }

        @Test
        void rejectsCancellingReservationThatHasEnded() {
            UUID id = UUID.randomUUID();
            TimeSlot past = new TimeSlot(NOW.minusSeconds(7200), NOW.minusSeconds(3600));
            Reservation ended = Reservation.confirmed(RESOURCE_ID, "Alex", "a@example.com", past, NOW.minusSeconds(9000));
            when(reservationRepository.findById(id)).thenReturn(Optional.of(ended));

            assertThatThrownBy(() -> service.cancel(id))
                    .satisfies(ex -> assertThat(codeOf(ex)).isEqualTo(ErrorCode.INVALID_RESERVATION_STATE));
        }

        @Test
        void unknownReservationIsNotFound() {
            UUID id = UUID.randomUUID();
            when(reservationRepository.findById(id)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> service.cancel(id))
                    .satisfies(ex -> assertThat(codeOf(ex)).isEqualTo(ErrorCode.RESERVATION_NOT_FOUND));
        }
    }

    @Test
    void listRejectsInvertedDateRange() {
        assertThatThrownBy(() -> service.list(null, null, END, START, Pageable.unpaged()))
                .satisfies(ex -> assertThat(codeOf(ex)).isEqualTo(ErrorCode.INVALID_TIME_RANGE));
        verifyNoInteractions(reservationRepository);
    }
}
