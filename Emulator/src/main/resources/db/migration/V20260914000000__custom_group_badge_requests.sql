CREATE TABLE IF NOT EXISTS `group_custom_badge_requests` (
    `id` int unsigned NOT NULL AUTO_INCREMENT,
    `guild_id` int unsigned NOT NULL,
    `requester_id` int unsigned NOT NULL,
    `badge_code` varchar(64) NOT NULL,
    `status` tinyint unsigned NOT NULL DEFAULT 0,
    `reviewer_id` int unsigned DEFAULT NULL,
    `created_at` int unsigned NOT NULL,
    `reviewed_at` int unsigned DEFAULT NULL,
    PRIMARY KEY (`id`),
    UNIQUE KEY `uq_group_custom_badge_request_code` (`badge_code`),
    KEY `idx_group_custom_badge_request_status` (`status`, `created_at`),
    KEY `idx_group_custom_badge_request_requester` (`requester_id`, `status`),
    KEY `idx_group_custom_badge_request_guild` (`guild_id`, `status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
