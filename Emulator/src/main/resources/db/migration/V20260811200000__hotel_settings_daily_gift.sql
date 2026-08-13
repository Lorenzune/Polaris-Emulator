CREATE TABLE IF NOT EXISTS `hotel_settings` (
    `key` VARCHAR(100) NOT NULL,
    `value` VARCHAR(255) NOT NULL,
    `comment` VARCHAR(255) NOT NULL DEFAULT '',
    PRIMARY KEY (`key`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

INSERT IGNORE INTO `hotel_settings` (`key`, `value`, `comment`) VALUES
    ('daily_gift.credits', '0', 'Crediti assegnati al primo login giornaliero'),
    ('daily_gift.duckets', '0', 'Duckets assegnati al primo login giornaliero'),
    ('daily_gift.diamonds', '0', 'Diamanti assegnati al primo login giornaliero');

CREATE TABLE IF NOT EXISTS `users_daily_gift_assignments` (
    `user_id` INT NOT NULL,
    `gift_date` DATE NOT NULL,
    `credits` INT UNSIGNED NOT NULL DEFAULT 0,
    `duckets` INT UNSIGNED NOT NULL DEFAULT 0,
    `diamonds` INT UNSIGNED NOT NULL DEFAULT 0,
    `assigned_at` TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (`user_id`, `gift_date`),
    CONSTRAINT `fk_users_daily_gift_assignments_user`
        FOREIGN KEY (`user_id`) REFERENCES `users` (`id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
