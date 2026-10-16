package com.bookingcore.common.exception;

import org.springframework.http.HttpStatus;

/**
 * Stable, machine-readable error codes returned in the {@code code} property of every Problem Details response.
 * Clients should branch on the code rather than on the human-readable title or detail.
 */
public enum ErrorCode {

    VALIDATION_FAILED(HttpStatus.BAD_REQUEST, "Validation failed"),
    MALFORMED_REQUEST(HttpStatus.BAD_REQUEST, "Malformed request"),
    INVALID_TIME_RANGE(HttpStatus.BAD_REQUEST, "Invalid time range"),
    RESOURCE_NOT_FOUND(HttpStatus.NOT_FOUND, "Resource not found"),
    RESERVATION_NOT_FOUND(HttpStatus.NOT_FOUND, "Reservation not found"),
    RESERVATION_CONFLICT(HttpStatus.CONFLICT, "Reservation conflict"),
    RESOURCE_INACTIVE(HttpStatus.CONFLICT, "Resource inactive"),
    INVALID_RESERVATION_STATE(HttpStatus.CONFLICT, "Invalid reservation state"),
    CONCURRENT_MODIFICATION(HttpStatus.CONFLICT, "Concurrent modification"),
    TRANSIENT_FAILURE(HttpStatus.SERVICE_UNAVAILABLE, "Temporarily unavailable"),
    INTERNAL_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, "Internal server error");

    private final HttpStatus status;
    private final String title;

    ErrorCode(HttpStatus status, String title) {
        this.status = status;
        this.title = title;
    }

    public HttpStatus status() {
        return status;
    }

    public String title() {
        return title;
    }
}
