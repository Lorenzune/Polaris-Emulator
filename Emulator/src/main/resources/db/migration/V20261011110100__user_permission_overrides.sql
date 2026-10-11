CREATE TABLE IF NOT EXISTS user_permission_overrides (
    user_id INT NOT NULL,
    permission_key VARCHAR(64) NOT NULL,
    value TINYINT UNSIGNED NOT NULL,
    expires_at BIGINT NOT NULL DEFAULT 0 COMMENT 'Unix milliseconds; 0 permanent',
    reason VARCHAR(255) NOT NULL DEFAULT '',
    created_by INT NOT NULL,
    created_by_name VARCHAR(64) NOT NULL DEFAULT '',
    created_at BIGINT NOT NULL,
    PRIMARY KEY (user_id, permission_key),
    KEY idx_permission_override_expiry (expires_at, user_id),
    CONSTRAINT chk_permission_override_value CHECK (value IN (0, 1, 2)),
    CONSTRAINT chk_permission_override_expiry CHECK (expires_at >= 0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- The upstream implementation stored Unix seconds in INT columns. Widen first,
-- then convert second-sized timestamps; modern millisecond timestamps stay intact.
ALTER TABLE user_permission_overrides MODIFY expires_at BIGINT NOT NULL DEFAULT 0,
    MODIFY created_at BIGINT NOT NULL DEFAULT 0;
UPDATE user_permission_overrides SET expires_at = expires_at * 1000
WHERE expires_at > 0 AND expires_at < 10000000000;
UPDATE user_permission_overrides SET created_at = created_at * 1000
WHERE created_at > 0 AND created_at < 10000000000;
ALTER TABLE user_permission_overrides ADD INDEX IF NOT EXISTS idx_permission_override_expiry (expires_at);
