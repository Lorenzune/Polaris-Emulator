CREATE TABLE IF NOT EXISTS `navigator_room_order` (
    `list_key` varchar(64) NOT NULL,
    `room_id` int(11) NOT NULL,
    `order_num` int(11) NOT NULL DEFAULT 0,
    PRIMARY KEY (`list_key`, `room_id`),
    KEY `idx_navigator_room_order_position` (`list_key`, `order_num`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

INSERT INTO `permission_definitions`
    (`permission_key`, `max_value`, `comment`, `rank_1`, `rank_2`, `rank_3`, `rank_4`, `rank_5`, `rank_6`, `rank_7`)
VALUES
    ('acc_navigator_room_order', 1, 'Allows rank 7 staff to reorder public navigator rooms and staff picks.', 0, 0, 0, 0, 0, 0, 1)
ON DUPLICATE KEY UPDATE
    `max_value` = VALUES(`max_value`),
    `comment` = VALUES(`comment`),
    `rank_1` = VALUES(`rank_1`),
    `rank_2` = VALUES(`rank_2`),
    `rank_3` = VALUES(`rank_3`),
    `rank_4` = VALUES(`rank_4`),
    `rank_5` = VALUES(`rank_5`),
    `rank_6` = VALUES(`rank_6`),
    `rank_7` = VALUES(`rank_7`);

ALTER TABLE `permissions`
    ADD COLUMN IF NOT EXISTS `acc_navigator_room_order` enum('0', '1') NOT NULL DEFAULT '0';

UPDATE `permissions`
SET `acc_navigator_room_order` = '1'
WHERE `level` = 7;
