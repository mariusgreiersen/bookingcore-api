package com.bookingcore.reservation;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.domain.Specification;

import jakarta.persistence.criteria.Predicate;

/** Parameterized filters for reservation listings. A {@code null} argument means "no filter". */
final class ReservationSpecifications {

    private ReservationSpecifications() {
    }

    /**
     * @param from if set, only reservations ending after this instant
     * @param to   if set, only reservations starting before this instant
     *             (together: reservations overlapping {@code [from, to)})
     */
    static Specification<Reservation> withFilters(UUID resourceId, ReservationStatus status, Instant from, Instant to) {
        return (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            if (resourceId != null) {
                predicates.add(cb.equal(root.get("resourceId"), resourceId));
            }
            if (status != null) {
                predicates.add(cb.equal(root.get("status"), status));
            }
            if (from != null) {
                predicates.add(cb.greaterThan(root.get("endTime"), from));
            }
            if (to != null) {
                predicates.add(cb.lessThan(root.get("startTime"), to));
            }
            return cb.and(predicates.toArray(Predicate[]::new));
        };
    }
}
