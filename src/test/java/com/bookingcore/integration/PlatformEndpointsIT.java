package com.bookingcore.integration;

import static org.hamcrest.Matchers.hasKey;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("Health, OpenAPI and operational behaviour")
class PlatformEndpointsIT extends AbstractIntegrationTest {

    @Test
    void healthIsUpWithoutDetails() throws Exception {
        mockMvc.perform(get("/actuator/health"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("UP"))
                .andExpect(jsonPath("$.components").doesNotExist());
        mockMvc.perform(get("/actuator/health/readiness")).andExpect(status().isOk());
        mockMvc.perform(get("/actuator/health/liveness")).andExpect(status().isOk());
    }

    @Test
    void sensitiveActuatorEndpointsAreNotExposed() throws Exception {
        mockMvc.perform(get("/actuator/env")).andExpect(status().isNotFound());
        mockMvc.perform(get("/actuator/beans")).andExpect(status().isNotFound());
        mockMvc.perform(get("/actuator/heapdump")).andExpect(status().isNotFound());
    }

    @Test
    void openApiDocumentDescribesAllEndpoints() throws Exception {
        mockMvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.info.title").value("BookingCore API"))
                .andExpect(jsonPath("$.paths", hasKey("/api/v1/resources")))
                .andExpect(jsonPath("$.paths", hasKey("/api/v1/resources/{id}")))
                .andExpect(jsonPath("$.paths", hasKey("/api/v1/resources/{id}/availability")))
                .andExpect(jsonPath("$.paths", hasKey("/api/v1/reservations")))
                .andExpect(jsonPath("$.paths", hasKey("/api/v1/reservations/{id}")))
                .andExpect(jsonPath("$.paths", hasKey("/api/v1/reservations/{id}/cancel")))
                .andExpect(jsonPath("$.paths['/api/v1/reservations'].post.responses", hasKey("409")))
                .andExpect(jsonPath("$.components.schemas", hasKey("ProblemDetail")));
    }

    @Test
    void swaggerUiIsServed() throws Exception {
        mockMvc.perform(get("/swagger-ui/index.html"))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("swagger-ui")));
    }

    @Test
    void requestIdIsPropagatedToHeaderAndErrorBody() throws Exception {
        mockMvc.perform(get("/api/v1/reservations/not-a-uuid").header("X-Request-Id", "trace-123"))
                .andExpect(status().isBadRequest())
                .andExpect(header().string("X-Request-Id", "trace-123"))
                .andExpect(jsonPath("$.requestId").value("trace-123"))
                .andExpect(jsonPath("$.code").value("MALFORMED_REQUEST"));
    }

    @Test
    void unsafeRequestIdIsReplaced() throws Exception {
        mockMvc.perform(get("/actuator/health").header("X-Request-Id", "bad id\nwith newline"))
                .andExpect(header().string("X-Request-Id", org.hamcrest.Matchers.matchesPattern("[0-9a-f-]{36}")));
    }

    @Test
    void unknownPathReturnsProblemDetails() throws Exception {
        mockMvc.perform(get("/api/v1/nothing-here"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.code").value("NOT_FOUND"));
    }
}
