CREATE TABLE IF NOT EXISTS `users_earnings_pending` (
    `id` BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    `operation_id` VARCHAR(96) NOT NULL,
    `user_id` INT NOT NULL,
    `category` VARCHAR(32) NOT NULL,
    `currency_type` INT NOT NULL,
    `amount` INT UNSIGNED NOT NULL,
    `reason` VARCHAR(96) NOT NULL DEFAULT '',
    `created_at` TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    UNIQUE KEY `uq_users_earnings_pending_operation` (`operation_id`),
    KEY `idx_users_earnings_pending_user_category` (`user_id`, `category`),
    CONSTRAINT `fk_users_earnings_pending_user`
        FOREIGN KEY (`user_id`) REFERENCES `users` (`id`) ON DELETE CASCADE
);
