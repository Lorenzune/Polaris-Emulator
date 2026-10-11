CREATE TABLE IF NOT EXISTS users_avatar_levels (
    user_id INT NOT NULL PRIMARY KEY,
    level INT NOT NULL DEFAULT 1,
    highest_score INT NOT NULL DEFAULT 0,
    rewarded_level INT NOT NULL DEFAULT 1,
    hosting_weighted_millis BIGINT UNSIGNED NOT NULL DEFAULT 0
);

-- Preserve the old Flash-score based Pantheon counter before changing its rule.
CREATE TABLE IF NOT EXISTS users_pantheon_legacy_progress (
    user_id INT NOT NULL PRIMARY KEY,
    achievement_name VARCHAR(255) NOT NULL,
    progress INT NOT NULL,
    queued_progress INT NOT NULL DEFAULT 0
);
INSERT IGNORE INTO users_pantheon_legacy_progress (user_id, achievement_name, progress)
SELECT user_id, achievement_name, progress FROM users_achievements
WHERE achievement_name = 'Profile_Pantheon_';
UPDATE users_achievements SET progress = 0 WHERE achievement_name = 'Profile_Pantheon_';
-- 285 is the native Pantheon definition id used by the runtime gamedata.
INSERT INTO users_pantheon_legacy_progress (user_id, achievement_name, progress, queued_progress)
SELECT user_id, 'Profile_Pantheon_', 0, SUM(amount) FROM users_achievements_queue
WHERE achievement_id = 285 OR achievement_id IN (SELECT id FROM achievements WHERE name = 'Profile_Pantheon_')
GROUP BY user_id
ON DUPLICATE KEY UPDATE queued_progress = VALUES(queued_progress);
DELETE FROM users_achievements_queue
WHERE achievement_id = 285 OR achievement_id IN (SELECT id FROM achievements WHERE name = 'Profile_Pantheon_');
