package com.bookingcore.resource;

import java.time.Clock;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.bookingcore.common.exception.NotFoundException;
import com.bookingcore.resource.dto.CreateResourceRequest;
import com.bookingcore.resource.dto.ResourceResponse;
import com.bookingcore.resource.dto.UpdateResourceRequest;

@Service
public class ResourceService {

    private static final Logger log = LoggerFactory.getLogger(ResourceService.class);

    private final ResourceRepository resourceRepository;
    private final Clock clock;

    public ResourceService(ResourceRepository resourceRepository, Clock clock) {
        this.resourceRepository = resourceRepository;
        this.clock = clock;
    }

    @Transactional
    public ResourceResponse create(CreateResourceRequest request) {
        Resource resource = new Resource(
                request.name().trim(),
                trimToNull(request.description()),
                trimToNull(request.location()),
                request.capacity(),
                clock.instant());
        Resource saved = resourceRepository.save(resource);
        log.info("Resource created: resourceId={} capacity={}", saved.getId(), saved.getCapacity());
        return ResourceResponse.from(saved);
    }

    @Transactional(readOnly = true)
    public ResourceResponse get(UUID id) {
        return ResourceResponse.from(findOrThrow(id));
    }

    @Transactional(readOnly = true)
    public Page<ResourceResponse> list(Boolean active, Integer minCapacity, String location, Pageable pageable) {
        return resourceRepository
                .findAll(ResourceSpecifications.withFilters(active, minCapacity, location), pageable)
                .map(ResourceResponse::from);
    }

    @Transactional
    public ResourceResponse update(UUID id, UpdateResourceRequest request) {
        Resource resource = findOrThrow(id);
        if (request.name() != null) {
            resource.rename(request.name().trim());
        }
        if (request.description() != null) {
            resource.describe(trimToNull(request.description()));
        }
        if (request.location() != null) {
            resource.relocate(trimToNull(request.location()));
        }
        if (request.capacity() != null) {
            resource.changeCapacity(request.capacity());
        }
        if (request.active() != null && request.active() != resource.isActive()) {
            resource.setActive(request.active());
            log.info("Resource {}: resourceId={}", request.active() ? "activated" : "deactivated", id);
        }
        resource.touch(clock.instant());
        return ResourceResponse.from(resourceRepository.saveAndFlush(resource));
    }

    private Resource findOrThrow(UUID id) {
        return resourceRepository.findById(id).orElseThrow(() -> NotFoundException.resource(id));
    }

    private static String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
