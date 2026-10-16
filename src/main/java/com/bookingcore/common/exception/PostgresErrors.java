package com.bookingcore.common.exception;

import org.postgresql.util.PSQLException;
import org.postgresql.util.ServerErrorMessage;

/**
 * Classifies PostgreSQL errors by SQLSTATE and constraint name rather than by message text,
 * so that only genuine violations of one specific constraint are mapped to a domain error.
 */
public final class PostgresErrors {

    /** SQLSTATE for {@code exclusion_violation}. */
    public static final String EXCLUSION_VIOLATION = "23P01";

    private PostgresErrors() {
    }

    /**
     * Returns {@code true} only if the cause chain contains a PostgreSQL exclusion violation raised by the
     * given constraint. Every other failure (unique, foreign-key or check violations, a different exclusion
     * constraint, connection errors, ...) returns {@code false}.
     */
    public static boolean isExclusionViolation(Throwable throwable, String constraintName) {
        for (Throwable t = throwable; t != null; t = t.getCause()) {
            if (t instanceof PSQLException psql && EXCLUSION_VIOLATION.equals(psql.getSQLState())) {
                ServerErrorMessage serverError = psql.getServerErrorMessage();
                return serverError != null && constraintName.equals(serverError.getConstraint());
            }
            if (t.getCause() == t) {
                break;
            }
        }
        return false;
    }
}
