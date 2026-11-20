-- Apply to the intended database after migrations 001 and 002; DATETIME values are UTC.
CREATE TABLE IF NOT EXISTS ticket_order (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    user_id BIGINT NOT NULL,
    activity_id BIGINT NOT NULL,
    ticket_type_id BIGINT NOT NULL,
    quantity INT NOT NULL,
    amount_cents BIGINT NOT NULL,
    status VARCHAR(20) NOT NULL,
    created_at DATETIME(3) NOT NULL,
    CONSTRAINT fk_order_activity FOREIGN KEY (activity_id) REFERENCES activity(id),
    CONSTRAINT fk_order_ticket FOREIGN KEY (ticket_type_id) REFERENCES ticket_type(id),
    CONSTRAINT chk_order_quantity CHECK (quantity = 1),
    CONSTRAINT chk_order_amount CHECK (amount_cents >= 0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
