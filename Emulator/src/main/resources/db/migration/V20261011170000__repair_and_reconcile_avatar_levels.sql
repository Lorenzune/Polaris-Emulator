-- Qualify the score used for threshold lookup. The previous unqualified score
-- resolved to t.score inside the subquery and therefore always selected level 30.
-- Retained levels are derived from the durable peak, never from a falling current score.
SET @avatar_baseline_was_present = (SELECT COUNT(*) FROM information_schema.COLUMNS
 WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='users_avatar_levels' AND COLUMN_NAME='hosting_baseline_millis');
ALTER TABLE users_avatar_levels
 ADD COLUMN IF NOT EXISTS hosting_baseline_millis BIGINT UNSIGNED NOT NULL DEFAULT 0,
 ADD COLUMN IF NOT EXISTS last_reconciled_at DATETIME NOT NULL DEFAULT '1970-01-01 00:00:00',
 ADD INDEX IF NOT EXISTS avatar_reconcile (dirty, last_reconciled_at, user_id);
ALTER TABLE users_avatar_hosting_checkpoints ADD INDEX IF NOT EXISTS avatar_hosting_owner (owner_id);
UPDATE users_avatar_levels l SET l.hosting_baseline_millis = GREATEST(
 CAST(l.hosting_weighted_millis AS DECIMAL(20,0)) - COALESCE((SELECT SUM(c.weighted_millis)
 FROM users_avatar_hosting_checkpoints c WHERE c.owner_id=l.user_id),0),0)
 WHERE @avatar_baseline_was_present=0;
CREATE TABLE IF NOT EXISTS users_avatar_level_repairs (
 user_id INT PRIMARY KEY, previous_level INT NOT NULL, previous_rewarded_level INT NOT NULL,
 highest_score INT NOT NULL, corrected_level INT NOT NULL,
 repaired_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
) ENGINE=InnoDB;
CREATE TABLE IF NOT EXISTS users_avatar_deferred_rewards (
 operation_id VARCHAR(96) PRIMARY KEY, user_id INT NOT NULL, level INT NOT NULL,
 category VARCHAR(32) NOT NULL, currency_type INT NOT NULL, amount INT UNSIGNED NOT NULL,
 reason VARCHAR(96) NOT NULL, created_at TIMESTAMP NOT NULL,
 INDEX avatar_deferred_owner (user_id, level)
) ENGINE=InnoDB;
DELIMITER $$
DROP PROCEDURE IF EXISTS avatar_levels_refresh$$
CREATE PROCEDURE avatar_levels_refresh(IN avatar_user_id INT)
BEGIN
 DECLARE avatar_peak INT;
 IF avatar_user_id > 0 THEN
  INSERT INTO users_avatar_levels (user_id) VALUES (avatar_user_id) ON DUPLICATE KEY UPDATE user_id = VALUES(user_id);
  IF EXISTS (SELECT 1 FROM users_avatar_level_config WHERE id = 1 AND ready = 1) THEN
   -- DML subqueries use current reads, rather than an earlier transaction snapshot.
   UPDATE users_avatar_levels SET
    raw_achievements = LEAST(2147483647, COALESCE((
     SELECT SUM(earned.level) FROM (
      SELECT d.achievement_name, MAX(d.level) AS level
      FROM users_avatar_achievement_levels d
      JOIN users_avatar_achievement_progress a ON a.user_id = avatar_user_id AND a.achievement_name = d.achievement_name
      WHERE GREATEST(0, a.progress) + GREATEST(0, a.pending_progress) >= d.progress_needed
        AND GREATEST(0, a.progress) + GREATEST(0, a.pending_progress) > 0
      GROUP BY d.achievement_name
     ) earned), 0)),
    raw_badges = (SELECT COUNT(*) FROM users_avatar_badge_counts
      WHERE user_id = avatar_user_id AND copies > 0 AND LEFT(badge_code, 4) <> 'ACH_'),
    raw_furni = (SELECT COUNT(*) FROM users_avatar_furni_counts i JOIN items_base b ON b.id = i.item_id
      WHERE i.user_id = avatar_user_id AND i.copies > 0
       AND LOWER(b.interaction_type) NOT IN ('postit', 'post_it', 'photo')
       AND LOWER(b.item_name) NOT IN ('photo', 'camera_photo', 'post_it')
       AND i.item_id <> (SELECT camera_item_id FROM users_avatar_level_config WHERE id = 1))
   WHERE user_id = avatar_user_id;
   UPDATE users_avatar_levels SET score = LEAST(2147483647,
    CAST(raw_achievements AS UNSIGNED) + LEAST(raw_badges, raw_achievements)
    + LEAST(hosting_weighted_millis DIV 60000000000, CAST(raw_achievements AS UNSIGNED) + LEAST(raw_badges, raw_achievements))
    + LEAST(raw_furni, CAST(raw_achievements AS UNSIGNED) + LEAST(raw_badges, raw_achievements)
      + LEAST(hosting_weighted_millis DIV 60000000000, CAST(raw_achievements AS UNSIGNED) + LEAST(raw_badges, raw_achievements))))
   WHERE user_id = avatar_user_id;
   IF COALESCE(@avatar_levels_defer, 0) = 0 THEN
   SELECT GREATEST(l.highest_score, l.score) INTO avatar_peak FROM users_avatar_levels l WHERE l.user_id = avatar_user_id FOR UPDATE;
   UPDATE users_avatar_levels SET highest_score = avatar_peak,
    level = (SELECT MAX(t.level) FROM users_avatar_level_thresholds t WHERE t.score <= avatar_peak),
    pantheon_progress = GREATEST(pantheon_progress, GREATEST(highest_score, score) - 4000, 0)
   WHERE user_id = avatar_user_id;
   END IF;
  END IF;
  UPDATE users_avatar_levels SET dirty = 1, revision = revision + 1 WHERE user_id = avatar_user_id;
 END IF;
END$$
DELIMITER ;
-- Preserve the correction audit and defer only unclaimed premature avatar rewards.
-- Claimed rewards and the immutable economic ledger are deliberately preserved.
START TRANSACTION;
INSERT IGNORE INTO users_avatar_level_repairs (user_id, previous_level, previous_rewarded_level, highest_score, corrected_level)
 SELECT l.user_id, l.level, l.rewarded_level, l.highest_score,
  (SELECT MAX(t.level) FROM users_avatar_level_thresholds t WHERE t.score <= GREATEST(l.highest_score,l.score))
 FROM users_avatar_levels l
 WHERE GREATEST(l.level,l.rewarded_level) > (SELECT MAX(t.level) FROM users_avatar_level_thresholds t WHERE t.score <= GREATEST(l.highest_score,l.score));
INSERT IGNORE INTO users_avatar_deferred_rewards (operation_id,user_id,level,category,currency_type,amount,reason,created_at)
 SELECT p.operation_id,p.user_id,t.level,p.category,p.currency_type,p.amount,p.reason,p.created_at
 FROM users_earnings_pending p JOIN users_avatar_level_repairs r ON r.user_id=p.user_id
 JOIN users_avatar_level_thresholds t ON BINARY p.operation_id=BINARY CONCAT('avatar-level:',p.user_id,':',t.level)
 WHERE t.level>r.corrected_level AND p.reason='avatar.level.reward';
DELETE p FROM users_earnings_pending p JOIN users_avatar_deferred_rewards d
 ON BINARY d.operation_id=BINARY p.operation_id
 AND d.user_id=p.user_id AND d.currency_type=p.currency_type AND d.amount=p.amount
 AND BINARY d.category=BINARY p.category AND BINARY d.reason=BINARY p.reason;
UPDATE users_avatar_levels l JOIN users_avatar_level_repairs r ON r.user_id=l.user_id
 SET l.level=r.corrected_level, l.rewarded_level=LEAST(l.rewarded_level,r.corrected_level),
 l.dirty=1,l.revision=l.revision+1;
COMMIT;
