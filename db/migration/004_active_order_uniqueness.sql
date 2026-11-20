-- Preflight required: inspect existing unique indexes, invalid statuses and duplicate active orders.
-- MySQL DDL commits implicitly; never apply over conflicting existing data.
ALTER TABLE ticket_order
    ADD COLUMN active_order_marker TINYINT GENERATED ALWAYS AS
        (CASE WHEN status IN ('PENDING', 'CONFIRMED') THEN 1 ELSE NULL END) VIRTUAL,
    ADD CONSTRAINT chk_order_status CHECK (BINARY status IN ('PENDING', 'CONFIRMED', 'CANCELLED')),
    ADD UNIQUE KEY uq_active_order (user_id, activity_id, active_order_marker);
