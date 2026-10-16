package com.bookingcore.availability;

import java.time.OffsetDateTime;
import java.util.UUID;

import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.bookingcore.availability.dto.AvailabilityResponse;
import com.bookingcore.config.ApiErrorResponses;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;

@RestController
@Tag(name = "Availability", description = "Informational availability checks")
public class AvailabilityController {

    private final AvailabilityService availabilityService;

    public AvailabilityController(AvailabilityService availabilityService) {
        this.availabilityService = availabilityService;
    }

    @GetMapping("/api/v1/resources/{id}/availability")
    @Operation(summary = "Check availability of a resource",
            description = "Reports whether [startTime, endTime) is free and lists overlapping confirmed reservations. "
                    + "Informational only: it does not hold the slot, and a positive answer does not guarantee that "
                    + "a subsequent booking succeeds. Maximum window: 31 days. Encode '+' in offsets as %2B.")
    @ApiResponse(responseCode = "200", description = "Availability result")
    @ApiErrorResponses.BadRequest
    @ApiErrorResponses.NotFound
    public AvailabilityResponse check(
            @PathVariable UUID id,
            @Parameter(description = "Interval start (ISO 8601)", example = "2027-03-15T09:00:00Z", required = true)
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) OffsetDateTime startTime,
            @Parameter(description = "Interval end, exclusive (ISO 8601)", example = "2027-03-15T10:00:00Z", required = true)
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) OffsetDateTime endTime) {
        return availabilityService.check(id, startTime, endTime);
    }
}
