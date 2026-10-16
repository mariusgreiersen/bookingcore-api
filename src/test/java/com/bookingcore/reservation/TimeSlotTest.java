package com.bookingcore.reservation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.time.OffsetDateTime;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import com.bookingcore.common.exception.BusinessException;
import com.bookingcore.common.exception.ErrorCode;

class TimeSlotTest {

    private static TimeSlot slot(String start, String end) {
        return new TimeSlot(Instant.parse(start), Instant.parse(end));
    }

    @Test
    void rejectsEmptyAndInvertedIntervals() {
        assertThatThrownBy(() -> slot("2027-03-15T10:00:00Z", "2027-03-15T10:00:00Z"))
                .isInstanceOf(BusinessException.class)
                .extracting(ex -> ((BusinessException) ex).errorCode()).isEqualTo(ErrorCode.INVALID_TIME_RANGE);
        assertThatThrownBy(() -> slot("2027-03-15T11:00:00Z", "2027-03-15T10:00:00Z"))
                .isInstanceOf(BusinessException.class);
    }

    @ParameterizedTest(name = "[10:00, 11:00) vs [{0}, {1}) overlaps = {2}")
    @CsvSource({
            "10:00:00, 11:00:00, true",   // identical
            "10:30:00, 11:30:00, true",   // partial, later
            "09:30:00, 10:30:00, true",   // partial, earlier
            "10:15:00, 10:45:00, true",   // contained
            "09:00:00, 12:00:00, true",   // containing
            "10:59:59, 12:00:00, true",   // one second overlap at the end
            "09:00:00, 10:00:01, true",   // one second overlap at the start
            "11:00:00, 12:00:00, false",  // back-to-back after
            "09:00:00, 10:00:00, false",  // back-to-back before
            "12:00:00, 13:00:00, false"   // disjoint
    })
    void usesHalfOpenIntervalSemantics(String start, String end, boolean expected) {
        TimeSlot booked = slot("2027-03-15T10:00:00Z", "2027-03-15T11:00:00Z");
        TimeSlot candidate = slot("2027-03-15T" + start + "Z", "2027-03-15T" + end + "Z");

        assertThat(booked.overlaps(candidate)).isEqualTo(expected);
        assertThat(candidate.overlaps(booked)).isEqualTo(expected);
    }

    @Test
    void convertsOffsetsToTheSameInstant() {
        TimeSlot fromOffsets = TimeSlot.of(
                OffsetDateTime.parse("2027-03-15T10:00:00+01:00"),
                OffsetDateTime.parse("2027-03-15T11:00:00+01:00"));

        assertThat(fromOffsets).isEqualTo(slot("2027-03-15T09:00:00Z", "2027-03-15T10:00:00Z"));
    }

    @Test
    void truncatesToDatabasePrecision() {
        TimeSlot slot = slot("2027-03-15T10:00:00.123456789Z", "2027-03-15T11:00:00Z");

        assertThat(slot.start()).isEqualTo(Instant.parse("2027-03-15T10:00:00.123456Z"));
    }
}
