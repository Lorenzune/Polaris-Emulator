-- Habbicons Catalog, Categories, User Ownership & Command Migration

-- 1. Categories Table
CREATE TABLE IF NOT EXISTS `custom_habbicon_categories` (
    `id` int(11) NOT NULL AUTO_INCREMENT,
    `name` varchar(100) NOT NULL,
    `badge` varchar(50) DEFAULT '',
    `sort_order` int(11) DEFAULT 0,
    `enabled` tinyint(1) DEFAULT 1,
    PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

INSERT INTO `custom_habbicon_categories` (`id`, `name`, `badge`, `sort_order`, `enabled`)
VALUES
    (1, 'Custom & Speciali', '', 1, 1),
    (2, 'Animati GIF', '', 2, 1),
    (3, 'Meme & Fun', '', 3, 1),
    (4, 'VIP & Esclusivi', '', 4, 1)
ON DUPLICATE KEY UPDATE `name` = VALUES(`name`);

-- 2. Catalog Table
CREATE TABLE IF NOT EXISTS `custom_habbicons_catalog` (
    `id` int(11) NOT NULL AUTO_INCREMENT,
    `category_id` int(11) NOT NULL DEFAULT 1,
    `image_file` varchar(150) NOT NULL,
    `display_name` varchar(100) NOT NULL,
    `points` int(11) NOT NULL DEFAULT 0,
    `points_type` int(11) NOT NULL DEFAULT 0,
    `min_rank` int(11) NOT NULL DEFAULT 0,
    `is_free` tinyint(1) NOT NULL DEFAULT 0,
    `enabled` tinyint(1) NOT NULL DEFAULT 1,
    `sort_order` int(11) NOT NULL DEFAULT 0,
    PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 3. User Ownership Table (Persistence)
CREATE TABLE IF NOT EXISTS `users_habbicons` (
    `user_id` int(11) NOT NULL,
    `habbicon_id` int(11) NOT NULL,
    `created_at` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (`user_id`, `habbicon_id`),
    KEY `idx_user_habbicons_user` (`user_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 4. Command Texts
INSERT INTO `emulator_texts` (`key`, `value`) VALUES
    ('commands.description.cmd_update_habbicons', ':update_habbicons'),
    ('commands.keys.cmd_update_habbicons', 'update_habbicons;update_habbicon'),
    ('commands.succes.cmd_update_habbicons', 'Catalogo Habbicons ricaricato con successo!')
ON DUPLICATE KEY UPDATE `value` = VALUES(`value`);

-- 5. Permission Definitions (Ranks 6 & 7 enabled by default)
INSERT INTO `permission_definitions`
    (`permission_key`, `max_value`, `comment`, `rank_1`, `rank_2`, `rank_3`, `rank_4`, `rank_5`, `rank_6`, `rank_7`)
VALUES
    ('cmd_update_habbicons', 1, 'Allows reloading the habbicons catalog.', 0, 0, 0, 0, 0, 1, 1),
    ('acc_anyhabbicon', 1, 'Allows using all habbicons freely without purchasing.', 0, 0, 0, 0, 0, 1, 1)
ON DUPLICATE KEY UPDATE
    `max_value` = VALUES(`max_value`),
    `comment` = VALUES(`comment`),
    `rank_6` = 1,
    `rank_7` = 1;

-- 6. Legacy Permissions Column if present
ALTER TABLE `permissions` ADD COLUMN IF NOT EXISTS `cmd_update_habbicons` enum('0','1') NOT NULL DEFAULT '0';
ALTER TABLE `permissions` ADD COLUMN IF NOT EXISTS `acc_anyhabbicon` enum('0','1') NOT NULL DEFAULT '0';
UPDATE `permissions` SET `cmd_update_habbicons` = '1', `acc_anyhabbicon` = '1' WHERE `level` >= 6;
