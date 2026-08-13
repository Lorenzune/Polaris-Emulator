-- Persistent deduplication for actions that must count distinct user choices.
CREATE TABLE IF NOT EXISTS `users_achievement_unique_events` (
    `user_id` INT NOT NULL,
    `achievement_name` VARCHAR(64) NOT NULL,
    `event_key` VARCHAR(128) NOT NULL,
    `created_at` TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (`user_id`, `achievement_name`, `event_key`),
    KEY `achievement_name` (`achievement_name`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- The official gamedata contains DailyHotelPresence with an empty level list.
-- Database levels are used by AchievementManager as an explicit emulator-side
-- fallback, making one progress point per distinct login day obtainable.
INSERT INTO `achievements`
    (`name`, `category`, `level`, `reward_amount`, `reward_type`, `points`, `progress_needed`)
VALUES
    ('DailyHotelPresence', 'identity', 1, 0, 0, 5, 1),
    ('DailyHotelPresence', 'identity', 2, 0, 0, 10, 3),
    ('DailyHotelPresence', 'identity', 3, 0, 0, 15, 7),
    ('DailyHotelPresence', 'identity', 4, 0, 0, 20, 14),
    ('DailyHotelPresence', 'identity', 5, 0, 0, 25, 30),
    ('DailyHotelPresence', 'identity', 6, 0, 0, 30, 60),
    ('DailyHotelPresence', 'identity', 7, 0, 0, 35, 120),
    ('DailyHotelPresence', 'identity', 8, 0, 0, 40, 240),
    ('DailyHotelPresence', 'identity', 9, 0, 0, 45, 365),
    ('DailyHotelPresence', 'identity', 10, 0, 0, 50, 720)
ON DUPLICATE KEY UPDATE
    `category` = VALUES(`category`),
    `reward_amount` = VALUES(`reward_amount`),
    `reward_type` = VALUES(`reward_type`),
    `points` = VALUES(`points`),
    `progress_needed` = VALUES(`progress_needed`);
