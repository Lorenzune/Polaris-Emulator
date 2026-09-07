CREATE TABLE IF NOT EXISTS `users_blocked` (
  `user_id` INT NOT NULL,
  `target_id` INT NOT NULL,
  `timestamp` INT NOT NULL DEFAULT 0,
  PRIMARY KEY (`user_id`, `target_id`),
  KEY `idx_users_blocked_target` (`target_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
