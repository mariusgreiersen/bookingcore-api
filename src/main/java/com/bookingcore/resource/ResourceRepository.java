package com.bookingcore.resource;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import jakarta.persistence.LockModeType;

public interface ResourceRepository extends JpaRepository<Resource, UUID>, JpaSpecificationExecutor<Resource> {

    /**
     * Loads a resource and locks its row for the rest of the transaction
     * ({@code SELECT ... FOR NO KEY UPDATE} on PostgreSQL).
     *
     * <p>Every write that creates or moves a confirmed reservation takes this lock first, which has two effects:
     * <ul>
     *   <li>Writers for the <em>same</em> resource are queued instead of racing inside the exclusion-constraint
     *       check. Without it, simultaneous conflicting inserts wait on each other's uncommitted index entries,
     *       which PostgreSQL resolves by aborting all but one with a deadlock error (SQLSTATE 40P01). With it,
     *       each loser runs after the winner has committed and fails cleanly with an exclusion violation.</li>
     *   <li>The resource cannot be deactivated between the "is active" check and the insert.</li>
     * </ul>
     * Bookings of different resources lock different rows and remain fully parallel. The lock only orders
     * writers; preventing overlaps is still the job of the exclusion constraint.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select r from Resource r where r.id = :id")
    Optional<Resource> findByIdForBooking(@Param("id") UUID id);
}
