package com.bookingcore.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;

import com.bookingcore.common.exception.PostgresErrors;

/**
 * Verifies the schema itself, bypassing the application layer entirely with plain SQL. This demonstrates that
 * the double-booking guarantee lives in PostgreSQL and holds for any client of the database.
 */
@DisplayName("Database constraints (plain SQL, no application code)")
class DatabaseConstraintIT extends AbstractIntegrationTest {

    private static final String INSERT = """
            INSERT INTO reservations (id, resource_id, customer_name, customer_email, start_time, end_time,
                                      status, created_at, updated_at)
            VALUES (?, ?, 'Direct SQL', 'sql@example.com', ?::timestamptz, ?::timestamptz, ?, now(), now())
            """;

    @Test
    void flywayAppliedAllMigrations() {
        List<String> versions = jdbcTemplate.queryForList(
                "SELECT version FROM flyway_schema_history WHERE success AND version IS NOT NULL ORDER BY installed_rank",
                String.class);
        assertThat(versions).containsExactly("1", "2", "3", "4");
    }

    @Test
    void exclusionConstraintRejectsOverlapWithSqlState23P01() throws Exception {
        UUID roomId = createResource();
        insert(roomId, BASE.withHour(10), BASE.withHour(11), "CONFIRMED");

        assertThatThrownBy(() -> insert(roomId, BASE.withHour(10).plusMinutes(59), BASE.withHour(12), "CONFIRMED"))
                .isInstanceOf(DataIntegrityViolationException.class)
                .satisfies(ex -> assertThat(PostgresErrors.isExclusionViolation(ex, "reservations_no_overlap")).isTrue());
    }

    @Test
    void halfOpenIntervalsAllowAdjacentRows() throws Exception {
        UUID roomId = createResource();
        insert(roomId, BASE.withHour(10), BASE.withHour(11), "CONFIRMED");
        insert(roomId, BASE.withHour(11), BASE.withHour(12), "CONFIRMED");

        assertThat(confirmedOverlapping(roomId, BASE.withHour(10), BASE.withHour(12))).isEqualTo(2);
    }

    @Test
    void cancelledRowsAreIgnoredByTheConstraint() throws Exception {
        UUID roomId = createResource();
        insert(roomId, BASE.withHour(10), BASE.withHour(11), "CANCELLED");
        insert(roomId, BASE.withHour(10), BASE.withHour(11), "CANCELLED");
        insert(roomId, BASE.withHour(10), BASE.withHour(11), "CONFIRMED");

        assertThat(confirmedOverlapping(roomId, BASE.withHour(10), BASE.withHour(11))).isEqualTo(1);
    }

    @Test
    void reactivatingCancelledRowIntoOverlapIsRejected() throws Exception {
        UUID roomId = createResource();
        UUID cancelled = insert(roomId, BASE.withHour(10), BASE.withHour(11), "CANCELLED");
        insert(roomId, BASE.withHour(10), BASE.withHour(11), "CONFIRMED");

        assertThatThrownBy(() -> jdbcTemplate.update(
                "UPDATE reservations SET status = 'CONFIRMED' WHERE id = ?", cancelled))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void checkConstraintsRejectInvalidRows() throws Exception {
        UUID roomId = createResource();

        // Empty interval (start == end) and unknown status are rejected by CHECK constraints...
        assertThatThrownBy(() -> insert(roomId, BASE.withHour(10), BASE.withHour(10), "CONFIRMED"))
                .isInstanceOf(DataIntegrityViolationException.class)
                .satisfies(ex -> assertThat(PostgresErrors.isExclusionViolation(ex, "reservations_no_overlap")).isFalse());
        assertThatThrownBy(() -> insert(roomId, BASE.withHour(10), BASE.withHour(11), "PENDING"))
                .isInstanceOf(DataIntegrityViolationException.class);
        // ...and a reservation for a non-existent resource by the foreign key - none of which is an overlap.
        assertThatThrownBy(() -> insert(UUID.randomUUID(), BASE.withHour(10), BASE.withHour(11), "CONFIRMED"))
                .isInstanceOf(DataIntegrityViolationException.class)
                .satisfies(ex -> assertThat(PostgresErrors.isExclusionViolation(ex, "reservations_no_overlap")).isFalse());
        assertThatThrownBy(() -> jdbcTemplate.update(
                "INSERT INTO resources (id, name, capacity, created_at, updated_at) VALUES (?, 'X', 0, now(), now())",
                UUID.randomUUID()))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    private UUID insert(UUID resourceId, OffsetDateTime start, OffsetDateTime end, String status) {
        UUID id = UUID.randomUUID();
        jdbcTemplate.update(INSERT, id, resourceId, start, end, status);
        return id;
    }
}
