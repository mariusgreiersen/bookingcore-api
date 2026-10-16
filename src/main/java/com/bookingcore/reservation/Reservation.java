package com.bookingcore.reservation;

import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

/**
 * A reservation of one resource for a half-open interval {@code [startTime, endTime)}.
 *
 * <p>The resource is referenced by id rather than through a JPA association: the reservation never needs the
 * resource's state after creation, and the foreign key in the schema guarantees referential integrity.
 * {@code @Version} protects against lost updates when the same reservation is modified concurrently.
 */
@Entity
@Table(name = "reservations")
public class Reservation {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "resource_id", nullable = false, updatable = false)
    private UUID resourceId;

    @Column(name = "customer_name", nullable = false, length = 100)
    private String customerName;

    @Column(name = "customer_email", nullable = false, length = 254)
    private String customerEmail;

    @Column(name = "start_time", nullable = false)
    private Instant startTime;

    @Column(name = "end_time", nullable = false)
    private Instant endTime;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private ReservationStatus status;

    @Version
    private long version;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected Reservation() {
        // for JPA
    }

    private Reservation(UUID resourceId, String customerName, String customerEmail, TimeSlot slot, Instant now) {
        this.resourceId = resourceId;
        this.customerName = customerName;
        this.customerEmail = customerEmail;
        this.startTime = slot.start();
        this.endTime = slot.end();
        this.status = ReservationStatus.CONFIRMED;
        this.createdAt = now;
        this.updatedAt = now;
    }

    public static Reservation confirmed(UUID resourceId, String customerName, String customerEmail,
                                        TimeSlot slot, Instant now) {
        return new Reservation(resourceId, customerName, customerEmail, slot, now);
    }

    public TimeSlot slot() {
        return new TimeSlot(startTime, endTime);
    }

    public boolean isCancelled() {
        return status == ReservationStatus.CANCELLED;
    }

    public boolean hasEndedBy(Instant instant) {
        return !endTime.isAfter(instant);
    }

    public void reschedule(TimeSlot slot, Instant now) {
        requireConfirmed();
        this.startTime = slot.start();
        this.endTime = slot.end();
        this.updatedAt = now;
    }

    public void updateCustomer(String customerName, String customerEmail, Instant now) {
        requireConfirmed();
        if (customerName != null) {
            this.customerName = customerName;
        }
        if (customerEmail != null) {
            this.customerEmail = customerEmail;
        }
        this.updatedAt = now;
    }

    /**
     * Cancels the reservation. Idempotent: returns {@code false} without changing anything if it was already
     * cancelled, {@code true} if this call performed the transition.
     */
    public boolean cancel(Instant now) {
        if (isCancelled()) {
            return false;
        }
        this.status = ReservationStatus.CANCELLED;
        this.updatedAt = now;
        return true;
    }

    private void requireConfirmed() {
        if (isCancelled()) {
            throw new IllegalStateException("Cancelled reservations cannot be modified");
        }
    }

    public UUID getId() {
        return id;
    }

    public UUID getResourceId() {
        return resourceId;
    }

    public String getCustomerName() {
        return customerName;
    }

    public String getCustomerEmail() {
        return customerEmail;
    }

    public Instant getStartTime() {
        return startTime;
    }

    public Instant getEndTime() {
        return endTime;
    }

    public ReservationStatus getStatus() {
        return status;
    }

    public long getVersion() {
        return version;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
