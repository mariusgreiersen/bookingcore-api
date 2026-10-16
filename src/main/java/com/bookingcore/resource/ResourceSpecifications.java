package com.bookingcore.resource;

import java.util.ArrayList;
import java.util.List;

import org.springframework.data.jpa.domain.Specification;

import jakarta.persistence.criteria.Predicate;

/** Parameterized filters for resource listings. A {@code null} argument means "no filter". */
final class ResourceSpecifications {

    private ResourceSpecifications() {
    }

    static Specification<Resource> withFilters(Boolean active, Integer minCapacity, String location) {
        return (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            if (active != null) {
                predicates.add(cb.equal(root.get("active"), active));
            }
            if (minCapacity != null) {
                predicates.add(cb.greaterThanOrEqualTo(root.get("capacity"), minCapacity));
            }
            if (location != null && !location.isBlank()) {
                String pattern = "%" + escapeLike(location.trim().toLowerCase()) + "%";
                predicates.add(cb.like(cb.lower(root.get("location")), pattern, '\\'));
            }
            return cb.and(predicates.toArray(Predicate[]::new));
        };
    }

    private static String escapeLike(String value) {
        return value.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
    }
}
