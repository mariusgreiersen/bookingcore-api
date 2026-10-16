-- Reservations of a resource for a half-open time interval [start_time, end_time).
-- Cancelled reservations are kept for history; they are never physically deleted.
CREATE TABLE reservations
(
    id             UUID         NOT NULL,
    resource_id    UUID         NOT NULL,
    customer_name  VARCHAR(100) NOT NULL,
    customer_email VARCHAR(254) NOT NULL,
    start_time     TIMESTAMPTZ  NOT NULL,
    end_time       TIMESTAMPTZ  NOT NULL,
    status         VARCHAR(20)  NOT NULL,
    version        BIGINT       NOT NULL DEFAULT 0,
    created_at     TIMESTAMPTZ  NOT NULL,
    updated_at     TIMESTAMPTZ  NOT NULL,

    CONSTRAINT pk_reservations PRIMARY KEY (id),
    CONSTRAINT fk_reservations_resource FOREIGN KEY (resource_id) REFERENCES resources (id),
    CONSTRAINT chk_reservations_time_range CHECK (start_time < end_time),
    CONSTRAINT chk_reservations_status CHECK (status IN ('CONFIRMED', 'CANCELLED'))
);
