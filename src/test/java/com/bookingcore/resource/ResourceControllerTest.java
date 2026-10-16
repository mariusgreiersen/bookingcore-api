package com.bookingcore.resource;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import com.bookingcore.common.exception.NotFoundException;
import com.bookingcore.resource.dto.ResourceResponse;

@WebMvcTest(ResourceController.class)
class ResourceControllerTest {

    private static final UUID ID = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final Instant NOW = Instant.parse("2027-03-01T08:00:00Z");
    private static final ResourceResponse ROOM_A =
            new ResourceResponse(ID, "Room A", "Huddle room", "Floor 1", 4, true, NOW, NOW);

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private ResourceService resourceService;

    @Test
    void createReturns201WithLocation() throws Exception {
        when(resourceService.create(any())).thenReturn(ROOM_A);

        mockMvc.perform(post("/api/v1/resources").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\": \"Room A\", \"capacity\": 4}"))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", "http://localhost/api/v1/resources/" + ID))
                .andExpect(jsonPath("$.name").value("Room A"))
                .andExpect(jsonPath("$.active").value(true));
    }

    @Test
    void rejectsBlankNameAndNonPositiveCapacity() throws Exception {
        mockMvc.perform(post("/api/v1/resources").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\": \"   \", \"capacity\": 0}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.errors.length()").value(2));
        verifyNoInteractions(resourceService);
    }

    @Test
    void patchRejectsBlankName() throws Exception {
        mockMvc.perform(patch("/api/v1/resources/{id}", ID).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\": \" \"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("name"));
    }

    @Test
    void getUnknownReturns404ProblemDetails() throws Exception {
        when(resourceService.get(ID)).thenThrow(NotFoundException.resource(ID));

        mockMvc.perform(get("/api/v1/resources/{id}", ID))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.title").value("Resource not found"))
                .andExpect(jsonPath("$.code").value("RESOURCE_NOT_FOUND"));
    }

    @Test
    void listReturnsPageEnvelope() throws Exception {
        when(resourceService.list(any(), any(), any(), any()))
                .thenAnswer(inv -> new PageImpl<>(List.of(ROOM_A), inv.getArgument(3, Pageable.class), 1));

        mockMvc.perform(get("/api/v1/resources").param("active", "true"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].id").value(ID.toString()))
                .andExpect(jsonPath("$.page").value(0))
                .andExpect(jsonPath("$.size").value(20))
                .andExpect(jsonPath("$.totalElements").value(1));
    }

    @Test
    void listRejectsNonPositiveMinCapacity() throws Exception {
        mockMvc.perform(get("/api/v1/resources").param("minCapacity", "0"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.errors[0].field").value("minCapacity"));
    }
}
