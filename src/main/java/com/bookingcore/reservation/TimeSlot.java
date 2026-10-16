package com.bookingcore.reservation;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.temporal.ChronoUnit;
import java.util.Objects;

import com.bookingcore.common.exception.BusinessException;
import com.bookingcore.common.exception.ErrorCode;

/**
 * A half-open time interval {@code [start, end)}. Two slots overlap iff {@code a.start < b.end && b.start < a.end},
 * which is exactly the semantics of PostgreSQL's {@code tstzrange(start, end, '[)') && ...} used by the
 * exclusion constraint, so adjacent slots (one ends when the other starts) do not overlap.
 *
 * <p>Instants are truncated to microseconds, the precision of PostgreSQL {@code timestamptz}, so the value the
 * API returns is exactly the value that was stored and checked.
 */
public record TimeSlot(Instant start, Instant end) {

    public TimeSlot {
        Objects.requireNonNull(start, "start");
        Objects.requireNonNull(end, "end");
        start = start.truncatedTo(ChronoUnit.MICROS);
        end = end.truncatedTo(ChronoUnit.MICROS);
        if (!start.isBefore(end)) {
            throw new BusinessException(ErrorCode.INVALID_TIME_RANGE, "startTime must be before endTime.");
        }
    }

    public static TimeSlot of(OffsetDateTime start, OffsetDateTime end) {
        return new TimeSlot(start.toInstant(), end.toInstant());
    }

    public boolean overlaps(TimeSlot other) {
        return start.isBefore(other.end) && other.start.isBefore(end);
    }

    public boolean startsAfter(Instant instant) {
        return start.isAfter(instant);
    }
}
