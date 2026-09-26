UPDATE emulator_settings SET `value` = '1' WHERE (`key` = 'wired.engine.enabled');
UPDATE emulator_settings SET `value` = '1' WHERE (`key` = 'wired.engine.exclusive');

ALTER TABLE emulator_settings
ADD COLUMN IF NOT EXISTS `comment` VARCHAR(255) NOT NULL AFTER `value`;


CREATE TABLE IF NOT EXISTS `catalog_items_bc` (
  `id` int(11) NOT NULL AUTO_INCREMENT,
  `item_ids` varchar(666) NOT NULL,
  `page_id` int(11) NOT NULL,
  `catalog_name` varchar(100) NOT NULL,
  `order_number` int(11) NOT NULL DEFAULT 1,
  `extradata` varchar(500) NOT NULL DEFAULT '',
  PRIMARY KEY (`id`) USING BTREE
) ENGINE=InnoDB AUTO_INCREMENT=21 DEFAULT CHARSET=latin1 COLLATE=latin1_swedish_ci ROW_FORMAT=DYNAMIC;

CREATE TABLE IF NOT EXISTS `catalog_pages_bc` (
  `id` int(11) NOT NULL AUTO_INCREMENT,
  `parent_id` int(11) NOT NULL DEFAULT -1,
  `caption` varchar(128) NOT NULL,
  `page_layout` enum(
    'default_3x3','club_buy','club_gift','frontpage','spaces','recycler',
    'recycler_info','recycler_prizes','trophies','plasto','marketplace',
    'marketplace_own_items','spaces_new','soundmachine','guilds','guild_furni',
    'info_duckets','info_rentables','info_pets','roomads','single_bundle',
    'sold_ltd_items','badge_display','bots','pets','pets2','pets3',
    'productpage1','room_bundle','recent_purchases',
    'default_3x3_color_grouping','guild_forum','vip_buy','info_loyalty',
    'loyalty_vip_buy','collectibles','petcustomization','frontpage_featured'
  ) NOT NULL DEFAULT 'default_3x3',
  `icon_color` int(11) NOT NULL DEFAULT 1,
  `icon_image` int(11) NOT NULL DEFAULT 1,
  `order_num` int(11) NOT NULL DEFAULT 1,
  `visible` enum('0','1') NOT NULL DEFAULT '1',
  `enabled` enum('0','1') NOT NULL DEFAULT '1',
  `page_headline` varchar(1024) NOT NULL DEFAULT '',
  `page_teaser` varchar(64) NOT NULL DEFAULT '',
  `page_special` varchar(2048) DEFAULT '' COMMENT 'Gold Bubble: catalog_special_txtbg1 // Speech Bubble: catalog_special_txtbg2 // Place normal text in page_text_teaser',
  `page_text1` text DEFAULT NULL,
  `page_text2` text DEFAULT NULL,
  `page_text_details` text DEFAULT NULL,
  `page_text_teaser` text DEFAULT NULL,
  PRIMARY KEY (`id`) USING BTREE
) ENGINE=MyISAM AUTO_INCREMENT=9 DEFAULT CHARSET=latin1 COLLATE=latin1_swedish_ci ROW_FORMAT=DYNAMIC;

ALTER TABLE `catalog_club_offers`
MODIFY COLUMN `type` ENUM('HC','VIP','BUILDERS_CLUB','BUILDERS_CLUB_ADDON') NOT NULL DEFAULT 'HC';

ALTER TABLE `catalog_pages`
  MODIFY COLUMN `page_layout` ENUM(
    'default_3x3',
    'club_buy',
    'club_gift',
    'frontpage',
    'spaces',
    'recycler',
    'recycler_info',
    'recycler_prizes',
    'trophies',
    'plasto',
    'marketplace',
    'marketplace_own_items',
    'spaces_new',
    'soundmachine',
    'guilds',
    'guild_furni',
    'info_duckets',
    'info_rentables',
    'info_pets',
    'roomads',
    'single_bundle',
    'sold_ltd_items',
    'badge_display',
    'bots',
    'pets',
    'pets2',
    'pets3',
    'productpage1',
    'room_bundle',
    'recent_purchases',
    'default_3x3_color_grouping',
    'guild_forum',
    'vip_buy',
    'info_loyalty',
    'loyalty_vip_buy',
    'collectibles',
    'petcustomization',
    'frontpage_featured',
    'builders_club_frontpage',
    'builders_club_addons',
    'builders_club_loyalty'
  ) NOT NULL DEFAULT 'default_3x3';

ALTER TABLE `catalog_pages`
ADD COLUMN IF NOT EXISTS `catalog_mode` ENUM('NORMAL','BUILDER','BOTH') NOT NULL DEFAULT 'NORMAL'
AFTER `club_only`;

ALTER TABLE `catalog_pages_bc`
  MODIFY COLUMN `page_layout` ENUM(
    'default_3x3',
    'club_buy',
    'club_gift',
    'frontpage',
    'spaces',
    'recycler',
    'recycler_info',
    'recycler_prizes',
    'trophies',
    'plasto',
    'marketplace',
    'marketplace_own_items',
    'spaces_new',
    'soundmachine',
    'guilds',
    'guild_furni',
    'info_duckets',
    'info_rentables',
    'info_pets',
    'roomads',
    'single_bundle',
    'sold_ltd_items',
    'badge_display',
    'bots',
    'pets',
    'pets2',
    'pets3',
    'productpage1',
    'room_bundle',
    'recent_purchases',
    'default_3x3_color_grouping',
    'guild_forum',
    'vip_buy',
    'info_loyalty',
    'loyalty_vip_buy',
    'collectibles',
    'petcustomization',
    'frontpage_featured',
    'builders_club_frontpage',
    'builders_club_addons',
    'builders_club_loyalty'
  ) NOT NULL DEFAULT 'default_3x3';

SET @col_exists := (
  SELECT COUNT(*) FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE()
  AND TABLE_NAME = 'users_settings'
  AND COLUMN_NAME = 'builders_club_bonus_furni'
);
SET @sql := IF(@col_exists = 0,
  'ALTER TABLE `users_settings` ADD COLUMN `builders_club_bonus_furni` INT NOT NULL DEFAULT 0;',
  'SELECT "exists";'
);
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

CREATE TABLE IF NOT EXISTS `wired_emulator_settings` (
  `key` varchar(191) NOT NULL,
  `value` text NOT NULL,
  `comment` text NOT NULL,
  PRIMARY KEY (`key`)
) ENGINE=InnoDB DEFAULT CHARSET=latin1 COLLATE=latin1_swedish_ci;

INSERT INTO `wired_emulator_settings` (`key`, `value`, `comment`)
SELECT 'wired.engine.enabled', COALESCE((SELECT `value` FROM `emulator_settings` WHERE `key` = 'wired.engine.enabled' LIMIT 1), '1'), 'Compatibility flag kept for older configs. The runtime now always uses the new wired engine.'
UNION ALL
SELECT 'wired.engine.exclusive', COALESCE((SELECT `value` FROM `emulator_settings` WHERE `key` = 'wired.engine.exclusive' LIMIT 1), '1'), 'Compatibility flag kept for older configs. The runtime now always uses the new wired engine.'
UNION ALL
SELECT 'wired.engine.maxStepsPerStack', COALESCE((SELECT `value` FROM `emulator_settings` WHERE `key` = 'wired.engine.maxStepsPerStack' LIMIT 1), '100'), 'Maximum amount of internal processing steps allowed for a single wired stack execution.'
UNION ALL
SELECT 'wired.engine.debug', COALESCE((SELECT `value` FROM `emulator_settings` WHERE `key` = 'wired.engine.debug' LIMIT 1), '0'), 'Enable verbose debug logging for the new wired engine.'
UNION ALL
SELECT 'wired.custom.enabled', COALESCE((SELECT `value` FROM `emulator_settings` WHERE `key` = 'wired.custom.enabled' LIMIT 1), '0'), 'Enable custom legacy wired behaviour such as user-based cooldown exceptions and compatibility logic.'
UNION ALL
SELECT 'hotel.wired.furni.selection.count', COALESCE((SELECT `value` FROM `emulator_settings` WHERE `key` = 'hotel.wired.furni.selection.count' LIMIT 1), '5'), 'Maximum number of furni that a wired box can store or select.'
UNION ALL
SELECT 'hotel.wired.max_delay', COALESCE((SELECT `value` FROM `emulator_settings` WHERE `key` = 'hotel.wired.max_delay' LIMIT 1), '20'), 'Maximum delay value accepted by wired effects that support delayed execution.'
UNION ALL
SELECT 'hotel.wired.message.max_length', COALESCE((SELECT `value` FROM `emulator_settings` WHERE `key` = 'hotel.wired.message.max_length' LIMIT 1), '100'), 'Maximum length of text fields used by wired messages and bot text effects.'
UNION ALL
SELECT 'wired.effect.teleport.delay', COALESCE((SELECT `value` FROM `emulator_settings` WHERE `key` = 'wired.effect.teleport.delay' LIMIT 1), '500'), 'Delay in milliseconds used by wired teleport movement.'
UNION ALL
SELECT 'wired.place.under', COALESCE((SELECT `value` FROM `emulator_settings` WHERE `key` = 'wired.place.under' LIMIT 1), '0'), 'Allow placing wired furniture underneath other items when room rules permit it.'
UNION ALL
SELECT 'wired.tick.interval.ms', COALESCE((SELECT `value` FROM `emulator_settings` WHERE `key` = 'wired.tick.interval.ms' LIMIT 1), '50'), 'Global wired tick interval in milliseconds used by repeaters and other tick-driven wired items.'
UNION ALL
SELECT 'wired.tick.resolution', COALESCE((SELECT `value` FROM `emulator_settings` WHERE `key` = 'wired.tick.resolution' LIMIT 1), '100'), 'Legacy wired tick resolution value kept for compatibility with older wired timing setups.'
UNION ALL
SELECT 'wired.tick.debug', COALESCE((SELECT `value` FROM `emulator_settings` WHERE `key` = 'wired.tick.debug' LIMIT 1), '0'), 'Enable verbose logging for the wired tick service.'
UNION ALL
SELECT 'wired.tick.thread.priority', COALESCE((SELECT `value` FROM `emulator_settings` WHERE `key` = 'wired.tick.thread.priority' LIMIT 1), '6'), 'Java thread priority used by the wired tick service.'
UNION ALL
SELECT 'wired.highscores.displaycount', COALESCE((SELECT `value` FROM `emulator_settings` WHERE `key` = 'wired.highscores.displaycount' LIMIT 1), '25'), 'Maximum number of wired highscore entries shown to users when a highscore is displayed.'
UNION ALL
SELECT 'wired.abuse.max.recursion.depth', COALESCE((SELECT `value` FROM `emulator_settings` WHERE `key` = 'wired.abuse.max.recursion.depth' LIMIT 1), '10'), 'Maximum recursive wired depth allowed before execution is stopped.'
UNION ALL
SELECT 'wired.abuse.max.events.per.window', COALESCE((SELECT `value` FROM `emulator_settings` WHERE `key` = 'wired.abuse.max.events.per.window' LIMIT 1), '100'), 'Maximum amount of identical wired events allowed inside the abuse rate-limit window before a room ban is applied.'
UNION ALL
SELECT 'wired.abuse.rate.limit.window.ms', COALESCE((SELECT `value` FROM `emulator_settings` WHERE `key` = 'wired.abuse.rate.limit.window.ms' LIMIT 1), '10000'), 'Time window in milliseconds used by the wired abuse rate limiter.'
UNION ALL
SELECT 'wired.abuse.ban.duration.ms', COALESCE((SELECT `value` FROM `emulator_settings` WHERE `key` = 'wired.abuse.ban.duration.ms' LIMIT 1), '600000'), 'Duration in milliseconds of the temporary wired ban after abuse detection.'
UNION ALL
SELECT 'wired.signal.max.events.per.chain', COALESCE((SELECT `value` FROM `emulator_settings` WHERE `key` = 'wired.signal.max.events.per.chain' LIMIT 1), '2048'), 'Maximum signal events admitted in one synchronous WIRED chain; 0 disables this limit.'
UNION ALL
SELECT 'wired.signal.max.chain.ms', COALESCE((SELECT `value` FROM `emulator_settings` WHERE `key` = 'wired.signal.max.chain.ms' LIMIT 1), '250'), 'Maximum synchronous duration in milliseconds for one WIRED signal chain; 0 disables this limit.'
UNION ALL
SELECT 'wired.monitor.usage.window.ms', COALESCE((SELECT `value` FROM `emulator_settings` WHERE `key` = 'wired.monitor.usage.window.ms' LIMIT 1), '1000'), 'Rolling window size in milliseconds used to calculate wired usage in the :wired monitor.'
UNION ALL
SELECT 'wired.monitor.usage.limit', COALESCE((SELECT `value` FROM `emulator_settings` WHERE `key` = 'wired.monitor.usage.limit' LIMIT 1), '1000'), 'Maximum wired usage budget allowed in one monitor window before EXECUTION_CAP is raised.'
UNION ALL
SELECT 'wired.monitor.delayed.events.limit', COALESCE((SELECT `value` FROM `emulator_settings` WHERE `key` = 'wired.monitor.delayed.events.limit' LIMIT 1), '100'), 'Maximum number of delayed wired events that can be queued in one room at the same time.'
UNION ALL
SELECT 'wired.monitor.overload.average.ms', COALESCE((SELECT `value` FROM `emulator_settings` WHERE `key` = 'wired.monitor.overload.average.ms' LIMIT 1), '50'), 'Average execution time threshold in milliseconds that starts overload tracking.'
UNION ALL
SELECT 'wired.monitor.overload.peak.ms', COALESCE((SELECT `value` FROM `emulator_settings` WHERE `key` = 'wired.monitor.overload.peak.ms' LIMIT 1), '150'), 'Peak single execution time threshold in milliseconds that starts overload tracking.'
UNION ALL
SELECT 'wired.monitor.overload.consecutive.windows', COALESCE((SELECT `value` FROM `emulator_settings` WHERE `key` = 'wired.monitor.overload.consecutive.windows' LIMIT 1), '2'), 'Number of consecutive overloaded monitor windows required before logging EXECUTOR_OVERLOAD.'
UNION ALL
SELECT 'wired.monitor.heavy.usage.percent', COALESCE((SELECT `value` FROM `emulator_settings` WHERE `key` = 'wired.monitor.heavy.usage.percent' LIMIT 1), '70'), 'Usage percentage threshold that contributes to marking a room as heavy in the :wired monitor.'
UNION ALL
SELECT 'wired.monitor.heavy.consecutive.windows', COALESCE((SELECT `value` FROM `emulator_settings` WHERE `key` = 'wired.monitor.heavy.consecutive.windows' LIMIT 1), '5'), 'Number of consecutive windows above the heavy usage threshold required before the room is marked as heavy.'
UNION ALL
SELECT 'wired.monitor.heavy.delayed.percent', COALESCE((SELECT `value` FROM `emulator_settings` WHERE `key` = 'wired.monitor.heavy.delayed.percent' LIMIT 1), '60'), 'Delayed queue percentage threshold that also contributes to the heavy-room calculation.'
ON DUPLICATE KEY UPDATE
  `value` = VALUES(`value`),
  `comment` = VALUES(`comment`);

DELETE FROM `emulator_settings`
WHERE `key` IN (
  'wired.engine.enabled',
  'wired.engine.exclusive',
  'wired.engine.maxStepsPerStack',
  'wired.engine.debug',
  'wired.custom.enabled',
  'hotel.wired.furni.selection.count',
  'hotel.wired.max_delay',
  'hotel.wired.message.max_length',
  'wired.effect.teleport.delay',
  'wired.place.under',
  'wired.tick.interval.ms',
  'wired.tick.resolution',
  'wired.tick.debug',
  'wired.tick.thread.priority',
  'wired.highscores.displaycount',
  'wired.abuse.max.recursion.depth',
  'wired.abuse.max.events.per.window',
  'wired.abuse.rate.limit.window.ms',
  'wired.abuse.ban.duration.ms',
  'wired.signal.max.events.per.chain',
  'wired.signal.max.chain.ms',
  'wired.monitor.usage.window.ms',
  'wired.monitor.usage.limit',
  'wired.monitor.delayed.events.limit',
  'wired.monitor.overload.average.ms',
  'wired.monitor.overload.peak.ms',
  'wired.monitor.overload.consecutive.windows',
  'wired.monitor.heavy.usage.percent',
  'wired.monitor.heavy.consecutive.windows',
  'wired.monitor.heavy.delayed.percent'
);

UPDATE `emulator_settings` SET `comment` = 'Allow whispering while a user stands inside a mute area.' WHERE `key` = 'room.chat.mutearea.allow_whisper';
UPDATE `emulator_settings` SET `comment` = 'HTML or text format used for room chat prefixes.' WHERE `key` = 'room.chat.prefix.format';
UPDATE `emulator_settings` SET `comment` = 'Badge code displayed on promoted rooms.' WHERE `key` = 'room.promotion.badge';
UPDATE `emulator_settings` SET `comment` = 'Image used by Rosie bubble notifications.' WHERE `key` = 'rosie.bubble.image.url';
UPDATE `emulator_settings` SET `comment` = 'Currency type used by Rosie when buying a room or room package.' WHERE `key` = 'rosie.buyroom.currency.type';
UPDATE `emulator_settings` SET `comment` = 'Configuration value used by `runtime.threads`.' WHERE `key` = 'runtime.threads';
UPDATE `emulator_settings` SET `comment` = 'Configuration value used by `save.private.chats`.' WHERE `key` = 'save.private.chats';
UPDATE `emulator_settings` SET `comment` = 'Configuration value used by `save.room.chats`.' WHERE `key` = 'save.room.chats';
UPDATE `emulator_settings` SET `comment` = 'Expose moderation tickets to the scripter or automation tooling.' WHERE `key` = 'scripter.modtool.tickets';
UPDATE `emulator_settings` SET `comment` = 'Currency type ID used for diamonds.' WHERE `key` = 'seasonal.currency.diamond';
UPDATE `emulator_settings` SET `comment` = 'Currency type ID used for duckets.' WHERE `key` = 'seasonal.currency.ducket';
UPDATE `emulator_settings` SET `comment` = 'Semicolon-separated display names for seasonal currency types.' WHERE `key` = 'seasonal.currency.names';
UPDATE `emulator_settings` SET `comment` = 'Currency type ID used for pixels.' WHERE `key` = 'seasonal.currency.pixel';
UPDATE `emulator_settings` SET `comment` = 'Currency type ID used for shells.' WHERE `key` = 'seasonal.currency.shell';
UPDATE `emulator_settings` SET `comment` = 'Primary seasonal currency type ID.' WHERE `key` = 'seasonal.primary.type';
UPDATE `emulator_settings` SET `comment` = 'Semicolon-separated list of currency type IDs treated as seasonal currencies.' WHERE `key` = 'seasonal.types';
UPDATE `emulator_settings` SET `comment` = 'Achievement code granted for the HC subscription tier.' WHERE `key` = 'subscriptions.hc.achievement';
UPDATE `emulator_settings` SET `comment` = 'Number of days before expiry when HC discount offers become available.' WHERE `key` = 'subscriptions.hc.discount.days_before_end';
UPDATE `emulator_settings` SET `comment` = 'Enable discounted HC renewal offers.' WHERE `key` = 'subscriptions.hc.discount.enabled';
UPDATE `emulator_settings` SET `comment` = 'Reset tracked credits spent when the HC subscription expires.' WHERE `key` = 'subscriptions.hc.payday.creditsspent_reset_on_expire';
UPDATE `emulator_settings` SET `comment` = 'Currency rewarded by the HC payday system.' WHERE `key` = 'subscriptions.hc.payday.currency';
UPDATE `emulator_settings` SET `comment` = 'Enable the HC payday reward system.' WHERE `key` = 'subscriptions.hc.payday.enabled';
UPDATE `emulator_settings` SET `comment` = 'Date interval used between HC payday reward runs.' WHERE `key` = 'subscriptions.hc.payday.interval';
UPDATE `emulator_settings` SET `comment` = 'Next scheduled execution date for HC payday rewards.' WHERE `key` = 'subscriptions.hc.payday.next_date';
UPDATE `emulator_settings` SET `comment` = 'Percentage of eligible spending returned by HC payday.' WHERE `key` = 'subscriptions.hc.payday.percentage';
UPDATE `emulator_settings` SET `comment` = 'Semicolon-separated streak thresholds and rewards for HC payday.' WHERE `key` = 'subscriptions.hc.payday.streak';
UPDATE `emulator_settings` SET `comment` = 'Enable the subscription background scheduler.' WHERE `key` = 'subscriptions.scheduler.enabled';
UPDATE `emulator_settings` SET `comment` = 'Interval in minutes between subscription scheduler runs.' WHERE `key` = 'subscriptions.scheduler.interval';
UPDATE `emulator_settings` SET `comment` = 'Compatibility marker used by the custom team wired implementation. Do not remove.' WHERE `key` = 'team.wired.update.rc-1';
UPDATE `emulator_settings` SET `comment` = 'API key used by the YouTube integration.' WHERE `key` = 'youtube.apikey';

-- =============================================================
-- Permissions normalization is handled by 005_normalize_permissions_schema.sql
-- (Removed from this file to avoid DELIMITER issues in HeidiSQL.)
-- =============================================================

CREATE TABLE IF NOT EXISTS `room_wired_settings` (
  `room_id` int(11) NOT NULL,
  `inspect_mask` int(11) NOT NULL DEFAULT 0 COMMENT 'Bitmask for who can open and inspect Wired in the room. 1=everyone, 2=users with rights, 4=group members, 8=group admins.',
  `modify_mask` int(11) NOT NULL DEFAULT 0 COMMENT 'Bitmask for who can modify Wired in the room. 2=users with rights, 4=group members, 8=group admins.',
  PRIMARY KEY (`room_id`),
  CONSTRAINT `fk_room_wired_settings_room_id` FOREIGN KEY (`room_id`) REFERENCES `rooms` (`id`) ON DELETE CASCADE ON UPDATE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS `room_user_wired_variables` (
    `room_id` int(11) NOT NULL,
    `user_id` int(11) NOT NULL,
    `variable_item_id` int(11) NOT NULL,
    `value` int(11) DEFAULT NULL,
    `created_at` int(11) NOT NULL DEFAULT 0,
    `updated_at` int(11) NOT NULL DEFAULT 0,
    PRIMARY KEY (`room_id`, `user_id`, `variable_item_id`),
    KEY `idx_room_user_wired_variables_room_item` (`room_id`, `variable_item_id`),
    KEY `idx_room_user_wired_variables_user` (`user_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS `room_furni_wired_variables` (
    `room_id` int(11) NOT NULL,
    `furni_id` int(11) NOT NULL,
    `variable_item_id` int(11) NOT NULL,
    `value` int(11) DEFAULT NULL,
    `created_at` int(11) NOT NULL DEFAULT 0,
    `updated_at` int(11) NOT NULL DEFAULT 0,
    PRIMARY KEY (`room_id`, `furni_id`, `variable_item_id`),
    KEY `idx_room_furni_wired_variables_room_item` (`room_id`, `variable_item_id`),
    KEY `idx_room_furni_wired_variables_furni` (`furni_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS `room_wired_variables` (
    `room_id` int(11) NOT NULL,
    `variable_item_id` int(11) NOT NULL,
    `value` int(11) NOT NULL DEFAULT 0,
    `created_at` int(11) NOT NULL DEFAULT 0,
    `updated_at` int(11) NOT NULL DEFAULT 0,
    PRIMARY KEY (`room_id`, `variable_item_id`),
    KEY `idx_room_wired_variables_room_item` (`room_id`, `variable_item_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;


ALTER TABLE `room_user_wired_variables`
    ADD COLUMN IF NOT EXISTS `created_at` int(11) NOT NULL DEFAULT 0 AFTER `value`;

ALTER TABLE `room_user_wired_variables`
    ADD COLUMN IF NOT EXISTS `updated_at` int(11) NOT NULL DEFAULT 0 AFTER `created_at`;

UPDATE `room_user_wired_variables`
SET
    `created_at` = IF(`created_at` > 0, `created_at`, UNIX_TIMESTAMP()),
    `updated_at` = IF(`updated_at` > 0, `updated_at`, IF(`created_at` > 0, `created_at`, UNIX_TIMESTAMP()));

ALTER TABLE `room_furni_wired_variables`
    ADD COLUMN IF NOT EXISTS `created_at` int(11) NOT NULL DEFAULT 0 AFTER `value`;

ALTER TABLE `room_furni_wired_variables`
    ADD COLUMN IF NOT EXISTS `updated_at` int(11) NOT NULL DEFAULT 0 AFTER `created_at`;

UPDATE `room_furni_wired_variables`
SET
    `created_at` = IF(`created_at` > 0, `created_at`, UNIX_TIMESTAMP()),
    `updated_at` = IF(`updated_at` > 0, `updated_at`, IF(`created_at` > 0, `created_at`, UNIX_TIMESTAMP()));

ALTER TABLE `room_wired_variables`
    ADD COLUMN IF NOT EXISTS `created_at` int(11) NOT NULL DEFAULT 0 AFTER `value`;

ALTER TABLE `room_wired_variables`
    ADD COLUMN IF NOT EXISTS `updated_at` int(11) NOT NULL DEFAULT 0 AFTER `created_at`;

UPDATE `room_wired_variables`
SET
    `created_at` = 0,
    `updated_at` = IF(`updated_at` > 0, `updated_at`, UNIX_TIMESTAMP());

INSERT INTO `chat_bubbles` (`type`, `name`, `permission`, `overridable`, `triggers_talking_furniture`) VALUES
(200, 'SHOW_MESSAGE_RED', '', 1, 0),
(201, 'SHOW_MESSAGE_GREEN', '', 1, 0),
(202, 'SHOW_MESSAGE_BLUE', '', 1, 0),
(210, 'SHOW_MESSAGE_ALERT', '', 1, 0),
(211, 'SHOW_MESSAGE_INFO', '', 1, 0),
(212, 'SHOW_MESSAGE_WARNING', '', 1, 0),
(220, 'SHOW_MESSAGE_WRONG', '', 1, 0),
(221, 'SHOW_MESSAGE_WRONG_CIRCLED', '', 1, 0),
(222, 'SHOW_MESSAGE_CORRECT', '', 1, 0),
(223, 'SHOW_MESSAGE_CORRECT_CIRCLED', '', 1, 0),
(224, 'SHOW_MESSAGE_QUESTION', '', 1, 0),
(225, 'SHOW_MESSAGE_QUESTION_CIRCLED', '', 1, 0),
(226, 'SHOW_MESSAGE_ARROW_UP', '', 1, 0),
(227, 'SHOW_MESSAGE_ARROW_UP_CIRCLED', '', 1, 0),
(228, 'SHOW_MESSAGE_ARROW_DOWN', '', 1, 0),
(229, 'SHOW_MESSAGE_ARROW_DOWN_CIRCLED', '', 1, 0),
(250, 'SHOW_MESSAGE_SKULL', '', 1, 0),
(251, 'SHOW_MESSAGE_SKULL_ALT', '', 1, 0),
(252, 'SHOW_MESSAGE_MAGNIFIER', '', 1, 0)
ON DUPLICATE KEY UPDATE
    `name` = VALUES(`name`),
    `permission` = VALUES(`permission`),
    `overridable` = VALUES(`overridable`),
    `triggers_talking_furniture` = VALUES(`triggers_talking_furniture`);

ALTER TABLE `catalog_club_offers`
MODIFY COLUMN `type` ENUM('HC', 'VIP', 'BUILDERS_CLUB', 'BUILDERS_CLUB_ADDON') NOT NULL DEFAULT 'HC';

ALTER TABLE `catalog_pages`
    MODIFY COLUMN `page_layout` ENUM(
        'default_3x3',
        'club_buy',
        'club_gift',
        'frontpage',
        'spaces',
        'recycler',
        'recycler_info',
        'recycler_prizes',
        'trophies',
        'plasto',
        'marketplace',
        'marketplace_own_items',
        'spaces_new',
        'soundmachine',
        'guilds',
        'guild_furni',
        'info_duckets',
        'info_rentables',
        'info_pets',
        'roomads',
        'single_bundle',
        'sold_ltd_items',
        'badge_display',
        'bots',
        'pets',
        'pets2',
        'pets3',
        'productpage1',
        'room_bundle',
        'recent_purchases',
        'default_3x3_color_grouping',
        'guild_forum',
        'vip_buy',
        'info_loyalty',
        'loyalty_vip_buy',
        'collectibles',
        'petcustomization',
        'frontpage_featured',
        'builders_club_frontpage',
        'builders_club_addons',
        'builders_club_loyalty'
    ) NOT NULL DEFAULT 'default_3x3';

INSERT INTO `emulator_settings` (`key`, `value`, `comment`) VALUES
    ('room.cycle.workers', '0', 'Room-cycle workers; 0 selects 2 to 16 based on available processors; positive values are clamped to 1-64. Restart required.'),
    ('room.cycle.slow_ms', '250', 'Log a warning when one room cycle exceeds this many milliseconds.')
ON DUPLICATE KEY UPDATE `comment` = VALUES(`comment`);

ALTER TABLE `catalog_pages`
ADD COLUMN IF NOT EXISTS `catalog_mode` ENUM('NORMAL', 'BUILDER', 'BOTH') NOT NULL DEFAULT 'NORMAL' AFTER `club_only`;

ALTER TABLE `rooms`
    ADD COLUMN IF NOT EXISTS `builders_club_trial_locked` TINYINT(1) NOT NULL DEFAULT 0 AFTER `allow_underpass`,
    ADD COLUMN IF NOT EXISTS `builders_club_original_state` VARCHAR(16) NOT NULL DEFAULT 'open' AFTER `builders_club_trial_locked`;

CREATE TABLE IF NOT EXISTS `builders_club_items` (
    `item_id` INT(11) NOT NULL,
    `user_id` INT(11) NOT NULL,
    `room_id` INT(11) NOT NULL DEFAULT 0,
    PRIMARY KEY (`item_id`),
    KEY `idx_builders_club_items_user_id` (`user_id`),
    KEY `idx_builders_club_items_room_id` (`room_id`)
) ENGINE=InnoDB DEFAULT CHARSET=latin1 COLLATE=latin1_swedish_ci;

ALTER TABLE `catalog_pages_bc`
    MODIFY COLUMN `page_layout` ENUM(
        'default_3x3',
        'club_buy',
        'club_gift',
        'frontpage',
        'spaces',
        'recycler',
        'recycler_info',
        'recycler_prizes',
        'trophies',
        'plasto',
        'marketplace',
        'marketplace_own_items',
        'spaces_new',
        'soundmachine',
        'guilds',
        'guild_furni',
        'info_duckets',
        'info_rentables',
        'info_pets',
        'roomads',
        'single_bundle',
        'sold_ltd_items',
        'badge_display',
        'bots',
        'pets',
        'pets2',
        'pets3',
        'productpage1',
        'room_bundle',
        'recent_purchases',
        'default_3x3_color_grouping',
        'guild_forum',
        'vip_buy',
        'info_loyalty',
        'loyalty_vip_buy',
        'collectibles',
        'petcustomization',
        'frontpage_featured',
        'builders_club_frontpage',
        'builders_club_addons',
        'builders_club_loyalty'
    ) NOT NULL DEFAULT 'default_3x3';
