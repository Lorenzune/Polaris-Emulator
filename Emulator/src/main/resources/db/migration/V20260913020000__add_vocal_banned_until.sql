ALTER TABLE `users_settings` ADD COLUMN IF NOT EXISTS `vocal_banned_until` int(11) NOT NULL DEFAULT 0;

INSERT INTO `emulator_texts` (`key`, `value`) VALUES
    ('commands.description.cmd_vocalban', ':vocalban <username> [ore / perm]'),
    ('commands.keys.cmd_vocalban', 'vocalban;banvocal;voiceban'),
    ('commands.description.cmd_unvocalban', ':unvocalban <username>'),
    ('commands.keys.cmd_unvocalban', 'unvocalban;unbanvocal;unvoiceban')
ON DUPLICATE KEY UPDATE `value` = VALUES(`value`);

INSERT INTO `permission_definitions`
    (`permission_key`, `max_value`, `comment`, `rank_1`, `rank_2`, `rank_3`, `rank_4`, `rank_5`, `rank_6`, `rank_7`)
VALUES
    ('cmd_vocalban', 1, 'Controls access to the :vocalban command.', 0, 0, 0, 0, 1, 1, 1),
    ('cmd_unvocalban', 1, 'Controls access to the :unvocalban command.', 0, 0, 0, 0, 1, 1, 1)
ON DUPLICATE KEY UPDATE
    `max_value` = VALUES(`max_value`),
    `comment` = VALUES(`comment`),
    `rank_5` = 1,
    `rank_6` = 1,
    `rank_7` = 1;

ALTER TABLE `permissions` ADD COLUMN IF NOT EXISTS `cmd_vocalban` enum('0','1','2') NOT NULL DEFAULT '0';
ALTER TABLE `permissions` ADD COLUMN IF NOT EXISTS `cmd_unvocalban` enum('0','1','2') NOT NULL DEFAULT '0';

UPDATE `permissions` SET `cmd_vocalban` = '1', `cmd_unvocalban` = '1' WHERE `level` >= 5 OR `id` >= 5;
