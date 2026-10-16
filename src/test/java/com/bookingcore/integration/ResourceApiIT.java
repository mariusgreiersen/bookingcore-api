package com.bookingcore.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.everyItem;
import static org.hamcrest.Matchers.greaterThanOrEqualTo;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

@DisplayName("Resource API (PostgreSQL)")
class ResourceApiIT extends AbstractIntegrationTest {

    @Test
    void createsAndRetrievesResource() throws Exception {
        String body = """
                {"name": "  Room Z  ", "description": "Corner room", "location": "Floor 5", "capacity": 6}
                """;
        String location = mockMvc.perform(post("/api/v1/resources").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.name").value("Room Z"))
                .andExpect(jsonPath("$.active").value(true))
                .andExpect(header().exists("Location"))
                .andReturn().getResponse().getHeader("Location");

        mockMvc.perform(get(location))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.capacity").value(6))
                .andExpect(jsonPath("$.location").value("Floor 5"));
    }

    @Test
    void updatesAndDeactivatesResource() throws Exception {
        UUID id = createResource("Room Patch", 4);

        mockMvc.perform(patch("/api/v1/resources/{id}", id).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"capacity\": 10, \"active\": false}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.capacity").value(10))
                .andExpect(jsonPath("$.active").value(false))
                .andExpect(jsonPath("$.name").value("Room Patch"));

        Map<String, Object> row = jdbcTemplate.queryForMap("SELECT capacity, active FROM resources WHERE id = ?", id);
        assertThat(row).containsEntry("capacity", 10).containsEntry("active", false);
    }

    @Test
    void listFiltersByActiveAndCapacity() throws Exception {
        UUID big = createResource("Big Hall " + UUID.randomUUID(), 50);
        UUID inactive = createResource("Closed Room " + UUID.randomUUID(), 60);
        mockMvc.perform(patch("/api/v1/resources/{id}", inactive).contentType(MediaType.APPLICATION_JSON)
                .content("{\"active\": false}")).andExpect(status().isOk());

        mockMvc.perform(get("/api/v1/resources").param("active", "true").param("minCapacity", "40").param("size", "100"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[*].id", hasItem(big.toString())))
                .andExpect(jsonPath("$.content[*].id", not(hasItem(inactive.toString()))))
                .andExpect(jsonPath("$.content[*].capacity", everyItem(greaterThanOrEqualTo(40))));
    }

    @Test
    void pageSizeIsCappedAt100() throws Exception {
        mockMvc.perform(get("/api/v1/resources").param("size", "5000"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.size").value(100));
    }

    @Test
    void unknownSortPropertyReturns400() throws Exception {
        mockMvc.perform(get("/api/v1/resources").param("sort", "doesNotExist"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
    }

    @Test
    void invalidCapacityIsRejected() throws Exception {
        mockMvc.perform(post("/api/v1/resources").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\": \"Room\", \"capacity\": 0}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.errors[0].field").value("capacity"));
    }
}
