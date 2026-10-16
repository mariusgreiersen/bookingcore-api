package com.bookingcore.config;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;

/** Reusable OpenAPI documentation for the Problem Details error responses. */
public final class ApiErrorResponses {

    public static final String PROBLEM_JSON = "application/problem+json";

    private ApiErrorResponses() {
    }

    @Target(ElementType.METHOD)
    @Retention(RetentionPolicy.RUNTIME)
    @ApiResponse(responseCode = "400", description = "Invalid input (validation failure, malformed JSON, bad UUID or timestamp)",
            content = @Content(mediaType = PROBLEM_JSON, schema = @Schema(implementation = ApiError.class),
                    examples = @ExampleObject(value = """
                            {
                              "type": "about:blank",
                              "title": "Validation failed",
                              "status": 400,
                              "detail": "One or more fields are invalid.",
                              "instance": "/api/v1/reservations",
                              "code": "VALIDATION_FAILED",
                              "requestId": "3f1c9a52-6a8e-4c43-a0a5-0f4f0f4a8b11",
                              "errors": [ { "field": "customerEmail", "message": "must be a well-formed email address" } ]
                            }""")))
    public @interface BadRequest {
    }

    @Target(ElementType.METHOD)
    @Retention(RetentionPolicy.RUNTIME)
    @ApiResponse(responseCode = "404", description = "The referenced resource or reservation does not exist",
            content = @Content(mediaType = PROBLEM_JSON, schema = @Schema(implementation = ApiError.class),
                    examples = @ExampleObject(value = """
                            {
                              "type": "about:blank",
                              "title": "Resource not found",
                              "status": 404,
                              "detail": "No resource exists with id 550e8400-e29b-41d4-a716-446655440000.",
                              "instance": "/api/v1/reservations",
                              "code": "RESOURCE_NOT_FOUND",
                              "requestId": "3f1c9a52-6a8e-4c43-a0a5-0f4f0f4a8b11"
                            }""")))
    public @interface NotFound {
    }

    @Target(ElementType.METHOD)
    @Retention(RetentionPolicy.RUNTIME)
    @ApiResponse(responseCode = "409", description = """
            Conflict. `code` is one of: RESERVATION_CONFLICT (interval overlaps a confirmed reservation), \
            RESOURCE_INACTIVE, INVALID_RESERVATION_STATE, CONCURRENT_MODIFICATION.""",
            content = @Content(mediaType = PROBLEM_JSON, schema = @Schema(implementation = ApiError.class),
                    examples = @ExampleObject(value = """
                            {
                              "type": "about:blank",
                              "title": "Reservation conflict",
                              "status": 409,
                              "detail": "The selected resource is already booked during this time interval.",
                              "instance": "/api/v1/reservations",
                              "code": "RESERVATION_CONFLICT",
                              "requestId": "3f1c9a52-6a8e-4c43-a0a5-0f4f0f4a8b11"
                            }""")))
    public @interface Conflict {
    }
}
