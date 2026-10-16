package com.bookingcore.resource;

import java.net.URI;
import java.util.UUID;

import org.springdoc.core.annotations.ParameterObject;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
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
import com.bookingcore.resource.dto.CreateResourceRequest;
import com.bookingcore.resource.dto.ResourceResponse;
import com.bookingcore.resource.dto.UpdateResourceRequest;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Positive;

@RestController
@RequestMapping("/api/v1/resources")
@Tag(name = "Resources", description = "Reservable resources such as meeting rooms and workspaces")
public class ResourceController {

    private final ResourceService resourceService;

    public ResourceController(ResourceService resourceService) {
        this.resourceService = resourceService;
    }

    @GetMapping
    @Operation(summary = "List resources",
            description = "Paginated list of resources. All filters are optional and combined with AND. "
                    + "Sortable by name, capacity, location, createdAt. Page size is capped at 100.")
    @ApiResponse(responseCode = "200", description = "A page of resources")
    @ApiErrorResponses.BadRequest
    public PageResponse<ResourceResponse> list(
            @Parameter(description = "Only active (true) or inactive (false) resources")
            @RequestParam(required = false) Boolean active,
            @Parameter(description = "Minimum capacity (attendees)", example = "6")
            @RequestParam(required = false) @Positive Integer minCapacity,
            @Parameter(description = "Case-insensitive substring match on location", example = "floor 2")
            @RequestParam(required = false) String location,
            @ParameterObject @PageableDefault(size = 20, sort = "name", direction = Sort.Direction.ASC) Pageable pageable) {
        return PageResponse.of(resourceService.list(active, minCapacity, location, pageable));
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get a resource")
    @ApiResponse(responseCode = "200", description = "The resource")
    @ApiErrorResponses.BadRequest
    @ApiErrorResponses.NotFound
    public ResourceResponse get(@PathVariable UUID id) {
        return resourceService.get(id);
    }

    @PostMapping
    @Operation(summary = "Create a resource")
    @ApiResponse(responseCode = "201", description = "Resource created; Location header points to it")
    @ApiErrorResponses.BadRequest
    public ResponseEntity<ResourceResponse> create(@Valid @RequestBody CreateResourceRequest request) {
        ResourceResponse created = resourceService.create(request);
        URI location = ServletUriComponentsBuilder.fromCurrentRequest()
                .path("/{id}").buildAndExpand(created.id()).toUri();
        return ResponseEntity.created(location).body(created);
    }

    @PatchMapping("/{id}")
    @Operation(summary = "Update or deactivate a resource",
            description = "Partial update; omitted fields are unchanged. Deactivating a resource keeps existing "
                    + "reservations but rejects new ones with 409 RESOURCE_INACTIVE.")
    @ApiResponse(responseCode = "200", description = "The updated resource")
    @ApiErrorResponses.BadRequest
    @ApiErrorResponses.NotFound
    @ApiErrorResponses.Conflict
    public ResourceResponse update(@PathVariable UUID id, @Valid @RequestBody UpdateResourceRequest request) {
        return resourceService.update(id, request);
    }
}
