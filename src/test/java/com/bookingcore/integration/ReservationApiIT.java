package com.bookingcore.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.endsWith;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

import com.jayway.jsonpath.JsonPath;

@DisplayName("Reservation API (PostgreSQL)")
class ReservationApiIT extends AbstractIntegrationTest {

    @Nested
    @DisplayName("create and retrieve")
    class CreateAndRetrieve {

        @Test
        void createsReservationAndPersistsIt() throws Exception {
            UUID roomId = createResource();
            OffsetDateTime start = BASE.withHour(10).withOffsetSameInstant(ZoneOffset.ofHours(1));
            OffsetDateTime end = start.plusHours(1);

            String json = postReservation(roomId, start, end)
                    .andExpect(status().isCreated())
                    .andExpect(header().string("Location", org.hamcrest.Matchers.matchesPattern(
                            ".*/api/v1/reservations/[0-9a-f-]{36}$")))
                    .andExpect(jsonPath("$.resourceId").value(roomId.toString()))
                    .andExpect(jsonPath("$.status").value("CONFIRMED"))
                    .andExpect(jsonPath("$.customerName").value("Alex Jensen"))
                    // Offsets are accepted on input and normalised to UTC on output.
                    .andExpect(jsonPath("$.startTime").value(start.toInstant().toString()))
                    .andReturn().getResponse().getContentAsString();
            UUID id = UUID.fromString(JsonPath.read(json, "$.id"));

            Map<String, Object> row = jdbcTemplate.queryForMap(
                    "SELECT resource_id, status, start_time, end_time FROM reservations WHERE id = ?", id);
            assertThat(row.get("resource_id")).isEqualTo(roomId);
            assertThat(row.get("status")).isEqualTo("CONFIRMED");

            mockMvc.perform(get("/api/v1/reservations/{id}", id))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.id").value(id.toString()))
                    .andExpect(jsonPath("$.endTime").value(end.toInstant().toString()));
        }

        @Test
        void unknownResourceReturns404() throws Exception {
            postReservation(UUID.randomUUID(), BASE.withHour(10), BASE.withHour(11))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.code").value("RESOURCE_NOT_FOUND"));
        }

        @Test
        void unknownReservationReturns404() throws Exception {
            mockMvc.perform(get("/api/v1/reservations/{id}", UUID.randomUUID()))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.code").value("RESERVATION_NOT_FOUND"));
        }

        @Test
        void inactiveResourceRejectsReservations() throws Exception {
            UUID roomId = createResource();
            mockMvc.perform(patch("/api/v1/resources/{id}", roomId)
                            .contentType(MediaType.APPLICATION_JSON).content("{\"active\": false}"))
                    .andExpect(status().isOk());

            postReservation(roomId, BASE.withHour(10), BASE.withHour(11))
                    .andExpect(status().isConflict())
                    .andExpect(jsonPath("$.code").value("RESOURCE_INACTIVE"));
        }

        @Test
        void startInThePastIsRejected() throws Exception {
            UUID roomId = createResource();
            OffsetDateTime start = OffsetDateTime.now(ZoneOffset.UTC).minusHours(1);

            postReservation(roomId, start, start.plusHours(2))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value("INVALID_TIME_RANGE"));
        }

        @Test
        void endBeforeStartIsRejected() throws Exception {
            UUID roomId = createResource();

            postReservation(roomId, BASE.withHour(11), BASE.withHour(10))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value("INVALID_TIME_RANGE"));
        }

        @Test
        void timestampWithoutOffsetIsRejected() throws Exception {
            UUID roomId = createResource();
            String body = """
                    {"resourceId": "%s", "customerName": "A", "customerEmail": "a@example.com",
                     "startTime": "2030-03-15T10:00:00", "endTime": "2030-03-15T11:00:00"}
                    """.formatted(roomId);

            mockMvc.perform(post("/api/v1/reservations").contentType(MediaType.APPLICATION_JSON).content(body))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value("MALFORMED_REQUEST"));
        }
    }

    @Nested
    @DisplayName("overlap rules")
    class OverlapRules {

        @Test
        void overlappingReservationIsRejectedWith409() throws Exception {
            UUID roomId = createResource();
            createReservation(roomId, BASE.withHour(10), BASE.withHour(11));

            postReservation(roomId, BASE.withHour(10).plusMinutes(30), BASE.withHour(11).plusMinutes(30))
                    .andExpect(status().isConflict())
                    .andExpect(header().string("Content-Type", "application/problem+json"))
                    .andExpect(jsonPath("$.title").value("Reservation conflict"))
                    .andExpect(jsonPath("$.status").value(409))
                    .andExpect(jsonPath("$.code").value("RESERVATION_CONFLICT"))
                    .andExpect(jsonPath("$.detail").value(
                            "The selected resource is already booked during this time interval."));

            assertThat(confirmedOverlapping(roomId, BASE.withHour(10), BASE.withHour(12))).isEqualTo(1);
        }

        @Test
        void backToBackReservationsAreAllowed() throws Exception {
            UUID roomId = createResource();
            createReservation(roomId, BASE.withHour(10), BASE.withHour(11));

            postReservation(roomId, BASE.withHour(11), BASE.withHour(12)).andExpect(status().isCreated());
            postReservation(roomId, BASE.withHour(9), BASE.withHour(10)).andExpect(status().isCreated());
        }

        @Test
        void overlapByOneSecondIsRejected() throws Exception {
            UUID roomId = createResource();
            createReservation(roomId, BASE.withHour(10), BASE.withHour(11));

            postReservation(roomId, BASE.withHour(11).minusSeconds(1), BASE.withHour(12))
                    .andExpect(status().isConflict());
            postReservation(roomId, BASE.withHour(9), BASE.withHour(10).plusSeconds(1))
                    .andExpect(status().isConflict());
        }

        @Test
        void containedAndContainingIntervalsAreRejected() throws Exception {
            UUID roomId = createResource();
            createReservation(roomId, BASE.withHour(10), BASE.withHour(12));

            postReservation(roomId, BASE.withHour(10).plusMinutes(15), BASE.withHour(10).plusMinutes(45))
                    .andExpect(status().isConflict());
            postReservation(roomId, BASE.withHour(9), BASE.withHour(13))
                    .andExpect(status().isConflict());
            postReservation(roomId, BASE.withHour(10), BASE.withHour(12))
                    .andExpect(status().isConflict());
        }

        @Test
        void sameIntervalExpressedInDifferentOffsetIsRejected() throws Exception {
            UUID roomId = createResource();
            createReservation(roomId, BASE.withHour(10), BASE.withHour(11));

            OffsetDateTime sameInstantInCopenhagenSummer = BASE.withHour(10).withOffsetSameInstant(ZoneOffset.ofHours(2));
            postReservation(roomId, sameInstantInCopenhagenSummer, sameInstantInCopenhagenSummer.plusHours(1))
                    .andExpect(status().isConflict());
        }

        @Test
        void differentRoomsCanBeBookedAtTheSameTime() throws Exception {
            UUID roomA = createResource();
            UUID roomB = createResource();

            createReservation(roomA, BASE.withHour(10), BASE.withHour(11));
            postReservation(roomB, BASE.withHour(10), BASE.withHour(11)).andExpect(status().isCreated());
        }

        @Test
        void cancellingFreesTheSlot() throws Exception {
            UUID roomId = createResource();
            UUID first = createReservation(roomId, BASE.withHour(10), BASE.withHour(11));
            postReservation(roomId, BASE.withHour(10), BASE.withHour(11)).andExpect(status().isConflict());

            mockMvc.perform(post("/api/v1/reservations/{id}/cancel", first))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.status").value("CANCELLED"));

            postReservation(roomId, BASE.withHour(10), BASE.withHour(11)).andExpect(status().isCreated());
            // The cancelled reservation is kept for history.
            assertThat(jdbcTemplate.queryForObject(
                    "SELECT status FROM reservations WHERE id = ?", String.class, first)).isEqualTo("CANCELLED");
        }
    }

    @Nested
    @DisplayName("update")
    class Update {

        @Test
        void updatesCustomerDetailsAndInterval() throws Exception {
            UUID roomId = createResource();
            UUID id = createReservation(roomId, BASE.withHour(10), BASE.withHour(11));
            String body = """
                    {"customerName": "Alex J.", "startTime": "%s", "endTime": "%s"}
                    """.formatted(BASE.withHour(13), BASE.withHour(14));

            mockMvc.perform(patch("/api/v1/reservations/{id}", id).contentType(MediaType.APPLICATION_JSON).content(body))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.customerName").value("Alex J."))
                    .andExpect(jsonPath("$.customerEmail").value("alex@example.com"))
                    .andExpect(jsonPath("$.startTime").value(BASE.withHour(13).toInstant().toString()));

            // The old slot is free again.
            postReservation(roomId, BASE.withHour(10), BASE.withHour(11)).andExpect(status().isCreated());
        }

        @Test
        void extendingIntoOwnPreviousIntervalIsAllowed() throws Exception {
            UUID roomId = createResource();
            UUID id = createReservation(roomId, BASE.withHour(10), BASE.withHour(11));

            mockMvc.perform(patch("/api/v1/reservations/{id}", id).contentType(MediaType.APPLICATION_JSON)
                            .content("{\"endTime\": \"%s\"}".formatted(BASE.withHour(12))))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.endTime").value(BASE.withHour(12).toInstant().toString()));
        }

        @Test
        void updateIntoConflictingIntervalIsRejectedAndOriginalIsUnchanged() throws Exception {
            UUID roomId = createResource();
            createReservation(roomId, BASE.withHour(10), BASE.withHour(11));
            UUID second = createReservation(roomId, BASE.withHour(12), BASE.withHour(13));
            Map<String, Object> before = jdbcTemplate.queryForMap("SELECT * FROM reservations WHERE id = ?", second);

            String body = """
                    {"customerName": "Changed Name", "startTime": "%s", "endTime": "%s"}
                    """.formatted(BASE.withHour(10).plusMinutes(30), BASE.withHour(12).plusMinutes(30));
            mockMvc.perform(patch("/api/v1/reservations/{id}", second).contentType(MediaType.APPLICATION_JSON).content(body))
                    .andExpect(status().isConflict())
                    .andExpect(jsonPath("$.code").value("RESERVATION_CONFLICT"));

            Map<String, Object> after = jdbcTemplate.queryForMap("SELECT * FROM reservations WHERE id = ?", second);
            assertThat(after).isEqualTo(before);
            mockMvc.perform(get("/api/v1/reservations/{id}", second))
                    .andExpect(jsonPath("$.customerName").value("Alex Jensen"))
                    .andExpect(jsonPath("$.startTime").value(BASE.withHour(12).toInstant().toString()));
        }

        @Test
        void cancelledReservationCannotBeUpdated() throws Exception {
            UUID roomId = createResource();
            UUID id = createReservation(roomId, BASE.withHour(10), BASE.withHour(11));
            mockMvc.perform(post("/api/v1/reservations/{id}/cancel", id)).andExpect(status().isOk());

            mockMvc.perform(patch("/api/v1/reservations/{id}", id).contentType(MediaType.APPLICATION_JSON)
                            .content("{\"customerName\": \"Someone\"}"))
                    .andExpect(status().isConflict())
                    .andExpect(jsonPath("$.code").value("INVALID_RESERVATION_STATE"));
        }
    }

    @Nested
    @DisplayName("cancel")
    class Cancel {

        @Test
        void cancellationIsIdempotent() throws Exception {
            UUID roomId = createResource();
            UUID id = createReservation(roomId, BASE.withHour(10), BASE.withHour(11));

            String first = mockMvc.perform(post("/api/v1/reservations/{id}/cancel", id))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.status").value("CANCELLED"))
                    .andReturn().getResponse().getContentAsString();
            String second = mockMvc.perform(post("/api/v1/reservations/{id}/cancel", id))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.status").value("CANCELLED"))
                    .andReturn().getResponse().getContentAsString();

            assertThat(second).isEqualTo(first);
        }

        @Test
        void cancellingUnknownReservationReturns404() throws Exception {
            mockMvc.perform(post("/api/v1/reservations/{id}/cancel", UUID.randomUUID()))
                    .andExpect(status().isNotFound());
        }
    }

    @Nested
    @DisplayName("list")
    class ListReservations {

        @Test
        void filtersByResourceStatusAndDateRangeWithPagination() throws Exception {
            UUID roomId = createResource();
            createReservation(roomId, BASE.withHour(8), BASE.withHour(9));
            createReservation(roomId, BASE.withHour(10), BASE.withHour(11));
            UUID cancelled = createReservation(roomId, BASE.withHour(12), BASE.withHour(13));
            mockMvc.perform(post("/api/v1/reservations/{id}/cancel", cancelled)).andExpect(status().isOk());

            mockMvc.perform(get("/api/v1/reservations").param("resourceId", roomId.toString()))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.totalElements").value(3))
                    .andExpect(jsonPath("$.content[0].startTime").value(BASE.withHour(8).toInstant().toString()));

            mockMvc.perform(get("/api/v1/reservations")
                            .param("resourceId", roomId.toString())
                            .param("status", "CONFIRMED")
                            .param("size", "1"))
                    .andExpect(jsonPath("$.content", hasSize(1)))
                    .andExpect(jsonPath("$.totalElements").value(2))
                    .andExpect(jsonPath("$.totalPages").value(2))
                    .andExpect(jsonPath("$.size").value(1));

            mockMvc.perform(get("/api/v1/reservations")
                            .param("resourceId", roomId.toString())
                            .param("from", BASE.withHour(9).toInstant().toString())
                            .param("to", BASE.withHour(12).toInstant().toString()))
                    .andExpect(jsonPath("$.totalElements").value(1))
                    .andExpect(jsonPath("$.content[0].startTime").value(endsWith("10:00:00Z")));
        }

        @Test
        void invalidStatusFilterReturns400() throws Exception {
            mockMvc.perform(get("/api/v1/reservations").param("status", "PENDING"))
                    .andExpect(status().isBadRequest());
        }
    }
}
