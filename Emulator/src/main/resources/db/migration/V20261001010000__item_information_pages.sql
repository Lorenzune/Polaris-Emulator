CREATE TABLE IF NOT EXISTS item_information_pages (
    item_id INT(11) NOT NULL,
    title VARCHAR(120) NOT NULL,
    content MEDIUMTEXT NOT NULL,
    updated_by INT(11) NOT NULL,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (item_id),
    CONSTRAINT fk_information_page_item FOREIGN KEY (item_id) REFERENCES items(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

INSERT INTO permission_definitions
    (permission_key, max_value, comment, rank_1, rank_2, rank_3, rank_4, rank_5, rank_6, rank_7)
VALUES ('acc_information_terminal_edit', 1, 'Allows editing information pages on any terminal in the current room.', 0, 0, 0, 0, 0, 0, 1)
ON DUPLICATE KEY UPDATE comment = VALUES(comment);
