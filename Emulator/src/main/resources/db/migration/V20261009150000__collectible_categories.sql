CREATE TABLE IF NOT EXISTS collectible_categories (
 id INT NOT NULL AUTO_INCREMENT PRIMARY KEY,
 title VARCHAR(100) NOT NULL,
 item_ids TEXT NOT NULL,
 reward_id INT NOT NULL UNIQUE,
 CONSTRAINT fk_collectible_category_reward FOREIGN KEY (reward_id) REFERENCES collectible_rewards(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
