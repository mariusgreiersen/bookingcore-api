package com.bookingcore.support;

import org.postgresql.util.PSQLException;
import org.postgresql.util.ServerErrorMessage;
import org.springframework.dao.DataIntegrityViolationException;

/** Builds realistic PostgreSQL driver exceptions for unit tests, without a database. */
public final class PostgresExceptions {

    private PostgresExceptions() {
    }

    /** A {@link PSQLException} as the driver produces it from a server error with SQLSTATE and constraint name. */
    public static PSQLException psqlException(String sqlState, String constraint) {
        String fields = "SERROR\0C" + sqlState + "\0Msimulated error\0"
                + (constraint == null ? "" : "n" + constraint + "\0");
        return new PSQLException(new ServerErrorMessage(fields));
    }

    /** The exception Spring Data surfaces when a flush hits the given constraint. */
    public static DataIntegrityViolationException integrityViolation(String sqlState, String constraint) {
        return new DataIntegrityViolationException("could not execute statement",
                new RuntimeException("wrapped by persistence provider", psqlException(sqlState, constraint)));
    }
}
