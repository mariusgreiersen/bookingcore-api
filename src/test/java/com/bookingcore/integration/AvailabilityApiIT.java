package com.bookingcore.integration;

import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.OffsetDateTime;
import java.util.UUID;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.ResultActions;

@DisplayName("Availability API (PostgreSQL)")
class AvailabilityApiIT extends AbstractIntegrationTest {

    @Test
    void freeIntervalIsAvailable() throws Exception {
        UUID roomId = createResource();
        createReservation(roomId, BASE.withHour(10), BASE.withHour(11));

        // Back-to-back with the existing reservation: still available.
        check(roomId, BASE.withHour(11), BASE.withHour(12))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.available").value(true))
                .andExpect(jsonPath("$.reason").value(nullValue()))
                .andExpect(jsonPath("$.conflicts", hasSize(0)));
    }

    @Test
    void overlappingIntervalIsUnavailableAndListsConflictsWithoutCustomerData() throws Exception {
        UUID roomId = createResource();
        createReservation(roomId, BASE.withHour(10), BASE.withHour(11));

        check(roomId, BASE.withHour(9), BASE.withHour(12))
                .andExpect(jsonPath("$.available").value(false))
                .andExpect(jsonPath("$.reason").value("OVERLAPPING_RESERVATION"))
                .andExpect(jsonPath("$.conflicts", hasSize(1)))
                .andExpect(jsonPath("$.conflicts[0].startTime").value(BASE.withHour(10).toInstant().toString()))
                .andExpect(jsonPath("$.conflicts[0].customerName").doesNotExist());
    }

    @Test
    void cancelledReservationsDoNotBlockAvailability() throws Exception {
        UUID roomId = createResource();
        UUID id = createReservation(roomId, BASE.withHour(10), BASE.withHour(11));
        mockMvc.perform(post("/api/v1/reservations/{id}/cancel", id)).andExpect(status().isOk());

        check(roomId, BASE.withHour(10), BASE.withHour(11))
                .andExpect(jsonPath("$.available").value(true));
    }

    @Test
    void unknownResourceReturns404() throws Exception {
        check(UUID.randomUUID(), BASE.withHour(10), BASE.withHour(11))
                .andExpect(status().isNotFound());
    }

    @Test
    void missingParameterReturns400() throws Exception {
        mockMvc.perform(get("/api/v1/resources/{id}/availability", createResource())
                        .param("startTime", BASE.toString()))
                .andExpect(status().isBadRequest());
    }

    private ResultActions check(UUID roomId, OffsetDateTime start, OffsetDateTime end) throws Exception {
        return mockMvc.perform(get("/api/v1/resources/{id}/availability", roomId)
                .param("startTime", start.toString())
                .param("endTime", end.toString()));
    }
}
