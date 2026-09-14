-- Permission for uploading custom group badges
INSERT INTO `permission_definitions`
    (`permission_key`, `max_value`, `comment`, `rank_1`, `rank_2`, `rank_3`, `rank_4`, `rank_5`, `rank_6`, `rank_7`)
VALUES
    ('acc_custom_group_badge', 1, 'Allows uploading a custom group badge image.', 0, 0, 0, 0, 0, 1, 1)
ON DUPLICATE KEY UPDATE
    `max_value` = VALUES(`max_value`),
    `comment` = VALUES(`comment`),
    `rank_6` = 1,
    `rank_7` = 1;

ALTER TABLE `permissions` ADD COLUMN IF NOT EXISTS `acc_custom_group_badge` enum('0','1') NOT NULL DEFAULT '0';
UPDATE `permissions` SET `acc_custom_group_badge` = '1' WHERE `level` >= 6;
