-- Counts and their source mutations must share transaction and rollback guarantees.
SET @avatar_engine := (SELECT ENGINE FROM information_schema.TABLES WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='items');
SET @avatar_ddl := IF(@avatar_engine <> 'InnoDB', 'ALTER TABLE `items` ENGINE=InnoDB ROW_FORMAT=DYNAMIC', 'DO 0');
PREPARE avatar_engine_stmt FROM @avatar_ddl;
EXECUTE avatar_engine_stmt;
DEALLOCATE PREPARE avatar_engine_stmt;
SET @avatar_engine := (SELECT ENGINE FROM information_schema.TABLES WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='users_badges');
SET @avatar_ddl := IF(@avatar_engine <> 'InnoDB', 'ALTER TABLE `users_badges` ENGINE=InnoDB ROW_FORMAT=DYNAMIC', 'DO 0');
PREPARE avatar_engine_stmt FROM @avatar_ddl;
EXECUTE avatar_engine_stmt;
DEALLOCATE PREPARE avatar_engine_stmt;
SET @avatar_engine := (SELECT ENGINE FROM information_schema.TABLES WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='users_achievements');
SET @avatar_ddl := IF(@avatar_engine <> 'InnoDB', 'ALTER TABLE `users_achievements` ENGINE=InnoDB ROW_FORMAT=DYNAMIC', 'DO 0');
PREPARE avatar_engine_stmt FROM @avatar_ddl;
EXECUTE avatar_engine_stmt;
DEALLOCATE PREPARE avatar_engine_stmt;
SET @avatar_engine := (SELECT ENGINE FROM information_schema.TABLES WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='users_achievements_queue');
SET @avatar_ddl := IF(@avatar_engine <> 'InnoDB', 'ALTER TABLE `users_achievements_queue` ENGINE=InnoDB ROW_FORMAT=DYNAMIC', 'DO 0');
PREPARE avatar_engine_stmt FROM @avatar_ddl;
EXECUTE avatar_engine_stmt;
DEALLOCATE PREPARE avatar_engine_stmt;
SET @avatar_engine := (SELECT ENGINE FROM information_schema.TABLES WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='builders_club_items');
SET @avatar_ddl := IF(@avatar_engine <> 'InnoDB', 'ALTER TABLE `builders_club_items` ENGINE=InnoDB ROW_FORMAT=DYNAMIC', 'DO 0');
PREPARE avatar_engine_stmt FROM @avatar_ddl;
EXECUTE avatar_engine_stmt;
DEALLOCATE PREPARE avatar_engine_stmt;
SET @avatar_engine := (SELECT ENGINE FROM information_schema.TABLES WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='users_avatar_levels');
SET @avatar_ddl := IF(@avatar_engine <> 'InnoDB', 'ALTER TABLE `users_avatar_levels` ENGINE=InnoDB ROW_FORMAT=DYNAMIC', 'DO 0');
PREPARE avatar_engine_stmt FROM @avatar_ddl;
EXECUTE avatar_engine_stmt;
DEALLOCATE PREPARE avatar_engine_stmt;
-- All ownership/progress changes update the durable avatar state in the same transaction.
-- These triggers also cover RCON, housekeeping and offline grants from existing integrations.
ALTER TABLE items ADD COLUMN avatar_level_excluded TINYINT NOT NULL DEFAULT 0;
UPDATE items i JOIN builders_club_items bc ON bc.item_id = i.id SET i.avatar_level_excluded = 1;
ALTER TABLE users_avatar_levels
 ADD COLUMN raw_achievements INT NOT NULL DEFAULT 0,
 ADD COLUMN raw_badges INT NOT NULL DEFAULT 0,
 ADD COLUMN raw_furni INT NOT NULL DEFAULT 0,
 ADD COLUMN score INT NOT NULL DEFAULT 0,
 ADD COLUMN pantheon_progress INT NOT NULL DEFAULT 0,
 ADD COLUMN revision BIGINT NOT NULL DEFAULT 0,
 ADD COLUMN dirty TINYINT NOT NULL DEFAULT 1,
 ADD INDEX avatar_dirty (dirty, user_id);
CREATE TABLE users_avatar_achievement_levels (
 achievement_name VARCHAR(255) NOT NULL,
 achievement_id INT NOT NULL,
 level INT NOT NULL,
 progress_needed INT NOT NULL,
 PRIMARY KEY (achievement_name, level),
 INDEX avatar_achievement_id (achievement_id)
) ENGINE=InnoDB;
CREATE TABLE users_avatar_level_thresholds (level INT PRIMARY KEY, score INT NOT NULL) ENGINE=InnoDB;
INSERT INTO users_avatar_level_thresholds (level, score) VALUES
(1, 0),
(2, 30),
(3, 35),
(4, 45),
(5, 60),
(6, 75),
(7, 95),
(8, 115),
(9, 140),
(10, 165),
(11, 190),
(12, 215),
(13, 240),
(14, 270),
(15, 300),
(16, 340),
(17, 390),
(18, 450),
(19, 520),
(20, 600),
(21, 700),
(22, 800),
(23, 900),
(24, 1100),
(25, 1300),
(26, 1600),
(27, 2000),
(28, 2500),
(29, 3200),
(30, 4000);
CREATE TABLE users_avatar_level_config (id INT PRIMARY KEY, camera_item_id INT NOT NULL DEFAULT 0, ready TINYINT NOT NULL DEFAULT 0) ENGINE=InnoDB;
INSERT INTO users_avatar_level_config (id) VALUES (1);
CREATE TABLE users_avatar_hosting_checkpoints (
 session_id VARCHAR(36) PRIMARY KEY,
 owner_id INT NOT NULL,
 weighted_millis BIGINT UNSIGNED NOT NULL DEFAULT 0
) ENGINE=InnoDB;
-- Transactional reference counts avoid reading source rows locked by concurrent grants.
CREATE TABLE users_avatar_badge_counts (
 user_id INT NOT NULL, badge_code VARCHAR(255) NOT NULL, copies INT NOT NULL DEFAULT 0,
 PRIMARY KEY (user_id, badge_code)
) ENGINE=InnoDB;
CREATE TABLE users_avatar_furni_counts (
 user_id INT NOT NULL, item_id INT NOT NULL, copies INT NOT NULL DEFAULT 0,
 PRIMARY KEY (user_id, item_id)
) ENGINE=InnoDB;
CREATE TABLE users_avatar_achievement_progress (
 user_id INT NOT NULL, achievement_name VARCHAR(255) NOT NULL,
 progress INT NOT NULL DEFAULT 0, pending_progress BIGINT NOT NULL DEFAULT 0,
 PRIMARY KEY (user_id, achievement_name)
) ENGINE=InnoDB;
INSERT INTO users_avatar_badge_counts (user_id, badge_code, copies)
 SELECT user_id, UPPER(badge_code), COUNT(*) FROM users_badges GROUP BY user_id, UPPER(badge_code);
INSERT INTO users_avatar_furni_counts (user_id, item_id, copies)
 SELECT i.user_id, i.item_id, COUNT(*) FROM items i
 LEFT JOIN builders_club_items bc ON bc.item_id = i.id
 WHERE i.avatar_level_excluded = 0 AND bc.item_id IS NULL GROUP BY i.user_id, i.item_id;
INSERT INTO users_avatar_achievement_progress (user_id, achievement_name, progress)
 SELECT user_id, achievement_name, MAX(progress) FROM users_achievements
 GROUP BY user_id, achievement_name;
DELIMITER $$
CREATE PROCEDURE avatar_levels_lock(IN first_user INT, IN second_user INT)
BEGIN
 -- Every reference change takes the user lock before touching counter rows.
 -- Sorting both owners also prevents opposite-direction transfers from inverting locks.
 IF LEAST(first_user, second_user) > 0 THEN
  INSERT INTO users_avatar_levels (user_id) VALUES (LEAST(first_user, second_user)) ON DUPLICATE KEY UPDATE user_id = VALUES(user_id);
 END IF;
 IF GREATEST(first_user, second_user) > 0 THEN
  INSERT INTO users_avatar_levels (user_id) VALUES (GREATEST(first_user, second_user)) ON DUPLICATE KEY UPDATE user_id = VALUES(user_id);
 END IF;
END$$
CREATE PROCEDURE avatar_levels_refresh(IN avatar_user_id INT)
BEGIN
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
   UPDATE users_avatar_levels SET highest_score = GREATEST(highest_score, score),
    level = GREATEST(level, (SELECT MAX(t.level) FROM users_avatar_level_thresholds t WHERE t.score <= score)),
    pantheon_progress = GREATEST(pantheon_progress, GREATEST(highest_score, score) - 4000, 0)
   WHERE user_id = avatar_user_id;
   END IF;
  END IF;
  UPDATE users_avatar_levels SET dirty = 1, revision = revision + 1 WHERE user_id = avatar_user_id;
 END IF;
END$$
CREATE PROCEDURE avatar_badge_change(IN avatar_user INT, IN avatar_code VARCHAR(255), IN delta INT)
BEGIN
 IF avatar_user > 0 THEN
  INSERT INTO users_avatar_badge_counts (user_id, badge_code, copies) VALUES (avatar_user, UPPER(avatar_code), GREATEST(0, delta))
  ON DUPLICATE KEY UPDATE copies = GREATEST(0, copies + delta);
 END IF;
END$$
CREATE PROCEDURE avatar_furni_change(IN avatar_user INT, IN avatar_item INT, IN delta INT)
BEGIN
 IF avatar_user > 0 THEN
  INSERT INTO users_avatar_furni_counts (user_id, item_id, copies) VALUES (avatar_user, avatar_item, GREATEST(0, delta))
  ON DUPLICATE KEY UPDATE copies = GREATEST(0, copies + delta);
 END IF;
END$$
CREATE PROCEDURE avatar_queue_change(IN avatar_user INT, IN avatar_achievement INT, IN delta BIGINT)
BEGIN
 DECLARE avatar_name VARCHAR(255);
 SET avatar_name = (SELECT achievement_name FROM users_avatar_achievement_levels WHERE achievement_id = avatar_achievement LIMIT 1);
 IF avatar_user > 0 AND avatar_name IS NOT NULL THEN
  INSERT INTO users_avatar_achievement_progress (user_id, achievement_name, pending_progress)
  VALUES (avatar_user, avatar_name, GREATEST(0, delta))
  ON DUPLICATE KEY UPDATE pending_progress = GREATEST(0, pending_progress + delta);
 END IF;
END$$
CREATE TRIGGER avatar_users_badges_insert AFTER INSERT ON users_badges FOR EACH ROW
BEGIN
 CALL avatar_levels_lock(NEW.user_id, 0); CALL avatar_badge_change(NEW.user_id, NEW.badge_code, 1); CALL avatar_levels_refresh(NEW.user_id);
END$$
CREATE TRIGGER avatar_users_badges_delete AFTER DELETE ON users_badges FOR EACH ROW
BEGIN
 CALL avatar_levels_lock(OLD.user_id, 0); CALL avatar_badge_change(OLD.user_id, OLD.badge_code, -1); CALL avatar_levels_refresh(OLD.user_id);
END$$
CREATE TRIGGER avatar_users_badges_update AFTER UPDATE ON users_badges FOR EACH ROW
BEGIN
 IF NOT (OLD.user_id <=> NEW.user_id) OR NOT (OLD.badge_code <=> NEW.badge_code) THEN CALL avatar_levels_lock(OLD.user_id, NEW.user_id); CALL avatar_badge_change(OLD.user_id, OLD.badge_code, -1); CALL avatar_badge_change(NEW.user_id, NEW.badge_code, 1); CALL avatar_levels_refresh(OLD.user_id); IF OLD.user_id <> NEW.user_id THEN CALL avatar_levels_refresh(NEW.user_id); END IF; END IF;
END$$
CREATE TRIGGER avatar_items_insert AFTER INSERT ON items FOR EACH ROW
BEGIN
 CALL avatar_levels_lock(NEW.user_id, 0); IF NEW.avatar_level_excluded = 0 AND NOT EXISTS (SELECT 1 FROM builders_club_items WHERE item_id = NEW.id) THEN CALL avatar_furni_change(NEW.user_id, NEW.item_id, 1); END IF; CALL avatar_levels_refresh(NEW.user_id);
END$$
CREATE TRIGGER avatar_items_delete AFTER DELETE ON items FOR EACH ROW
BEGIN
 CALL avatar_levels_lock(OLD.user_id, 0); IF OLD.avatar_level_excluded = 0 AND NOT EXISTS (SELECT 1 FROM builders_club_items WHERE item_id = OLD.id) THEN CALL avatar_furni_change(OLD.user_id, OLD.item_id, -1); END IF; CALL avatar_levels_refresh(OLD.user_id);
END$$
CREATE TRIGGER avatar_items_update AFTER UPDATE ON items FOR EACH ROW
BEGIN
 IF NOT (OLD.user_id <=> NEW.user_id) OR NOT (OLD.item_id <=> NEW.item_id) OR NOT (OLD.avatar_level_excluded <=> NEW.avatar_level_excluded) THEN CALL avatar_levels_lock(OLD.user_id, NEW.user_id); IF OLD.avatar_level_excluded = 0 AND NOT EXISTS (SELECT 1 FROM builders_club_items WHERE item_id = OLD.id) THEN CALL avatar_furni_change(OLD.user_id, OLD.item_id, -1); END IF; IF NEW.avatar_level_excluded = 0 AND NOT EXISTS (SELECT 1 FROM builders_club_items WHERE item_id = NEW.id) THEN CALL avatar_furni_change(NEW.user_id, NEW.item_id, 1); END IF; CALL avatar_levels_refresh(OLD.user_id); IF OLD.user_id <> NEW.user_id THEN CALL avatar_levels_refresh(NEW.user_id); END IF; END IF;
END$$
CREATE TRIGGER avatar_users_achievements_insert AFTER INSERT ON users_achievements FOR EACH ROW
BEGIN
 CALL avatar_levels_lock(NEW.user_id, 0); INSERT INTO users_avatar_achievement_progress (user_id, achievement_name, progress) VALUES (NEW.user_id, NEW.achievement_name, NEW.progress) ON DUPLICATE KEY UPDATE progress = VALUES(progress); CALL avatar_levels_refresh(NEW.user_id);
END$$
CREATE TRIGGER avatar_users_achievements_delete AFTER DELETE ON users_achievements FOR EACH ROW
BEGIN
 CALL avatar_levels_lock(OLD.user_id, 0); UPDATE users_avatar_achievement_progress SET progress = 0 WHERE user_id = OLD.user_id AND achievement_name = OLD.achievement_name; CALL avatar_levels_refresh(OLD.user_id);
END$$
CREATE TRIGGER avatar_users_achievements_update AFTER UPDATE ON users_achievements FOR EACH ROW
BEGIN
 IF NOT (OLD.user_id <=> NEW.user_id) OR NOT (OLD.progress <=> NEW.progress) OR NOT (OLD.achievement_name <=> NEW.achievement_name) THEN
  CALL avatar_levels_lock(OLD.user_id, NEW.user_id);
  UPDATE users_avatar_achievement_progress SET progress = 0 WHERE user_id = OLD.user_id AND achievement_name = OLD.achievement_name;
  INSERT INTO users_avatar_achievement_progress (user_id, achievement_name, progress) VALUES (NEW.user_id, NEW.achievement_name, NEW.progress) ON DUPLICATE KEY UPDATE progress = VALUES(progress);
  CALL avatar_levels_refresh(OLD.user_id); IF OLD.user_id <> NEW.user_id THEN CALL avatar_levels_refresh(NEW.user_id); END IF;
 END IF;
END$$
CREATE TRIGGER avatar_users_achievements_queue_insert AFTER INSERT ON users_achievements_queue FOR EACH ROW
BEGIN
 CALL avatar_levels_lock(NEW.user_id, 0); CALL avatar_queue_change(NEW.user_id, NEW.achievement_id, NEW.amount); CALL avatar_levels_refresh(NEW.user_id);
END$$
CREATE TRIGGER avatar_users_achievements_queue_delete AFTER DELETE ON users_achievements_queue FOR EACH ROW
BEGIN
 CALL avatar_levels_lock(OLD.user_id, 0); CALL avatar_queue_change(OLD.user_id, OLD.achievement_id, -CAST(OLD.amount AS SIGNED)); CALL avatar_levels_refresh(OLD.user_id);
END$$
CREATE TRIGGER avatar_users_achievements_queue_update AFTER UPDATE ON users_achievements_queue FOR EACH ROW
BEGIN
 IF NOT (OLD.user_id <=> NEW.user_id) OR NOT (OLD.amount <=> NEW.amount) OR NOT (OLD.achievement_id <=> NEW.achievement_id) THEN
  CALL avatar_levels_lock(OLD.user_id, NEW.user_id);
  CALL avatar_queue_change(OLD.user_id, OLD.achievement_id, -CAST(OLD.amount AS SIGNED)); CALL avatar_queue_change(NEW.user_id, NEW.achievement_id, NEW.amount);
  CALL avatar_levels_refresh(OLD.user_id); IF OLD.user_id <> NEW.user_id THEN CALL avatar_levels_refresh(NEW.user_id); END IF;
 END IF;
END$$
CREATE PROCEDURE avatar_bc_change(IN avatar_instance INT, IN delta INT)
BEGIN
 DECLARE avatar_user INT;
 DECLARE avatar_base INT;
 SET avatar_user = (SELECT user_id FROM items WHERE id = avatar_instance AND avatar_level_excluded = 0);
 SET avatar_base = (SELECT item_id FROM items WHERE id = avatar_instance AND avatar_level_excluded = 0);
 IF avatar_user > 0 THEN
  CALL avatar_levels_lock(avatar_user, 0); CALL avatar_furni_change(avatar_user, avatar_base, delta); CALL avatar_levels_refresh(avatar_user);
 END IF;
END$$
CREATE TRIGGER avatar_bc_insert AFTER INSERT ON builders_club_items FOR EACH ROW
BEGIN
 CALL avatar_bc_change(NEW.item_id, -1);
END$$
CREATE TRIGGER avatar_bc_delete AFTER DELETE ON builders_club_items FOR EACH ROW
BEGIN
 CALL avatar_bc_change(OLD.item_id, 1);
END$$
CREATE TRIGGER avatar_bc_update AFTER UPDATE ON builders_club_items FOR EACH ROW
BEGIN
 IF OLD.item_id <> NEW.item_id THEN CALL avatar_bc_change(OLD.item_id, 1); CALL avatar_bc_change(NEW.item_id, -1); END IF;
END$$
DELIMITER ;
