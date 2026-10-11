CREATE TABLE IF NOT EXISTS user_mention_mutes (
    user_id INT(11) NOT NULL,
    muted_user_id INT(11) NOT NULL,
    PRIMARY KEY (user_id, muted_user_id),
    KEY idx_muted_sender (muted_user_id, user_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
