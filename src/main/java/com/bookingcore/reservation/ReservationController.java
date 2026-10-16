package com.bookingcore.reservation;

import java.net.URI;
import java.time.OffsetDateTime;
import java.util.UUID;

import org.springdoc.core.annotations.ParameterObject;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import com.bookingcore.common.response.PageResponse;
import com.bookingcore.config.ApiErrorResponses;
import com.bookingcore.reservation.dto.CreateReservationRequest;
import com.bookingcore.reservation.dto.ReservationResponse;
import com.bookingcore.reservation.dto.UpdateReservationRequest;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/v1/reservations")
@Tag(name = "Reservations", description = "Create, modify and cancel reservations")
public class ReservationController {

    private final ReservationService reservationService;

    public ReservationController(ReservationService reservationService) {
        this.reservationService = reservationService;
    }

    @PostMapping
    @Operation(summary = "Create a reservation",
            description = "Reserves a resource for the half-open interval [startTime, endTime). Overlapping a "
                    + "confirmed reservation of the same resource returns 409 RESERVATION_CONFLICT; this is "
                    + "enforced by the database and therefore safe under concurrent requests. Back-to-back "
                    + "reservations (end == next start) are allowed.")
    @ApiResponse(responseCode = "201", description = "Reservation created; Location header points to it")
    @ApiErrorResponses.BadRequest
    @ApiErrorResponses.NotFound
    @ApiErrorResponses.Conflict
    public ResponseEntity<ReservationResponse> create(@Valid @RequestBody CreateReservationRequest request) {
        ReservationResponse created = reservationService.create(request);
        URI location = ServletUriComponentsBuilder.fromCurrentRequest()
                .path("/{id}").buildAndExpand(created.id()).toUri();
        return ResponseEntity.created(location).body(created);
    }

    @GetMapping
    @Operation(summary = "List reservations",
            description = "Paginated list. Filters are optional and combined with AND; from/to select reservations "
                    + "overlapping [from, to). Sortable by startTime, endTime, createdAt, status. Page size is "
                    + "capped at 100.")
    @ApiResponse(responseCode = "200", description = "A page of reservations")
    @ApiErrorResponses.BadRequest
    public PageResponse<ReservationResponse> list(
            @Parameter(description = "Only reservations of this resource")
            @RequestParam(required = false) UUID resourceId,
            @Parameter(description = "Only reservations with this status")
            @RequestParam(required = false) ReservationStatus status,
            @Parameter(description = "Only reservations ending after this instant (ISO 8601)", example = "2027-03-15T00:00:00Z")
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) OffsetDateTime from,
            @Parameter(description = "Only reservations starting before this instant (ISO 8601)", example = "2027-03-16T00:00:00Z")
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) OffsetDateTime to,
            @ParameterObject @PageableDefault(size = 20, sort = "startTime", direction = Sort.Direction.ASC) Pageable pageable) {
        return PageResponse.of(reservationService.list(resourceId, status, from, to, pageable));
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get a reservation")
    @ApiResponse(responseCode = "200", description = "The reservation")
    @ApiErrorResponses.BadRequest
    @ApiErrorResponses.NotFound
    public ReservationResponse get(@PathVariable UUID id) {
        return reservationService.get(id);
    }

    @PatchMapping("/{id}")
    @Operation(summary = "Update a reservation",
            description = "Partial update of customer details and/or interval. A changed interval is re-validated "
                    + "and re-checked against other confirmed reservations (409 on overlap); on failure the "
                    + "reservation is left unchanged. Cancelled reservations cannot be updated (409).")
    @ApiResponse(responseCode = "200", description = "The updated reservation")
    @ApiErrorResponses.BadRequest
    @ApiErrorResponses.NotFound
    @ApiErrorResponses.Conflict
    public ReservationResponse update(@PathVariable UUID id, @Valid @RequestBody UpdateReservationRequest request) {
        return reservationService.update(id, request);
    }

    @PostMapping("/{id}/cancel")
    @Operation(summary = "Cancel a reservation",
            description = "Marks the reservation CANCELLED (it is kept for history) and frees its interval. "
                    + "Idempotent: cancelling an already cancelled reservation returns 200 with the unchanged "
                    + "reservation. A reservation that has already ended cannot be cancelled (409).")
    @ApiResponse(responseCode = "200", description = "The cancelled reservation")
    @ApiErrorResponses.BadRequest
    @ApiErrorResponses.NotFound
    @ApiErrorResponses.Conflict
    public ReservationResponse cancel(@PathVariable UUID id) {
        return reservationService.cancel(id);
    }
}
