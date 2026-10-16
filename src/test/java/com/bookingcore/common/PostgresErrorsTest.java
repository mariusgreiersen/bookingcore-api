package com.bookingcore.common;

import static com.bookingcore.support.PostgresExceptions.integrityViolation;
import static com.bookingcore.support.PostgresExceptions.psqlException;
import static org.assertj.core.api.Assertions.assertThat;

import java.sql.SQLException;

import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;

import com.bookingcore.common.exception.PostgresErrors;

class PostgresErrorsTest {

    private static final String CONSTRAINT = "reservations_no_overlap";

    @Test
    void recognisesExclusionViolationOfTheNamedConstraintAnywhereInTheCauseChain() {
        assertThat(PostgresErrors.isExclusionViolation(psqlException("23P01", CONSTRAINT), CONSTRAINT)).isTrue();
        assertThat(PostgresErrors.isExclusionViolation(integrityViolation("23P01", CONSTRAINT), CONSTRAINT)).isTrue();
    }

    @Test
    void doesNotClassifyOtherConstraintViolationsAsOverlap() {
        assertThat(PostgresErrors.isExclusionViolation(integrityViolation("23503", "fk_reservations_resource"), CONSTRAINT))
                .as("foreign key violation").isFalse();
        assertThat(PostgresErrors.isExclusionViolation(integrityViolation("23514", "chk_reservations_time_range"), CONSTRAINT))
                .as("check violation").isFalse();
        assertThat(PostgresErrors.isExclusionViolation(integrityViolation("23505", "pk_reservations"), CONSTRAINT))
                .as("unique violation").isFalse();
        assertThat(PostgresErrors.isExclusionViolation(integrityViolation("23P01", "some_other_exclusion"), CONSTRAINT))
                .as("different exclusion constraint").isFalse();
        assertThat(PostgresErrors.isExclusionViolation(integrityViolation("23P01", null), CONSTRAINT))
                .as("exclusion violation without constraint name").isFalse();
    }

    @Test
    void doesNotClassifyNonPostgresOrUnrelatedFailures() {
        assertThat(PostgresErrors.isExclusionViolation(new SQLException("boom", "23P01"), CONSTRAINT))
                .as("plain SQLException cannot be attributed to a constraint").isFalse();
        assertThat(PostgresErrors.isExclusionViolation(new DataIntegrityViolationException("no cause"), CONSTRAINT)).isFalse();
        assertThat(PostgresErrors.isExclusionViolation(new IllegalStateException("x"), CONSTRAINT)).isFalse();
        assertThat(PostgresErrors.isExclusionViolation(null, CONSTRAINT)).isFalse();
    }
}
