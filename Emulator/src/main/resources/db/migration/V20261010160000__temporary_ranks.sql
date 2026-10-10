CREATE TABLE IF NOT EXISTS user_temporary_ranks (
    user_id INT NOT NULL PRIMARY KEY,
    previous_rank INT NOT NULL,
    assigned_rank INT NOT NULL,
    expires_at BIGINT NOT NULL,
    actor_id INT NOT NULL,
    reason VARCHAR(255) NOT NULL DEFAULT '',
    created_at BIGINT NOT NULL,
    INDEX idx_temporary_rank_expiry (expires_at, user_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
