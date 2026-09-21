CREATE TABLE IF NOT EXISTS `room_voice_monitor_sessions` (
    `session_id` char(36) NOT NULL,
    `moderator_user_id` int(11) NOT NULL,
    `room_id` int(11) NOT NULL,
    `started_at` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `stopped_at` timestamp NULL DEFAULT NULL,
    PRIMARY KEY (`session_id`),
    KEY `idx_voice_monitor_moderator_started` (`moderator_user_id`, `started_at`),
    KEY `idx_voice_monitor_room_started` (`room_id`, `started_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

INSERT INTO `permission_definitions`
    (`permission_key`, `max_value`, `comment`, `rank_1`, `rank_2`, `rank_3`, `rank_4`, `rank_5`, `rank_6`, `rank_7`)
VALUES
    ('acc_voice_surveillance', 1, 'Allows rank 7 staff to listen to active room voice for moderation. Access is logged.', 0, 0, 0, 0, 0, 0, 1)
ON DUPLICATE KEY UPDATE
    `max_value` = VALUES(`max_value`),
    `comment` = VALUES(`comment`),
    `rank_1` = 0,
    `rank_2` = 0,
    `rank_3` = 0,
    `rank_4` = 0,
    `rank_5` = 0,
    `rank_6` = 0,
    `rank_7` = 1;

ALTER TABLE `permissions`
    ADD COLUMN IF NOT EXISTS `acc_voice_surveillance` enum('0', '1') NOT NULL DEFAULT '0';

UPDATE `permissions`
SET `acc_voice_surveillance` = '1'
WHERE `id` = 7;
