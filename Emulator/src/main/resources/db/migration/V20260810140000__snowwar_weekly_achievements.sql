-- Persistent weekly SnowStorm standings used by SnowWarWeeklyBest and
-- SnowWarWeeklyTopTen. A week starts on Monday in hotel.timezone.
CREATE TABLE IF NOT EXISTS `snowwar_weekly_scores` (
    `user_id` INT NOT NULL,
    `week_start` DATE NOT NULL,
    `score` INT NOT NULL DEFAULT 0,
    PRIMARY KEY (`user_id`, `week_start`),
    KEY `idx_snowwar_weekly_scores_week_score` (`week_start`, `score`, `user_id`)
);

CREATE TABLE IF NOT EXISTS `snowwar_weekly_awards` (
    `user_id` INT NOT NULL,
    `week_start` DATE NOT NULL,
    `award_type` VARCHAR(16) NOT NULL,
    PRIMARY KEY (`user_id`, `week_start`, `award_type`),
    KEY `idx_snowwar_weekly_awards_week` (`week_start`)
);
