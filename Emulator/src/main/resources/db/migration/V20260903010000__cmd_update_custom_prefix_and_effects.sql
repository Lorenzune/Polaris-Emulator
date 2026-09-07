-- :update_custom_prefix command, categories & prefix effect catalog configuration

-- 1. Command texts for :update_custom_prefix
INSERT INTO `emulator_texts` (`key`, `value`) VALUES
    ('commands.description.cmd_update_custom_prefix', ':update_custom_prefix'),
    ('commands.keys.cmd_update_custom_prefix', 'update_custom_prefix;update_customprefix;update_prefix;update_prefixes'),
    ('commands.succes.cmd_update_custom_prefix', 'Impostazioni ed effetti dei prefissi ricaricati con successo per tutti gli utenti online!')
ON DUPLICATE KEY UPDATE `value` = VALUES(`value`);

-- 2. Normalized permission definitions (Ranks 6 & 7 enabled by default)
INSERT INTO `permission_definitions`
    (`permission_key`, `max_value`, `comment`, `rank_1`, `rank_2`, `rank_3`, `rank_4`, `rank_5`, `rank_6`, `rank_7`)
VALUES
    ('cmd_update_custom_prefix', 1, 'Allows reloading custom prefix settings and catalog for all online users.', 0, 0, 0, 0, 0, 1, 1)
ON DUPLICATE KEY UPDATE
    `max_value` = VALUES(`max_value`),
    `comment` = VALUES(`comment`),
    `rank_6` = 1,
    `rank_7` = 1;

-- 3. Legacy permissions column if table is present
ALTER TABLE `permissions` ADD COLUMN IF NOT EXISTS `cmd_update_custom_prefix` enum('0','1') NOT NULL DEFAULT '0';
UPDATE `permissions` SET `cmd_update_custom_prefix` = '1' WHERE `level` >= 6;

-- 4. Create category tables for icons and nick/prefix
CREATE TABLE IF NOT EXISTS `custom_icon_category` (
    `id` int(11) NOT NULL AUTO_INCREMENT,
    `name` varchar(100) NOT NULL,
    `badge` varchar(50) NOT NULL DEFAULT '',
    `sort_order` int(11) NOT NULL DEFAULT 0,
    `enabled` tinyint(1) NOT NULL DEFAULT 1,
    PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS `custom_nick_category` (
    `id` int(11) NOT NULL AUTO_INCREMENT,
    `name` varchar(100) NOT NULL,
    `badge` varchar(50) NOT NULL DEFAULT '',
    `sort_order` int(11) NOT NULL DEFAULT 0,
    `enabled` tinyint(1) NOT NULL DEFAULT 1,
    PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS `custom_prefix_category` (
    `id` int(11) NOT NULL AUTO_INCREMENT,
    `name` varchar(100) NOT NULL,
    `badge` varchar(50) NOT NULL DEFAULT '',
    `sort_order` int(11) NOT NULL DEFAULT 0,
    `enabled` tinyint(1) NOT NULL DEFAULT 1,
    PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 5. Add category_id and credits columns
ALTER TABLE `custom_nick_icons_catalog` ADD COLUMN IF NOT EXISTS `category_id` int(11) NOT NULL DEFAULT 0;
ALTER TABLE `custom_prefixes_catalog` ADD COLUMN IF NOT EXISTS `category_id` int(11) NOT NULL DEFAULT 0;
ALTER TABLE `custom_prefixes_catalog` ADD COLUMN IF NOT EXISTS `credits` int(11) NOT NULL DEFAULT 0;

-- 6. Populate default categories for icons and nick/prefix
INSERT INTO `custom_nick_category` (`id`, `name`, `badge`, `sort_order`, `enabled`) VALUES
    (1, 'Animati', '', 1, 1),
    (2, 'Statici & Glow', '', 2, 1),
    (3, 'Speciali & Epici', '', 3, 1)
ON DUPLICATE KEY UPDATE `name` = VALUES(`name`), `sort_order` = VALUES(`sort_order`), `enabled` = VALUES(`enabled`);

INSERT INTO `custom_prefix_category` (`id`, `name`, `badge`, `sort_order`, `enabled`) VALUES
    (1, 'Animati', '', 1, 1),
    (2, 'Statici & Glow', '', 2, 1),
    (3, 'Speciali & Epici', '', 3, 1)
ON DUPLICATE KEY UPDATE `name` = VALUES(`name`), `sort_order` = VALUES(`sort_order`), `enabled` = VALUES(`enabled`);

INSERT INTO `custom_icon_category` (`id`, `name`, `badge`, `sort_order`, `enabled`) VALUES
    (1, 'Icone 1 - 50', '', 1, 1),
    (2, 'Icone 51 - 100', '', 2, 1),
    (3, 'Icone 101 - 150', '', 3, 1),
    (4, 'Icone 151 - 200', '', 4, 1),
    (5, 'Icone 201 - 250', '', 5, 1),
    (6, 'Icone 251 - 300', '', 6, 1),
    (7, 'Icone 301 - 350', '', 7, 1)
ON DUPLICATE KEY UPDATE `name` = VALUES(`name`), `sort_order` = VALUES(`sort_order`), `enabled` = VALUES(`enabled`);

-- 7. Default general settings in custom_prefix_settings
INSERT INTO `custom_prefix_settings` (`key_name`, `value`) VALUES
    ('price_color_credits', '5'),
    ('price_color_points', '0'),
    ('color_points_type', '5'),
    ('disabled_effects', 'cartoon,toon')
ON DUPLICATE KEY UPDATE `value` = VALUES(`value`);

-- 8. Populate custom_prefixes_catalog with all effects, category_id, credits, points, points_type, and enabled status
INSERT INTO `custom_prefixes_catalog` (`category_id`, `display_name`, `text`, `color`, `icon`, `effect`, `font`, `credits`, `points`, `points_type`, `enabled`, `sort_order`) VALUES
    (2, 'Nessuno', 'PREFIX', '#000000', '', '', '', 0, 0, 0, 1, 1),
    (2, 'Corsivo', 'PREFIX', '#000000', '', 'italic', '', 1, 0, 0, 1, 2),
    (2, 'Sottolineato', 'PREFIX', '#000000', '', 'underline', '', 1, 0, 0, 1, 3),
    (2, 'Bagliore', 'PREFIX', '#000000', '', 'glow', '', 2, 0, 0, 1, 4),
    (2, 'Ombra 3D', 'PREFIX', '#000000', '', 'shadow', '', 2, 0, 0, 1, 5),
    (2, 'Contorno', 'PREFIX', '#000000', '', 'outline', '', 2, 0, 0, 1, 6),
    (1, 'Pulsazione', 'PREFIX', '#000000', '', 'pulse', '', 3, 0, 0, 1, 7),
    (1, 'Rimbalzo', 'PREFIX', '#000000', '', 'bounce', '', 4, 0, 0, 1, 8),
    (1, 'Onda Animata', 'PREFIX', '#000000', '', 'wave', '', 4, 0, 0, 1, 9),
    (1, 'Scuotimento', 'PREFIX', '#000000', '', 'shake', '', 4, 0, 0, 1, 10),
    (2, 'Neon Intenso', 'PREFIX', '#000000', '', 'bold-glow', '', 5, 0, 0, 1, 11),
    (2, 'Discord Glow', 'PREFIX', '#000000', '', 'discord-neon', '', 5, 0, 0, 1, 12),
    (3, 'Pop 3D', 'PREFIX', '#000000', '', 'pop', '', 6, 0, 0, 1, 13),
    (3, 'Ghiaccio Polare', 'PREFIX', '#000000', '', 'frost', '', 7, 1, 5, 1, 14),
    (1, 'Luccichio', 'PREFIX', '#000000', '', 'sparkle', '', 8, 1, 5, 1, 15),
    (1, 'Matrix Digitale', 'PREFIX', '#000000', '', 'matrix', '', 8, 2, 5, 1, 16),
    (3, 'Cristallo Ice', 'PREFIX', '#000000', '', 'ice', '', 10, 2, 5, 1, 17),
    (3, 'Oro Lucente', 'PREFIX', '#000000', '', 'gold', '', 10, 2, 5, 1, 18),
    (1, 'Fiamme Vive', 'PREFIX', '#000000', '', 'fire', '', 12, 3, 5, 1, 19),
    (1, 'Glitch Cyber', 'PREFIX', '#000000', '', 'glitch', '', 12, 3, 5, 1, 20),
    (1, 'Cyberpunk Neon', 'PREFIX', '#000000', '', 'cyberpunk', '', 12, 3, 5, 1, 21),
    (1, 'Elettricità', 'PREFIX', '#000000', '', 'electric', '', 14, 4, 5, 1, 22),
    (1, 'Arcobaleno', 'PREFIX', '#000000', '', 'rainbow', '', 15, 5, 5, 1, 23),
    (1, 'Galassia Cosmica', 'PREFIX', '#000000', '', 'cosmic', '', 15, 5, 5, 1, 24)
ON DUPLICATE KEY UPDATE
    `category_id` = VALUES(`category_id`),
    `display_name` = VALUES(`display_name`),
    `credits` = VALUES(`credits`),
    `points` = VALUES(`points`),
    `points_type` = VALUES(`points_type`),
    `enabled` = VALUES(`enabled`),
    `sort_order` = VALUES(`sort_order`);
