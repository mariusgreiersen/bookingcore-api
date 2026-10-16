package com.bookingcore.integration;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import com.jayway.jsonpath.JsonPath;

/**
 * Base class for integration tests: full application context, embedded server on a random port, MockMvc and
 * a real PostgreSQL container. All subclasses share the same configuration, so Spring caches one context and
 * one container for the whole integration-test run.
 *
 * <p>Tests do not truncate tables between runs; instead every test creates its own resource(s), which keeps
 * tests independent while exercising the database the way production would.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
public abstract class AbstractIntegrationTest {

    /** A future, hour-aligned base time so that time-based validation never makes tests flaky. */
    protected static final OffsetDateTime BASE =
            OffsetDateTime.now(ZoneOffset.UTC).plusDays(30).truncatedTo(ChronoUnit.HOURS);

    @Autowired
    protected MockMvc mockMvc;

    @Autowired
    protected JdbcTemplate jdbcTemplate;

    protected UUID createResource(String name, int capacity) throws Exception {
        String body = """
                {"name": "%s", "description": "Test room", "location": "Floor 1", "capacity": %d}
                """.formatted(name, capacity);
        String json = mockMvc.perform(post("/api/v1/resources")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return UUID.fromString(JsonPath.read(json, "$.id"));
    }

    protected UUID createResource() throws Exception {
        return createResource("Room " + UUID.randomUUID().toString().substring(0, 8), 4);
    }

    protected ResultActions postReservation(UUID resourceId, OffsetDateTime start, OffsetDateTime end) throws Exception {
        return mockMvc.perform(post("/api/v1/reservations")
                .contentType(MediaType.APPLICATION_JSON)
                .content(reservationJson(resourceId, start, end)));
    }

    protected UUID createReservation(UUID resourceId, OffsetDateTime start, OffsetDateTime end) throws Exception {
        String json = postReservation(resourceId, start, end)
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return UUID.fromString(JsonPath.read(json, "$.id"));
    }

    protected static String reservationJson(UUID resourceId, OffsetDateTime start, OffsetDateTime end) {
        return """
                {
                  "resourceId": "%s",
                  "customerName": "Alex Jensen",
                  "customerEmail": "alex@example.com",
                  "startTime": "%s",
                  "endTime": "%s"
                }
                """.formatted(resourceId, start, end);
    }

    /** Number of CONFIRMED reservations of a resource overlapping [start, end), counted directly in PostgreSQL. */
    protected int confirmedOverlapping(UUID resourceId, OffsetDateTime start, OffsetDateTime end) {
        Integer count = jdbcTemplate.queryForObject("""
                        SELECT count(*) FROM reservations
                        WHERE resource_id = ? AND status = 'CONFIRMED'
                          AND tstzrange(start_time, end_time, '[)') && tstzrange(?::timestamptz, ?::timestamptz, '[)')
                        """,
                Integer.class, resourceId, start, end);
        return count == null ? 0 : count;
    }
}
