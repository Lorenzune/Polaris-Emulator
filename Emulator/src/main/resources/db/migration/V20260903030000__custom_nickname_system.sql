-- Custom Nickname System: catalog, user nicks, categories, and settings

CREATE TABLE IF NOT EXISTS `custom_nick_category` (
    `id` int(11) NOT NULL AUTO_INCREMENT,
    `name` varchar(100) NOT NULL,
    `badge` varchar(50) NOT NULL DEFAULT '',
    `sort_order` int(11) NOT NULL DEFAULT 0,
    `enabled` tinyint(1) NOT NULL DEFAULT 1,
    PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS `custom_nicks_catalog` (
    `id` int(11) NOT NULL AUTO_INCREMENT,
    `category_id` int(11) NOT NULL DEFAULT 0,
    `display_name` varchar(100) NOT NULL,
    `color` varchar(255) NOT NULL DEFAULT '#000000',
    `effect` varchar(50) NOT NULL DEFAULT '',
    `font` varchar(50) NOT NULL DEFAULT '',
    `credits` int(11) NOT NULL DEFAULT 0,
    `points` int(11) NOT NULL DEFAULT 0,
    `points_type` int(11) NOT NULL DEFAULT 0,
    `enabled` tinyint(1) NOT NULL DEFAULT 1,
    `sort_order` int(11) NOT NULL DEFAULT 0,
    PRIMARY KEY (`id`),
    KEY `idx_custom_nicks_cat` (`category_id`, `enabled`, `sort_order`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS `user_nicks` (
    `id` int(11) NOT NULL AUTO_INCREMENT,
    `user_id` int(11) NOT NULL,
    `color` varchar(255) NOT NULL DEFAULT '#000000',
    `effect` varchar(50) NOT NULL DEFAULT '',
    `font` varchar(50) NOT NULL DEFAULT '',
    `active` tinyint(1) NOT NULL DEFAULT 0,
    `catalog_nick_id` int(11) NOT NULL DEFAULT 0,
    `created_at` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    KEY `idx_user_nicks_user` (`user_id`),
    KEY `idx_user_nicks_active` (`user_id`, `active`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS `custom_nick_settings` (
    `key_name` varchar(100) NOT NULL,
    `value` varchar(255) NOT NULL,
    PRIMARY KEY (`key_name`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- Categories
INSERT INTO `custom_nick_category` (`id`, `name`, `badge`, `sort_order`, `enabled`) VALUES
    (1, 'Luccichio & Luce', '', 1, 1),
    (2, 'Animati & Onde', '', 2, 1),
    (3, 'Elementali & Natura', '', 3, 1),
    (4, 'Cyber & Retrò', '', 4, 1),
    (5, 'Speciali & Epici', '', 5, 1)
ON DUPLICATE KEY UPDATE `name` = VALUES(`name`), `sort_order` = VALUES(`sort_order`), `enabled` = VALUES(`enabled`);

-- Settings
INSERT INTO `custom_nick_settings` (`key_name`, `value`) VALUES
    ('price_color_credits', '5'),
    ('price_color_points', '0'),
    ('color_points_type', '5'),
    ('price_custom_credits', '10'),
    ('price_custom_points', '2'),
    ('custom_points_type', '5'),
    ('font_price_credits', '10'),
    ('font_price_points', '0'),
    ('font_points_type', '5'),
    ('max_nicks', '60'),
    ('min_rank_to_buy', '1')
ON DUPLICATE KEY UPDATE `value` = VALUES(`value`);

-- Nickname Effects Catalog (Forum Classic & Modern styles)
INSERT INTO `custom_nicks_catalog` (`category_id`, `display_name`, `color`, `effect`, `font`, `credits`, `points`, `points_type`, `enabled`, `sort_order`) VALUES
    (1, 'Shimmering Metallico', '#E0E7FF', 'shimmer', '', 8, 1, 5, 1, 1),
    (1, 'Golden Glitter', '#FFD700', 'glitter', '', 10, 2, 5, 1, 2),
    (1, 'Sparkle Stellare', '#FFFBEB', 'sparkle', '', 8, 1, 5, 1, 3),
    (1, 'Neon Glow Cyan', '#00FFFF', 'neon', '', 6, 0, 0, 1, 4),
    (1, 'Neon Glow Fucsia', '#FF007F', 'neon', '', 6, 0, 0, 1, 5),
    (1, 'Bagliore Dorato', '#F59E0B', 'glow', '', 4, 0, 0, 1, 6),
    (2, 'Rainbow Wave', '#FF0055', 'rainbow', '', 15, 5, 5, 1, 7),
    (2, 'Onda Fluida', '#38BDF8', 'wave', '', 5, 0, 0, 1, 8),
    (2, 'Pulsazione Energetica', '#A855F7', 'pulse', '', 4, 0, 0, 1, 9),
    (2, 'Rimbalzo 3D', '#EC4899', 'bounce', '', 4, 0, 0, 1, 10),
    (3, 'Burning Fire', '#EF4444', 'fire', '', 12, 3, 5, 1, 11),
    (3, 'Fiamma Blu Infernale', '#3B82F6', 'fire', '', 12, 3, 5, 1, 12),
    (3, 'Ice Frost Glaciale', '#E0F2FE', 'ice', '', 10, 2, 5, 1, 13),
    (3, 'Elettricità Fulminea', '#FACC15', 'electric', '', 14, 4, 5, 1, 14),
    (3, 'Toxic Slime Acido', '#22C55E', 'toxic', '', 10, 2, 5, 1, 15),
    (4, 'Matrix Hacker Glitch', '#10B981', 'matrix', '', 12, 3, 5, 1, 16),
    (4, 'Cyberpunk Distortion', '#EC4899', 'cyberpunk', '', 12, 3, 5, 1, 17),
    (4, 'Glitch Cromatico', '#06B6D4', 'glitch', '', 12, 3, 5, 1, 18),
    (4, 'Vaporwave Sunset', '#F472B6', 'vaporwave', '', 10, 2, 5, 1, 19),
    (4, 'Hologram Scanline', '#67E8F9', 'hologram', '', 10, 2, 5, 1, 20),
    (5, 'Cosmic Aurora', '#C084FC', 'cosmic', '', 15, 5, 5, 1, 21),
    (5, 'Chrome Silver Polish', '#F1F5F9', 'chrome', '', 10, 2, 5, 1, 22),
    (5, 'Dark Void Shadow', '#6B21A8', 'darkness', '', 8, 1, 5, 1, 23),
    (5, 'Pixel Art 8-Bit', '#FFFFFF', '', 'pixel', 6, 0, 0, 1, 24),
    (5, 'Cherry Bomb Vintage', '#F43F5E', '', 'cherry', 6, 0, 0, 1, 25),
    (5, 'Vampiro Gothic', '#9333EA', '', 'vampiro', 6, 0, 0, 1, 26)
ON DUPLICATE KEY UPDATE `display_name` = VALUES(`display_name`), `credits` = VALUES(`credits`), `points` = VALUES(`points`), `enabled` = VALUES(`enabled`);
