-- Custom Bubble Category and category_id in chat_bubbles
CREATE TABLE IF NOT EXISTS `custom_bubble_category` (
    `id` int(11) NOT NULL AUTO_INCREMENT,
    `name` varchar(100) NOT NULL,
    `badge` varchar(50) DEFAULT '',
    `sort_order` int(11) NOT NULL DEFAULT 0,
    `enabled` tinyint(1) NOT NULL DEFAULT 1,
    PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

ALTER TABLE `chat_bubbles`
    ADD COLUMN IF NOT EXISTS `category_id` int(11) NOT NULL DEFAULT 0 AFTER `name`;

INSERT INTO `custom_bubble_category` (`id`, `name`, `badge`, `sort_order`, `enabled`)
VALUES
    (1, 'Base / Classici', '', 1, 1),
    (2, 'Colorati & Gradienti', '', 2, 1),
    (3, 'Animati & Speciali', '', 3, 1),
    (4, 'Animali & Natura', '', 4, 1),
    (5, 'Eventi & Festività', '', 5, 1),
    (6, 'VIP & Staff', '', 6, 1)
ON DUPLICATE KEY UPDATE `name` = VALUES(`name`);
