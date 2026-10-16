package com.bookingcore.reservation;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ReservationRepository extends JpaRepository<Reservation, UUID>, JpaSpecificationExecutor<Reservation> {

    /**
     * Reservations with the given status on a resource that overlap the half-open interval {@code [start, end)}.
     * Used for informational availability checks only; it is never relied on to prevent double bookings.
     */
    @Query("""
            select r from Reservation r
            where r.resourceId = :resourceId
              and r.status = :status
              and r.startTime < :end
              and r.endTime > :start
            order by r.startTime
            """)
    List<Reservation> findOverlapping(@Param("resourceId") UUID resourceId,
                                      @Param("status") ReservationStatus status,
                                      @Param("start") Instant start,
                                      @Param("end") Instant end);
}
