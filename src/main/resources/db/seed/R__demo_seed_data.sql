-- Demo data for local development (Spring profile "demo" only; never part of db/migration).
-- Fixed UUIDs make the README examples copy-pasteable. Reservation times are relative to the
-- day the seed runs, so they are in the future on a freshly created database.
INSERT INTO resources (id, name, description, location, capacity, active, created_at, updated_at)
VALUES ('11111111-1111-1111-1111-111111111111', 'Room A', 'Small huddle room with a wall-mounted screen', 'Floor 1', 4, TRUE, now(), now()),
       ('22222222-2222-2222-2222-222222222222', 'Room B', 'Meeting room with video conferencing', 'Floor 2', 8, TRUE, now(), now()),
       ('33333333-3333-3333-3333-333333333333', 'Room C', 'Large conference room with projector', 'Floor 3', 12, TRUE, now(), now()),
       ('44444444-4444-4444-4444-444444444444', 'Quiet Workspace', 'Closed for renovation', 'Floor 1', 1, FALSE, now(), now())
ON CONFLICT (id) DO NOTHING;

INSERT INTO reservations (id, resource_id, customer_name, customer_email, start_time, end_time, status, created_at, updated_at)
VALUES ('aaaaaaaa-0000-0000-0000-000000000001', '22222222-2222-2222-2222-222222222222',
        'Maja Nielsen', 'maja@example.com',
        date_trunc('day', now()) + INTERVAL '1 day 9 hours',
        date_trunc('day', now()) + INTERVAL '1 day 10 hours',
        'CONFIRMED', now(), now()),
       ('aaaaaaaa-0000-0000-0000-000000000002', '33333333-3333-3333-3333-333333333333',
        'Lars Holm', 'lars@example.com',
        date_trunc('day', now()) + INTERVAL '1 day 13 hours',
        date_trunc('day', now()) + INTERVAL '1 day 15 hours',
        'CONFIRMED', now(), now()),
       ('aaaaaaaa-0000-0000-0000-000000000003', '33333333-3333-3333-3333-333333333333',
        'Sofie Berg', 'sofie@example.com',
        date_trunc('day', now()) + INTERVAL '2 days 10 hours',
        date_trunc('day', now()) + INTERVAL '2 days 11 hours',
        'CANCELLED', now(), now())
ON CONFLICT (id) DO NOTHING;
