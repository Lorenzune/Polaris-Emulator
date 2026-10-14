-- Component routines are separate: MariaDB pre-locks tables reachable through a
-- trigger's routines, even if a conditional branch will not execute.
-- Achievement and hosting changes must never reach items_base through that graph.
-- Preserve the full public refresh and the existing atomic trade flush boundary.
-- The editor and rare system count instances by base definition. A room/user
-- prefix cannot serve this lookup; reuse any existing item_id-leading index.
SET @polaris_item_lookup_exists = (
 SELECT COUNT(*) FROM information_schema.STATISTICS
 WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'items'
  AND COLUMN_NAME = 'item_id' AND SEQ_IN_INDEX = 1
);
SET @polaris_item_lookup_sql = IF(@polaris_item_lookup_exists > 0, 'DO 0',
 'ALTER TABLE items ADD INDEX idx_items_item_id (item_id)');
PREPARE polaris_item_lookup_stmt FROM @polaris_item_lookup_sql;
EXECUTE polaris_item_lookup_stmt;
DEALLOCATE PREPARE polaris_item_lookup_stmt;
ALTER TABLE users_avatar_furni_counts ADD INDEX IF NOT EXISTS avatar_furni_item_owner (item_id, user_id);
DELIMITER $$
DROP PROCEDURE IF EXISTS avatar_levels_publish$$
CREATE PROCEDURE avatar_levels_publish(IN avatar_user_id INT)
BEGIN
 DECLARE avatar_peak INT;
 IF avatar_user_id > 0 THEN
  CALL avatar_levels_lock(avatar_user_id, 0);
  IF COALESCE(@avatar_levels_defer, 0) = 0 AND EXISTS (SELECT 1 FROM users_avatar_level_config WHERE id = 1 AND ready = 1) THEN
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

DROP PROCEDURE IF EXISTS avatar_levels_count_achievements$$
CREATE PROCEDURE avatar_levels_count_achievements(IN avatar_user_id INT)
BEGIN
 UPDATE users_avatar_levels SET raw_achievements = LEAST(2147483647, COALESCE((
     SELECT SUM(earned.level) FROM (
      SELECT d.achievement_name, MAX(d.level) AS level
      FROM users_avatar_achievement_levels d
      JOIN users_avatar_achievement_progress a ON a.user_id = avatar_user_id AND a.achievement_name = d.achievement_name
      WHERE GREATEST(0, a.progress) + GREATEST(0, a.pending_progress) >= d.progress_needed
        AND GREATEST(0, a.progress) + GREATEST(0, a.pending_progress) > 0
      GROUP BY d.achievement_name
     ) earned), 0))
 WHERE user_id = avatar_user_id;
END$$
DROP PROCEDURE IF EXISTS avatar_levels_achievements_changed$$
CREATE PROCEDURE avatar_levels_achievements_changed(IN avatar_user_id INT)
BEGIN
 IF avatar_user_id > 0 THEN
  CALL avatar_levels_lock(avatar_user_id, 0);
  IF COALESCE(@avatar_levels_defer, 0) = 0 AND EXISTS (SELECT 1 FROM users_avatar_level_config WHERE id = 1 AND ready = 1) THEN
   CALL avatar_levels_count_achievements(avatar_user_id);
  END IF;
  CALL avatar_levels_publish(avatar_user_id);
 END IF;
END$$

DROP PROCEDURE IF EXISTS avatar_levels_count_badges$$
CREATE PROCEDURE avatar_levels_count_badges(IN avatar_user_id INT)
BEGIN
 UPDATE users_avatar_levels SET raw_badges = (SELECT COUNT(*) FROM users_avatar_badge_counts
      WHERE user_id = avatar_user_id AND copies > 0 AND LEFT(badge_code, 4) <> 'ACH_')
 WHERE user_id = avatar_user_id;
END$$
DROP PROCEDURE IF EXISTS avatar_levels_badges_changed$$
CREATE PROCEDURE avatar_levels_badges_changed(IN avatar_user_id INT)
BEGIN
 IF avatar_user_id > 0 THEN
  CALL avatar_levels_lock(avatar_user_id, 0);
  IF COALESCE(@avatar_levels_defer, 0) = 0 AND EXISTS (SELECT 1 FROM users_avatar_level_config WHERE id = 1 AND ready = 1) THEN
   CALL avatar_levels_count_badges(avatar_user_id);
  END IF;
  CALL avatar_levels_publish(avatar_user_id);
 END IF;
END$$

DROP PROCEDURE IF EXISTS avatar_levels_count_furni$$
CREATE PROCEDURE avatar_levels_count_furni(IN avatar_user_id INT)
BEGIN
 UPDATE users_avatar_levels SET raw_furni = (SELECT COUNT(*) FROM users_avatar_furni_counts i JOIN items_base b ON b.id = i.item_id
      WHERE i.user_id = avatar_user_id AND i.copies > 0
       AND LOWER(b.interaction_type) NOT IN ('postit', 'post_it', 'photo')
       AND LOWER(b.item_name) NOT IN ('photo', 'camera_photo', 'post_it')
       AND i.item_id <> (SELECT camera_item_id FROM users_avatar_level_config WHERE id = 1))
 WHERE user_id = avatar_user_id;
END$$
DROP PROCEDURE IF EXISTS avatar_levels_furni_changed$$
CREATE PROCEDURE avatar_levels_furni_changed(IN avatar_user_id INT)
BEGIN
 IF avatar_user_id > 0 THEN
  CALL avatar_levels_lock(avatar_user_id, 0);
  IF COALESCE(@avatar_levels_defer, 0) = 0 AND EXISTS (SELECT 1 FROM users_avatar_level_config WHERE id = 1 AND ready = 1) THEN
   CALL avatar_levels_count_furni(avatar_user_id);
  END IF;
  CALL avatar_levels_publish(avatar_user_id);
 END IF;
END$$

DROP PROCEDURE IF EXISTS avatar_levels_refresh$$
CREATE PROCEDURE avatar_levels_refresh(IN avatar_user_id INT)
BEGIN
 IF avatar_user_id > 0 THEN
  CALL avatar_levels_lock(avatar_user_id, 0);
  IF COALESCE(@avatar_levels_defer, 0) = 0 AND EXISTS (SELECT 1 FROM users_avatar_level_config WHERE id = 1 AND ready = 1) THEN
   CALL avatar_levels_count_achievements(avatar_user_id);
   CALL avatar_levels_count_badges(avatar_user_id);
   CALL avatar_levels_count_furni(avatar_user_id);
  END IF;
  CALL avatar_levels_publish(avatar_user_id);
 END IF;
END$$

-- Seed all caches together at startup/definition reload, before accepting grants.
-- These grouped updates avoid one database round trip per account.
DROP PROCEDURE IF EXISTS avatar_levels_initialize_components$$
CREATE PROCEDURE avatar_levels_initialize_components()
BEGIN
 UPDATE users_avatar_levels l
 LEFT JOIN (
  SELECT earned.user_id, LEAST(2147483647, SUM(earned.level)) AS total
  FROM (
   SELECT a.user_id, d.achievement_name, MAX(d.level) AS level
   FROM users_avatar_achievement_progress a
   JOIN users_avatar_achievement_levels d ON d.achievement_name = a.achievement_name
   WHERE GREATEST(0, a.progress) + GREATEST(0, a.pending_progress) >= d.progress_needed
    AND GREATEST(0, a.progress) + GREATEST(0, a.pending_progress) > 0
   GROUP BY a.user_id, d.achievement_name
  ) earned GROUP BY earned.user_id
 ) counts ON counts.user_id = l.user_id
 SET l.raw_achievements = COALESCE(counts.total, 0);
 UPDATE users_avatar_levels l
 LEFT JOIN (
  SELECT user_id, COUNT(*) AS total FROM users_avatar_badge_counts
  WHERE copies > 0 AND LEFT(badge_code, 4) <> 'ACH_' GROUP BY user_id
 ) counts ON counts.user_id = l.user_id
 SET l.raw_badges = COALESCE(counts.total, 0);
 UPDATE users_avatar_levels l
 LEFT JOIN (
  SELECT i.user_id, COUNT(*) AS total FROM users_avatar_furni_counts i JOIN items_base b ON b.id = i.item_id
  WHERE i.copies > 0
   AND LOWER(b.interaction_type) NOT IN ('postit', 'post_it', 'photo')
   AND LOWER(b.item_name) NOT IN ('photo', 'camera_photo', 'post_it')
   AND i.item_id <> (SELECT camera_item_id FROM users_avatar_level_config WHERE id = 1)
  GROUP BY i.user_id
 ) counts ON counts.user_id = l.user_id
 SET l.raw_furni = COALESCE(counts.total, 0);
END$$

DROP TRIGGER IF EXISTS avatar_users_badges_insert$$
CREATE TRIGGER avatar_users_badges_insert AFTER INSERT ON users_badges FOR EACH ROW
BEGIN
 CALL avatar_levels_lock(NEW.user_id, 0); CALL avatar_badge_change(NEW.user_id, NEW.badge_code, 1); CALL avatar_levels_badges_changed(NEW.user_id);
END$$
DROP TRIGGER IF EXISTS avatar_users_badges_delete$$
CREATE TRIGGER avatar_users_badges_delete AFTER DELETE ON users_badges FOR EACH ROW
BEGIN
 CALL avatar_levels_lock(OLD.user_id, 0); CALL avatar_badge_change(OLD.user_id, OLD.badge_code, -1); CALL avatar_levels_badges_changed(OLD.user_id);
END$$
DROP TRIGGER IF EXISTS avatar_users_badges_update$$
CREATE TRIGGER avatar_users_badges_update AFTER UPDATE ON users_badges FOR EACH ROW
BEGIN
 IF NOT (OLD.user_id <=> NEW.user_id) OR NOT (OLD.badge_code <=> NEW.badge_code) THEN CALL avatar_levels_lock(OLD.user_id, NEW.user_id); CALL avatar_badge_change(OLD.user_id, OLD.badge_code, -1); CALL avatar_badge_change(NEW.user_id, NEW.badge_code, 1); CALL avatar_levels_badges_changed(OLD.user_id); IF OLD.user_id <> NEW.user_id THEN CALL avatar_levels_badges_changed(NEW.user_id); END IF; END IF;
END$$
DROP TRIGGER IF EXISTS avatar_items_insert$$
CREATE TRIGGER avatar_items_insert AFTER INSERT ON items FOR EACH ROW
BEGIN
 CALL avatar_levels_lock(NEW.user_id, 0); IF NEW.avatar_level_excluded = 0 AND NOT EXISTS (SELECT 1 FROM builders_club_items WHERE item_id = NEW.id) THEN CALL avatar_furni_change(NEW.user_id, NEW.item_id, 1); END IF; CALL avatar_levels_furni_changed(NEW.user_id);
END$$
DROP TRIGGER IF EXISTS avatar_items_delete$$
CREATE TRIGGER avatar_items_delete AFTER DELETE ON items FOR EACH ROW
BEGIN
 CALL avatar_levels_lock(OLD.user_id, 0); IF OLD.avatar_level_excluded = 0 AND NOT EXISTS (SELECT 1 FROM builders_club_items WHERE item_id = OLD.id) THEN CALL avatar_furni_change(OLD.user_id, OLD.item_id, -1); END IF; CALL avatar_levels_furni_changed(OLD.user_id);
END$$
DROP TRIGGER IF EXISTS avatar_items_update$$
CREATE TRIGGER avatar_items_update AFTER UPDATE ON items FOR EACH ROW
BEGIN
 IF NOT (OLD.user_id <=> NEW.user_id) OR NOT (OLD.item_id <=> NEW.item_id) OR NOT (OLD.avatar_level_excluded <=> NEW.avatar_level_excluded) THEN CALL avatar_levels_lock(OLD.user_id, NEW.user_id); IF OLD.avatar_level_excluded = 0 AND NOT EXISTS (SELECT 1 FROM builders_club_items WHERE item_id = OLD.id) THEN CALL avatar_furni_change(OLD.user_id, OLD.item_id, -1); END IF; IF NEW.avatar_level_excluded = 0 AND NOT EXISTS (SELECT 1 FROM builders_club_items WHERE item_id = NEW.id) THEN CALL avatar_furni_change(NEW.user_id, NEW.item_id, 1); END IF; CALL avatar_levels_furni_changed(OLD.user_id); IF OLD.user_id <> NEW.user_id THEN CALL avatar_levels_furni_changed(NEW.user_id); END IF; END IF;
END$$
DROP TRIGGER IF EXISTS avatar_users_achievements_insert$$
CREATE TRIGGER avatar_users_achievements_insert AFTER INSERT ON users_achievements FOR EACH ROW
BEGIN
 CALL avatar_levels_lock(NEW.user_id, 0); INSERT INTO users_avatar_achievement_progress (user_id, achievement_name, progress) VALUES (NEW.user_id, NEW.achievement_name, NEW.progress) ON DUPLICATE KEY UPDATE progress = VALUES(progress); CALL avatar_levels_achievements_changed(NEW.user_id);
END$$
DROP TRIGGER IF EXISTS avatar_users_achievements_delete$$
CREATE TRIGGER avatar_users_achievements_delete AFTER DELETE ON users_achievements FOR EACH ROW
BEGIN
 CALL avatar_levels_lock(OLD.user_id, 0); UPDATE users_avatar_achievement_progress SET progress = 0 WHERE user_id = OLD.user_id AND achievement_name = OLD.achievement_name; CALL avatar_levels_achievements_changed(OLD.user_id);
END$$
DROP TRIGGER IF EXISTS avatar_users_achievements_update$$
CREATE TRIGGER avatar_users_achievements_update AFTER UPDATE ON users_achievements FOR EACH ROW
BEGIN
 IF NOT (OLD.user_id <=> NEW.user_id) OR NOT (OLD.progress <=> NEW.progress) OR NOT (OLD.achievement_name <=> NEW.achievement_name) THEN
  CALL avatar_levels_lock(OLD.user_id, NEW.user_id);
  UPDATE users_avatar_achievement_progress SET progress = 0 WHERE user_id = OLD.user_id AND achievement_name = OLD.achievement_name;
  INSERT INTO users_avatar_achievement_progress (user_id, achievement_name, progress) VALUES (NEW.user_id, NEW.achievement_name, NEW.progress) ON DUPLICATE KEY UPDATE progress = VALUES(progress);
  CALL avatar_levels_achievements_changed(OLD.user_id); IF OLD.user_id <> NEW.user_id THEN CALL avatar_levels_achievements_changed(NEW.user_id); END IF;
 END IF;
END$$
DROP TRIGGER IF EXISTS avatar_users_achievements_queue_insert$$
CREATE TRIGGER avatar_users_achievements_queue_insert AFTER INSERT ON users_achievements_queue FOR EACH ROW
BEGIN
 CALL avatar_levels_lock(NEW.user_id, 0); CALL avatar_queue_change(NEW.user_id, NEW.achievement_id, NEW.amount); CALL avatar_levels_achievements_changed(NEW.user_id);
END$$
DROP TRIGGER IF EXISTS avatar_users_achievements_queue_delete$$
CREATE TRIGGER avatar_users_achievements_queue_delete AFTER DELETE ON users_achievements_queue FOR EACH ROW
BEGIN
 CALL avatar_levels_lock(OLD.user_id, 0); CALL avatar_queue_change(OLD.user_id, OLD.achievement_id, -CAST(OLD.amount AS SIGNED)); CALL avatar_levels_achievements_changed(OLD.user_id);
END$$
DROP TRIGGER IF EXISTS avatar_users_achievements_queue_update$$
CREATE TRIGGER avatar_users_achievements_queue_update AFTER UPDATE ON users_achievements_queue FOR EACH ROW
BEGIN
 IF NOT (OLD.user_id <=> NEW.user_id) OR NOT (OLD.amount <=> NEW.amount) OR NOT (OLD.achievement_id <=> NEW.achievement_id) THEN
  CALL avatar_levels_lock(OLD.user_id, NEW.user_id);
  CALL avatar_queue_change(OLD.user_id, OLD.achievement_id, -CAST(OLD.amount AS SIGNED)); CALL avatar_queue_change(NEW.user_id, NEW.achievement_id, NEW.amount);
  CALL avatar_levels_achievements_changed(OLD.user_id); IF OLD.user_id <> NEW.user_id THEN CALL avatar_levels_achievements_changed(NEW.user_id); END IF;
 END IF;
END$$
DROP PROCEDURE IF EXISTS avatar_bc_change$$
CREATE PROCEDURE avatar_bc_change(IN avatar_instance INT, IN delta INT)
BEGIN
 DECLARE avatar_user INT;
 DECLARE avatar_base INT;
 SET avatar_user = (SELECT user_id FROM items WHERE id = avatar_instance AND avatar_level_excluded = 0);
 SET avatar_base = (SELECT item_id FROM items WHERE id = avatar_instance AND avatar_level_excluded = 0);
 IF avatar_user > 0 THEN
  CALL avatar_levels_lock(avatar_user, 0); CALL avatar_furni_change(avatar_user, avatar_base, delta); CALL avatar_levels_furni_changed(avatar_user);
 END IF;
END$$
-- Definition edits must also keep the cached furni component coherent, without
-- recalculating unrelated components or publishing a transient peak.
DROP PROCEDURE IF EXISTS avatar_levels_definition_change$$
CREATE PROCEDURE avatar_levels_definition_change(
 IN previous_item INT, IN previous_eligible INT, IN current_item INT, IN current_eligible INT)
BEGIN
 UPDATE users_avatar_levels l
 JOIN (
  SELECT c.user_id, SUM(
   IF(c.item_id = current_item, current_eligible, 0)
   - IF(c.item_id = previous_item, previous_eligible, 0)) AS delta
  FROM users_avatar_furni_counts c
  WHERE c.copies > 0 AND ((previous_item > 0 AND c.item_id = previous_item)
   OR (current_item > 0 AND c.item_id = current_item))
  GROUP BY c.user_id
 ) changes ON changes.user_id = l.user_id
 SET l.raw_furni = GREATEST(0, l.raw_furni + changes.delta),
     l.dirty = 1, l.revision = l.revision + 1
 WHERE changes.delta <> 0;
END$$
DROP TRIGGER IF EXISTS avatar_item_definition_insert$$
CREATE TRIGGER avatar_item_definition_insert AFTER INSERT ON items_base FOR EACH ROW
BEGIN
 DECLARE eligible INT;
 SET eligible = LOWER(NEW.interaction_type) NOT IN ('postit', 'post_it', 'photo')
  AND LOWER(NEW.item_name) NOT IN ('photo', 'camera_photo', 'post_it')
  AND NEW.id <> (SELECT camera_item_id FROM users_avatar_level_config WHERE id = 1);
 IF eligible THEN CALL avatar_levels_definition_change(0, 0, NEW.id, 1); END IF;
END$$
DROP TRIGGER IF EXISTS avatar_item_definition_delete$$
CREATE TRIGGER avatar_item_definition_delete AFTER DELETE ON items_base FOR EACH ROW
BEGIN
 DECLARE eligible INT;
 SET eligible = LOWER(OLD.interaction_type) NOT IN ('postit', 'post_it', 'photo')
  AND LOWER(OLD.item_name) NOT IN ('photo', 'camera_photo', 'post_it')
  AND OLD.id <> (SELECT camera_item_id FROM users_avatar_level_config WHERE id = 1);
 IF eligible THEN CALL avatar_levels_definition_change(OLD.id, 1, 0, 0); END IF;
END$$
DROP TRIGGER IF EXISTS avatar_item_definition_update$$
CREATE TRIGGER avatar_item_definition_update AFTER UPDATE ON items_base FOR EACH ROW
BEGIN
 DECLARE previous_eligible INT;
 DECLARE current_eligible INT;
 DECLARE camera_item INT;
 IF OLD.id <> NEW.id OR NOT (OLD.interaction_type <=> NEW.interaction_type)
  OR NOT (OLD.item_name <=> NEW.item_name) THEN
 SET camera_item = (SELECT camera_item_id FROM users_avatar_level_config WHERE id = 1);
 SET previous_eligible = LOWER(OLD.interaction_type) NOT IN ('postit', 'post_it', 'photo')
  AND LOWER(OLD.item_name) NOT IN ('photo', 'camera_photo', 'post_it') AND OLD.id <> camera_item;
 SET current_eligible = LOWER(NEW.interaction_type) NOT IN ('postit', 'post_it', 'photo')
  AND LOWER(NEW.item_name) NOT IN ('photo', 'camera_photo', 'post_it') AND NEW.id <> camera_item;
 IF OLD.id <> NEW.id OR previous_eligible <> current_eligible THEN
  CALL avatar_levels_definition_change(OLD.id, previous_eligible, NEW.id, current_eligible);
 END IF;
 END IF;
END$$
DELIMITER ;
START TRANSACTION;
INSERT IGNORE INTO users_avatar_levels (user_id)
 SELECT user_id FROM users_avatar_badge_counts WHERE user_id > 0
 UNION SELECT user_id FROM users_avatar_furni_counts WHERE user_id > 0
 UNION SELECT user_id FROM users_avatar_achievement_progress WHERE user_id > 0;
CALL avatar_levels_initialize_components();
UPDATE users_avatar_levels SET dirty = 1, revision = revision + 1;
COMMIT;
