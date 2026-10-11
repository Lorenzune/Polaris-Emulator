-- Legacy schemas allowed several rows for one user's achievement. An additive
-- upsert then inserted another row, and login/recounts could read an older tier.
-- Keep an audit of the original maximum before repairing the natural key.
CREATE TABLE IF NOT EXISTS users_achievement_progress_repairs (
    user_id INT NOT NULL,
    achievement_name VARCHAR(255) NOT NULL,
    progress INT NOT NULL,
    source_rows BIGINT NOT NULL,
    repaired_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (user_id, achievement_name)
) ENGINE=InnoDB;


DELIMITER $$
CREATE PROCEDURE avatar_levels_repair_achievements()
BEGIN
    DECLARE previous_defer INT DEFAULT 0;
    DECLARE unique_keys INT DEFAULT 0;
    DECLARE EXIT HANDLER FOR SQLEXCEPTION
    BEGIN
        ROLLBACK;
        SET @avatar_levels_defer = previous_defer;
        RESIGNAL;
    END;
    SET previous_defer = COALESCE(@avatar_levels_defer, 0);
    -- Source and projections are transactional. Suppress intermediate scores
    -- while replacing duplicates so the repair cannot award a temporary peak.
    SET @avatar_levels_defer = 1;
    START TRANSACTION;
    -- Lock original rows before taking the audit snapshot. A concurrent grant
    -- must not be committed between the snapshot and replacement of its row.
    UPDATE users_achievements SET progress = progress ORDER BY user_id, achievement_name;
    INSERT INTO users_achievement_progress_repairs (user_id, achievement_name, progress, source_rows)
    SELECT user_id, achievement_name, MAX(progress), COUNT(*)
    FROM users_achievements GROUP BY user_id, achievement_name
    ON DUPLICATE KEY UPDATE progress = GREATEST(progress, VALUES(progress)),
        source_rows = GREATEST(source_rows, VALUES(source_rows));
    DELETE a FROM users_achievements a
    JOIN users_achievement_progress_repairs r
        ON r.user_id = a.user_id AND r.achievement_name = a.achievement_name
    WHERE r.source_rows > 1;
    INSERT INTO users_achievements (user_id, achievement_name, progress)
    SELECT user_id, achievement_name, progress
    FROM users_achievement_progress_repairs WHERE source_rows > 1;
    COMMIT;
    SET @avatar_levels_defer = previous_defer;

    SELECT COUNT(*) INTO unique_keys FROM (
        SELECT INDEX_NAME FROM information_schema.STATISTICS
        WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'users_achievements'
            AND NON_UNIQUE = 0
        GROUP BY INDEX_NAME
        HAVING COUNT(*) = 2 AND SUM(SUB_PART IS NOT NULL) = 0
            AND SUM(COLUMN_NAME IN ('user_id', 'achievement_name')) = 2
    ) natural_keys;
    IF unique_keys = 0 THEN
        ALTER TABLE users_achievements
            ADD UNIQUE KEY uq_users_achievement_progress (user_id, achievement_name);
    END IF;
    -- Re-read every source on the next action/login or dirty worker pass.
    UPDATE users_avatar_levels SET dirty = 1, revision = revision + 1;
END$$
DELIMITER ;
CALL avatar_levels_repair_achievements();
DROP PROCEDURE avatar_levels_repair_achievements;
