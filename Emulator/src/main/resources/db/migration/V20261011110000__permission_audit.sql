CREATE TABLE IF NOT EXISTS permission_audit (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    `timestamp` BIGINT NOT NULL,
    actor_id INT NOT NULL DEFAULT 0,
    actor_name VARCHAR(64) NOT NULL DEFAULT '',
    action VARCHAR(32) NOT NULL,
    target_type VARCHAR(16) NOT NULL,
    target_id INT NOT NULL,
    subject VARCHAR(64) NOT NULL DEFAULT '',
    old_value VARCHAR(255) NOT NULL DEFAULT '',
    new_value VARCHAR(255) NOT NULL DEFAULT '',
    via VARCHAR(64) NOT NULL DEFAULT '',
    reason VARCHAR(255) NOT NULL DEFAULT '',
    KEY idx_permission_audit_target (target_type, target_id, id),
    KEY idx_permission_audit_time (`timestamp`, id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
ALTER TABLE permission_audit ADD COLUMN IF NOT EXISTS reason VARCHAR(255) NOT NULL DEFAULT '';
ALTER TABLE permission_audit MODIFY old_value VARCHAR(255) NOT NULL DEFAULT '', MODIFY new_value VARCHAR(255) NOT NULL DEFAULT '';
