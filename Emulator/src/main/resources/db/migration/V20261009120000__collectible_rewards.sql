CREATE TABLE IF NOT EXISTS collectible_rewards (
 id INT NOT NULL AUTO_INCREMENT PRIMARY KEY,
 configuration MEDIUMTEXT NOT NULL,
 expires_at BIGINT NOT NULL,
 enabled TINYINT NOT NULL DEFAULT 1,
 created_by INT NOT NULL,
 created_at BIGINT NOT NULL,
 INDEX collectible_active (enabled, expires_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
CREATE TABLE IF NOT EXISTS collectible_profiles (
 user_id INT NOT NULL PRIMARY KEY,
 xp BIGINT NOT NULL DEFAULT 0,
 highest_xp BIGINT NOT NULL DEFAULT 0,
 CONSTRAINT fk_collectible_profile_user FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
CREATE TABLE IF NOT EXISTS collectible_claims (
 user_id INT NOT NULL,
 reward_id INT NOT NULL,
 claimed_at BIGINT NOT NULL,
 PRIMARY KEY (user_id, reward_id),
 CONSTRAINT fk_collectible_claim_user FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE,
 CONSTRAINT fk_collectible_claim_reward FOREIGN KEY (reward_id) REFERENCES collectible_rewards(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
INSERT INTO permission_definitions
 (permission_key,max_value,comment,rank_1,rank_2,rank_3,rank_4,rank_5,rank_6,rank_7)
SELECT 'acc_collectibles_editor',1,'Allows staff to create and disable collectible rewards.',
 COALESCE(MAX(rank_1),0),COALESCE(MAX(rank_2),0),COALESCE(MAX(rank_3),0),
 COALESCE(MAX(rank_4),0),COALESCE(MAX(rank_5),0),COALESCE(MAX(rank_6),0),COALESCE(MAX(rank_7),1)
FROM permission_definitions WHERE permission_key='acc_achievement_editor'
ON DUPLICATE KEY UPDATE max_value=VALUES(max_value),comment=VALUES(comment);
