CREATE TABLE IF NOT EXISTS wired_quest_definitions (
 code VARCHAR(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin NOT NULL PRIMARY KEY,
 configuration MEDIUMTEXT NOT NULL,
 updated_at INT NOT NULL DEFAULT 0
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
CREATE TABLE IF NOT EXISTS wired_quest_progress (
 user_id INT NOT NULL PRIMARY KEY,
 progress MEDIUMTEXT NOT NULL,
 updated_at INT NOT NULL DEFAULT 0,
 CONSTRAINT fk_wired_quest_user FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
-- Polaris permissions live in permission_definitions, not legacy permission columns.
-- Inherit achievement-editor access on first creation; retain existing quest grants on retry.
INSERT INTO permission_definitions
 (permission_key, max_value, comment, rank_1, rank_2, rank_3, rank_4, rank_5, rank_6, rank_7)
SELECT 'acc_quest_editor', 1, 'Allows staff to manage Wired quests and their completion rewards.',
 COALESCE(MAX(rank_1),0), COALESCE(MAX(rank_2),0), COALESCE(MAX(rank_3),0),
 COALESCE(MAX(rank_4),0), COALESCE(MAX(rank_5),0), COALESCE(MAX(rank_6),0), COALESCE(MAX(rank_7),1)
FROM permission_definitions WHERE permission_key='acc_achievement_editor'
ON DUPLICATE KEY UPDATE max_value=VALUES(max_value), comment=VALUES(comment);
UPDATE items_base SET interaction_type=item_name WHERE item_name IN ('wf_var_quest','wf_var_quest_chain') AND NOT(interaction_type <=> item_name);
