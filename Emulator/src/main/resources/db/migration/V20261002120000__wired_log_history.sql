-- Permanent room history. No foreign keys: picking up a wired or deleting a room
-- must not cascade into deletion of its historical logs.
CREATE TABLE IF NOT EXISTS `wired_log_history` (
    `id` BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    `event_key` VARCHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    `room_id` INT NOT NULL,
    `occurred_at_ms` BIGINT NOT NULL,
    `log_type` VARCHAR(32) NOT NULL,
    `level` VARCHAR(16) NOT NULL,
    `message` LONGTEXT NOT NULL,
    `source_label` TEXT NOT NULL,
    `source_id` INT NOT NULL,
    PRIMARY KEY (`id`),
    UNIQUE KEY `uq_wired_log_event` (`event_key`),
    KEY `idx_wired_log_room_time` (`room_id`, `occurred_at_ms`, `id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
