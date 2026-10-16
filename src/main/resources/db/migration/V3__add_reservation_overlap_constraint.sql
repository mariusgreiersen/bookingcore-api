-- Database-enforced double-booking prevention.
--
-- btree_gist lets a GiST index combine scalar equality (resource_id WITH =) with
-- range overlap (tstzrange WITH &&). It is a trusted extension (PostgreSQL 13+),
-- so the database owner can install it without superuser rights.
CREATE EXTENSION IF NOT EXISTS btree_gist;

-- No two CONFIRMED reservations for the same resource may have overlapping intervals.
--   * '[)' makes intervals half-open, so 10:00-11:00 and 11:00-12:00 do not overlap.
--   * The WHERE clause makes this a partial constraint: CANCELLED rows never block a slot.
--   * PostgreSQL checks the constraint against the index while holding it for the inserting
--     transaction, so concurrent conflicting writers are serialized: the second one waits for
--     the first to commit or roll back, then fails with SQLSTATE 23P01 (exclusion_violation).
ALTER TABLE reservations
    ADD CONSTRAINT reservations_no_overlap
        EXCLUDE USING gist (
            resource_id WITH =,
            tstzrange(start_time, end_time, '[)') WITH &&
        )
        WHERE (status = 'CONFIRMED');
