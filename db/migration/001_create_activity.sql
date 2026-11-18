-- Execute only against the intended pulsepass database. DATETIME values represent UTC.
CREATE TABLE IF NOT EXISTS activity (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    name VARCHAR(150) NOT NULL,
    description TEXT,
    sale_start_time DATETIME(3) NOT NULL,
    sale_end_time DATETIME(3) NOT NULL,
    event_start_time DATETIME(3) NOT NULL,
    status VARCHAR(20) NOT NULL,
    created_at DATETIME(3) NOT NULL
) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;
