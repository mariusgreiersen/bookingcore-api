-- Supports the foreign key and reservation listings filtered by resource (including cancelled rows,
-- which the partial exclusion-constraint index does not cover).
CREATE INDEX idx_reservations_resource_start ON reservations (resource_id, start_time);

-- Supports reservation listings filtered by status and/or date range.
CREATE INDEX idx_reservations_status_start ON reservations (status, start_time);

-- Supports resource listings filtered by active flag, ordered by name.
CREATE INDEX idx_resources_active_name ON resources (active, name);
