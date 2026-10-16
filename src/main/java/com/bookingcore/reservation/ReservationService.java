package com.bookingcore.reservation;

import java.time.Clock;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.bookingcore.common.exception.BusinessException;
import com.bookingcore.common.exception.ErrorCode;
import com.bookingcore.common.exception.NotFoundException;
import com.bookingcore.common.exception.PostgresErrors;
import com.bookingcore.reservation.dto.CreateReservationRequest;
import com.bookingcore.reservation.dto.ReservationResponse;
import com.bookingcore.reservation.dto.UpdateReservationRequest;
import com.bookingcore.resource.Resource;
import com.bookingcore.resource.ResourceRepository;

/**
 * Reservation use cases.
 *
 * <h2>Concurrency strategy</h2>
 * Overlap prevention is <em>not</em> implemented as "query for conflicts, then insert", which is racy: two
 * transactions can both observe a free slot and both insert. Instead, every write that creates or moves a
 * confirmed reservation is flushed to PostgreSQL inside the transaction, where the {@value #OVERLAP_CONSTRAINT}
 * exclusion constraint atomically rejects overlaps. The resulting SQLSTATE 23P01 for that specific constraint
 * is translated to {@link ErrorCode#RESERVATION_CONFLICT} (HTTP 409); any other database error propagates
 * unchanged. The transaction is then rolled back, leaving the database untouched.
 *
 * <p>Before writing, the resource row is locked so that writers for the same resource queue up rather than
 * deadlocking each other inside the constraint check (see {@link ResourceRepository#findByIdForBooking}).
 */
@Service
public class ReservationService {

    /** Name of the exclusion constraint defined in V3__add_reservation_overlap_constraint.sql. */
    static final String OVERLAP_CONSTRAINT = "reservations_no_overlap";

    private static final Logger log = LoggerFactory.getLogger(ReservationService.class);

    private final ReservationRepository reservationRepository;
    private final ResourceRepository resourceRepository;
    private final Clock clock;

    public ReservationService(ReservationRepository reservationRepository,
                              ResourceRepository resourceRepository,
                              Clock clock) {
        this.reservationRepository = reservationRepository;
        this.resourceRepository = resourceRepository;
        this.clock = clock;
    }

    @Transactional
    public ReservationResponse create(CreateReservationRequest request) {
        Instant now = clock.instant();
        TimeSlot slot = TimeSlot.of(request.startTime(), request.endTime());
        requireStartsInFuture(slot, now);
        lockActiveResource(request.resourceId());

        Reservation reservation = Reservation.confirmed(
                request.resourceId(),
                request.customerName().trim(),
                request.customerEmail().trim(),
                slot,
                now);
        Reservation saved = flushChecked(reservation);

        log.info("Reservation created: reservationId={} resourceId={} start={} end={}",
                saved.getId(), saved.getResourceId(), slot.start(), slot.end());
        return ReservationResponse.from(saved);
    }

    @Transactional(readOnly = true)
    public ReservationResponse get(UUID id) {
        return ReservationResponse.from(findOrThrow(id));
    }

    @Transactional(readOnly = true)
    public Page<ReservationResponse> list(UUID resourceId, ReservationStatus status,
                                          OffsetDateTime from, OffsetDateTime to, Pageable pageable) {
        if (from != null && to != null && !from.isBefore(to)) {
            throw new BusinessException(ErrorCode.INVALID_TIME_RANGE, "'from' must be before 'to'.");
        }
        return reservationRepository
                .findAll(ReservationSpecifications.withFilters(
                        resourceId, status,
                        from == null ? null : from.toInstant(),
                        to == null ? null : to.toInstant()), pageable)
                .map(ReservationResponse::from);
    }

    @Transactional
    public ReservationResponse update(UUID id, UpdateReservationRequest request) {
        Instant now = clock.instant();
        Reservation reservation = findOrThrow(id);
        if (reservation.isCancelled()) {
            throw new BusinessException(ErrorCode.INVALID_RESERVATION_STATE,
                    "Reservation " + id + " is cancelled and can no longer be modified.");
        }

        if (request.startTime() != null || request.endTime() != null) {
            TimeSlot current = reservation.slot();
            TimeSlot requested = new TimeSlot(
                    request.startTime() != null ? request.startTime().toInstant() : current.start(),
                    request.endTime() != null ? request.endTime().toInstant() : current.end());
            if (!requested.equals(current)) {
                requireStartsInFuture(requested, now);
                lockActiveResource(reservation.getResourceId());
                reservation.reschedule(requested, now);
            }
        }
        if (request.customerName() != null || request.customerEmail() != null) {
            reservation.updateCustomer(
                    request.customerName() == null ? null : request.customerName().trim(),
                    request.customerEmail() == null ? null : request.customerEmail().trim(),
                    now);
        }

        Reservation saved = flushChecked(reservation);
        log.info("Reservation updated: reservationId={} resourceId={} start={} end={}",
                saved.getId(), saved.getResourceId(), saved.getStartTime(), saved.getEndTime());
        return ReservationResponse.from(saved);
    }

    /**
     * Cancels a reservation. Idempotent: cancelling an already cancelled reservation succeeds and returns it
     * unchanged. Reservations that have already ended cannot be cancelled.
     */
    @Transactional
    public ReservationResponse cancel(UUID id) {
        Instant now = clock.instant();
        Reservation reservation = findOrThrow(id);
        if (reservation.isCancelled()) {
            log.debug("Reservation already cancelled: reservationId={}", id);
            return ReservationResponse.from(reservation);
        }
        if (reservation.hasEndedBy(now)) {
            throw new BusinessException(ErrorCode.INVALID_RESERVATION_STATE,
                    "Reservation " + id + " has already ended and cannot be cancelled.");
        }
        reservation.cancel(now);
        Reservation saved = reservationRepository.saveAndFlush(reservation);
        log.info("Reservation cancelled: reservationId={} resourceId={}", id, saved.getResourceId());
        return ReservationResponse.from(saved);
    }

    private Reservation findOrThrow(UUID id) {
        return reservationRepository.findById(id).orElseThrow(() -> NotFoundException.reservation(id));
    }

    /**
     * Locks the resource row (see {@link ResourceRepository#findByIdForBooking}) so that concurrent writers for
     * the same resource are applied one after another, then checks that it exists and is active.
     */
    private void lockActiveResource(UUID resourceId) {
        Resource resource = resourceRepository.findByIdForBooking(resourceId)
                .orElseThrow(() -> NotFoundException.resource(resourceId));
        if (!resource.isActive()) {
            throw new BusinessException(ErrorCode.RESOURCE_INACTIVE,
                    "Resource " + resourceId + " is inactive and does not accept new reservations.");
        }
    }

    private static void requireStartsInFuture(TimeSlot slot, Instant now) {
        if (!slot.startsAfter(now)) {
            throw new BusinessException(ErrorCode.INVALID_TIME_RANGE, "startTime must be in the future.");
        }
    }

    /**
     * Writes the reservation and flushes immediately so that constraint violations surface here, inside the
     * transaction, rather than at commit time outside the service.
     */
    private Reservation flushChecked(Reservation reservation) {
        try {
            return reservationRepository.saveAndFlush(reservation);
        } catch (DataAccessException ex) {
            if (PostgresErrors.isExclusionViolation(ex, OVERLAP_CONSTRAINT)) {
                log.info("Reservation rejected, overlapping interval: resourceId={} start={} end={}",
                        reservation.getResourceId(), reservation.getStartTime(), reservation.getEndTime());
                throw new BusinessException(ErrorCode.RESERVATION_CONFLICT,
                        "The selected resource is already booked during this time interval.", ex);
            }
            throw ex;
        }
    }
}
