-- :update_customicons command
-- Reloads the custom nick icons catalog and updates all connected clients.

-- 1. Command texts
INSERT INTO `emulator_texts` (`key`, `value`) VALUES
    ('commands.description.cmd_update_customicons', ':update_customicons'),
    ('commands.keys.cmd_update_customicons', 'update_customicons;update_custom_icons;update_icons;update_nickicons'),
    ('commands.succes.cmd_update_customicons', 'Catalogo icone personalizzate aggiornato con successo per tutti gli utenti online!')
ON DUPLICATE KEY UPDATE `value` = VALUES(`value`);

-- 2. Normalized permission definitions (Ranks 6 & 7 enabled by default)
INSERT INTO `permission_definitions`
    (`permission_key`, `max_value`, `comment`, `rank_1`, `rank_2`, `rank_3`, `rank_4`, `rank_5`, `rank_6`, `rank_7`)
VALUES
    ('cmd_update_customicons', 1, 'Allows reloading custom nick icons catalog for all online users.', 0, 0, 0, 0, 0, 1, 1)
ON DUPLICATE KEY UPDATE
    `max_value` = VALUES(`max_value`),
    `comment` = VALUES(`comment`),
    `rank_6` = 1,
    `rank_7` = 1;

-- 3. Legacy permissions column if table is present
ALTER TABLE `permissions` ADD COLUMN IF NOT EXISTS `cmd_update_customicons` enum('0','1') NOT NULL DEFAULT '0';
UPDATE `permissions` SET `cmd_update_customicons` = '1' WHERE `level` >= 6;
