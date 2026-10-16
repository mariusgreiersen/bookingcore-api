package com.bookingcore.reservation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.containsString;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.dao.CannotAcquireLockException;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.MediaType;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import com.bookingcore.common.exception.BusinessException;
import com.bookingcore.common.exception.ErrorCode;
import com.bookingcore.common.exception.NotFoundException;
import com.bookingcore.reservation.dto.CreateReservationRequest;
import com.bookingcore.reservation.dto.ReservationResponse;

@WebMvcTest(ReservationController.class)
class ReservationControllerTest {

    private static final UUID RESOURCE_ID = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final UUID RESERVATION_ID = UUID.fromString("7b0c2f9e-3d1a-4f7e-9a51-2c8d0e6b4a10");
    private static final String VALID_BODY = """
            {
              "resourceId": "11111111-1111-1111-1111-111111111111",
              "customerName": "Alex Jensen",
              "customerEmail": "alex@example.com",
              "startTime": "2027-03-15T10:00:00+01:00",
              "endTime": "2027-03-15T11:00:00+01:00"
            }
            """;

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private ReservationService reservationService;

    private static ReservationResponse response(ReservationStatus status) {
        Instant created = Instant.parse("2027-03-01T08:00:00Z");
        return new ReservationResponse(RESERVATION_ID, RESOURCE_ID, "Alex Jensen", "alex@example.com",
                Instant.parse("2027-03-15T09:00:00Z"), Instant.parse("2027-03-15T10:00:00Z"),
                status, created, created);
    }

    @Test
    void createReturns201WithLocationAndUtcTimestamps() throws Exception {
        when(reservationService.create(any())).thenReturn(response(ReservationStatus.CONFIRMED));

        mockMvc.perform(post("/api/v1/reservations").contentType(MediaType.APPLICATION_JSON).content(VALID_BODY))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", "http://localhost/api/v1/reservations/" + RESERVATION_ID))
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.id").value(RESERVATION_ID.toString()))
                .andExpect(jsonPath("$.status").value("CONFIRMED"))
                .andExpect(jsonPath("$.startTime").value("2027-03-15T09:00:00Z"));

        ArgumentCaptor<CreateReservationRequest> captor = ArgumentCaptor.forClass(CreateReservationRequest.class);
        verify(reservationService).create(captor.capture());
        assertThat(captor.getValue().startTime().toInstant()).isEqualTo(Instant.parse("2027-03-15T09:00:00Z"));
    }

    @Test
    void missingFieldsReturn400WithFieldErrors() throws Exception {
        mockMvc.perform(post("/api/v1/reservations").contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.title").value("Validation failed"))
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.errors[*].field", containsInAnyOrder(
                        "resourceId", "customerName", "customerEmail", "startTime", "endTime")));
        verifyNoInteractions(reservationService);
    }

    @Test
    void invalidEmailReturns400() throws Exception {
        String body = VALID_BODY.replace("alex@example.com", "not-an-email");

        mockMvc.perform(post("/api/v1/reservations").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("customerEmail"));
    }

    @Test
    void timestampWithoutOffsetReturns400() throws Exception {
        String body = VALID_BODY.replace("2027-03-15T10:00:00+01:00", "2027-03-15T10:00:00");

        mockMvc.perform(post("/api/v1/reservations").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("MALFORMED_REQUEST"));
    }

    @Test
    void malformedJsonReturns400WithoutParserDetails() throws Exception {
        mockMvc.perform(post("/api/v1/reservations").contentType(MediaType.APPLICATION_JSON).content("{\"resourceId\": "))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("MALFORMED_REQUEST"))
                .andExpect(content().string(not(containsString("jackson"))));
    }

    @Test
    void bookingConflictReturns409ProblemDetails() throws Exception {
        when(reservationService.create(any())).thenThrow(new BusinessException(ErrorCode.RESERVATION_CONFLICT,
                "The selected resource is already booked during this time interval."));

        mockMvc.perform(post("/api/v1/reservations").contentType(MediaType.APPLICATION_JSON).content(VALID_BODY)
                        .header("X-Request-Id", "req-42"))
                .andExpect(status().isConflict())
                .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(header().string("X-Request-Id", "req-42"))
                .andExpect(jsonPath("$.type").value("about:blank"))
                .andExpect(jsonPath("$.title").value("Reservation conflict"))
                .andExpect(jsonPath("$.status").value(409))
                .andExpect(jsonPath("$.detail").value("The selected resource is already booked during this time interval."))
                .andExpect(jsonPath("$.instance").value("/api/v1/reservations"))
                .andExpect(jsonPath("$.code").value("RESERVATION_CONFLICT"))
                .andExpect(jsonPath("$.requestId").value("req-42"));
    }

    @Test
    void unknownResourceReturns404() throws Exception {
        when(reservationService.create(any())).thenThrow(NotFoundException.resource(RESOURCE_ID));

        mockMvc.perform(post("/api/v1/reservations").contentType(MediaType.APPLICATION_JSON).content(VALID_BODY))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("RESOURCE_NOT_FOUND"));
    }

    @Test
    void invalidUuidInPathReturns400() throws Exception {
        mockMvc.perform(get("/api/v1/reservations/not-a-uuid"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("MALFORMED_REQUEST"))
                .andExpect(jsonPath("$.detail").value("Parameter 'id' has an invalid format."));
    }

    @Test
    void optimisticLockFailureReturns409() throws Exception {
        when(reservationService.cancel(RESERVATION_ID))
                .thenThrow(new ObjectOptimisticLockingFailureException(Reservation.class, RESERVATION_ID));

        mockMvc.perform(post("/api/v1/reservations/{id}/cancel", RESERVATION_ID))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("CONCURRENT_MODIFICATION"));
    }

    @Test
    void transientLockFailureReturns503WithRetryAfter() throws Exception {
        when(reservationService.create(any())).thenThrow(new CannotAcquireLockException("deadlock detected"));

        mockMvc.perform(post("/api/v1/reservations").contentType(MediaType.APPLICATION_JSON).content(VALID_BODY))
                .andExpect(status().isServiceUnavailable())
                .andExpect(header().string("Retry-After", "1"))
                .andExpect(jsonPath("$.code").value("TRANSIENT_FAILURE"))
                .andExpect(content().string(not(containsString("deadlock"))));
    }

    @Test
    void unexpectedErrorReturns500WithoutInternalDetails() throws Exception {
        when(reservationService.get(RESERVATION_ID))
                .thenThrow(new IllegalStateException("SELECT * FROM reservations -- secret internals"));

        mockMvc.perform(get("/api/v1/reservations/{id}", RESERVATION_ID))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.code").value("INTERNAL_ERROR"))
                .andExpect(content().string(not(containsString("SELECT"))))
                .andExpect(content().string(not(containsString("IllegalStateException"))));
    }

    @Test
    void cancelReturns200() throws Exception {
        when(reservationService.cancel(RESERVATION_ID)).thenReturn(response(ReservationStatus.CANCELLED));

        mockMvc.perform(post("/api/v1/reservations/{id}/cancel", RESERVATION_ID))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CANCELLED"));
    }

    @Test
    void listAppliesDefaultsCapsPageSizeAndReturnsEnvelope() throws Exception {
        when(reservationService.list(any(), any(), any(), any(), any()))
                .thenAnswer(inv -> new PageImpl<>(List.of(response(ReservationStatus.CONFIRMED)),
                        inv.getArgument(4, Pageable.class), 1000));

        mockMvc.perform(get("/api/v1/reservations").param("size", "1000").param("page", "2")
                        .param("resourceId", RESOURCE_ID.toString()).param("status", "CONFIRMED"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].id").value(RESERVATION_ID.toString()))
                .andExpect(jsonPath("$.page").value(2))
                .andExpect(jsonPath("$.size").value(100))
                .andExpect(jsonPath("$.totalElements").value(1000))
                .andExpect(jsonPath("$.totalPages").value(10));

        ArgumentCaptor<Pageable> pageable = ArgumentCaptor.forClass(Pageable.class);
        verify(reservationService).list(eq(RESOURCE_ID), eq(ReservationStatus.CONFIRMED), isNull(), isNull(), pageable.capture());
        assertThat(pageable.getValue()).isEqualTo(PageRequest.of(2, 100, Sort.by("startTime")));
    }
}
