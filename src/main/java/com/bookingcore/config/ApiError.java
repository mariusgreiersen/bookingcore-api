package com.bookingcore.config;

import java.util.List;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Documentation-only schema describing the RFC 9457 Problem Details body produced by
 * {@link com.bookingcore.common.exception.GlobalExceptionHandler}. Not used at runtime.
 */
@Schema(name = "ProblemDetail", description = "RFC 9457 Problem Details error response")
public record ApiError(
        @Schema(example = "about:blank") String type,
        @Schema(example = "Reservation conflict") String title,
        @Schema(example = "409") int status,
        @Schema(example = "The selected resource is already booked during this time interval.") String detail,
        @Schema(example = "/api/v1/reservations") String instance,
        @Schema(description = "Stable machine-readable error code", example = "RESERVATION_CONFLICT") String code,
        @Schema(description = "Correlation id, also returned in the X-Request-Id header") String requestId,
        @Schema(description = "Field-level validation errors (400 VALIDATION_FAILED only)") List<FieldViolation> errors) {

    @Schema(name = "FieldViolation")
    public record FieldViolation(
            @Schema(example = "customerEmail") String field,
            @Schema(example = "must be a well-formed email address") String message) {
    }
}
