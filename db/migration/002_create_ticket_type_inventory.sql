-- Execute only after inspecting the intended database and applying 001.
CREATE TABLE IF NOT EXISTS ticket_type (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    activity_id BIGINT NOT NULL,
    name VARCHAR(100) NOT NULL,
    price_cents BIGINT NOT NULL,
    status VARCHAR(20) NOT NULL,
    created_at DATETIME(3) NOT NULL,
    CONSTRAINT fk_ticket_activity FOREIGN KEY (activity_id) REFERENCES activity(id),
    CONSTRAINT chk_ticket_price CHECK (price_cents >= 0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE IF NOT EXISTS inventory (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    ticket_type_id BIGINT NOT NULL,
    total_stock INT NOT NULL,
    available_stock INT NOT NULL,
    CONSTRAINT uq_inventory_ticket UNIQUE (ticket_type_id),
    CONSTRAINT fk_inventory_ticket FOREIGN KEY (ticket_type_id) REFERENCES ticket_type(id),
    CONSTRAINT chk_inventory_total CHECK (total_stock >= 0),
    CONSTRAINT chk_inventory_available CHECK (available_stock >= 0),
    CONSTRAINT chk_inventory_limit CHECK (available_stock <= total_stock)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
