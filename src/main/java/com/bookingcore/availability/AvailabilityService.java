package com.bookingcore.availability;

import java.time.Clock;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.bookingcore.availability.dto.AvailabilityResponse;
import com.bookingcore.availability.dto.AvailabilityResponse.BusyInterval;
import com.bookingcore.availability.dto.AvailabilityResponse.UnavailabilityReason;
import com.bookingcore.common.exception.BusinessException;
import com.bookingcore.common.exception.ErrorCode;
import com.bookingcore.common.exception.NotFoundException;
import com.bookingcore.reservation.ReservationRepository;
import com.bookingcore.reservation.ReservationStatus;
import com.bookingcore.reservation.TimeSlot;
import com.bookingcore.resource.Resource;
import com.bookingcore.resource.ResourceRepository;

/**
 * Read-only availability lookup. The answer reflects committed data at query time and may be stale by the time
 * the client books; the booking itself is protected by the database exclusion constraint, not by this check.
 */
@Service
public class AvailabilityService {

    /** Bounds the size of the conflict list returned for a single query. */
    static final Duration MAX_WINDOW = Duration.ofDays(31);

    private final ResourceRepository resourceRepository;
    private final ReservationRepository reservationRepository;
    private final Clock clock;

    public AvailabilityService(ResourceRepository resourceRepository,
                               ReservationRepository reservationRepository,
                               Clock clock) {
        this.resourceRepository = resourceRepository;
        this.reservationRepository = reservationRepository;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public AvailabilityResponse check(UUID resourceId, OffsetDateTime startTime, OffsetDateTime endTime) {
        TimeSlot slot = TimeSlot.of(startTime, endTime);
        if (Duration.between(slot.start(), slot.end()).compareTo(MAX_WINDOW) > 0) {
            throw new BusinessException(ErrorCode.INVALID_TIME_RANGE,
                    "The availability window must not exceed " + MAX_WINDOW.toDays() + " days.");
        }
        Resource resource = resourceRepository.findById(resourceId)
                .orElseThrow(() -> NotFoundException.resource(resourceId));

        List<BusyInterval> conflicts = reservationRepository
                .findOverlapping(resourceId, ReservationStatus.CONFIRMED, slot.start(), slot.end())
                .stream()
                .map(r -> new BusyInterval(r.getStartTime(), r.getEndTime()))
                .toList();

        UnavailabilityReason reason;
        if (!resource.isActive()) {
            reason = UnavailabilityReason.RESOURCE_INACTIVE;
        } else if (!conflicts.isEmpty()) {
            reason = UnavailabilityReason.OVERLAPPING_RESERVATION;
        } else if (!slot.startsAfter(clock.instant())) {
            reason = UnavailabilityReason.START_IN_PAST;
        } else {
            reason = null;
        }
        return new AvailabilityResponse(resourceId, slot.start(), slot.end(), reason == null, reason, conflicts);
    }
}
