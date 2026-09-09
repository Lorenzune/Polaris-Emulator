-- :update_customizeprofile command
-- Reloads custom chat bubbles, nick icons, custom prefixes, and nick styles, broadcasting to all connected clients.

-- 1. Command texts (strictly :update_customizeprofile without aliases)
INSERT INTO `emulator_texts` (`key`, `value`) VALUES
    ('commands.description.cmd_update_customizeprofile', ':update_customizeprofile'),
    ('commands.keys.cmd_update_customizeprofile', 'update_customizeprofile'),
    ('commands.succes.cmd_update_customizeprofile', 'Profilo di personalizzazione (fumetti, icone, prefissi e stili nick) aggiornato con successo per tutti i giocatori online!')
ON DUPLICATE KEY UPDATE `value` = VALUES(`value`);

-- 2. Normalized permission definitions (Ranks 6 & 7 enabled by default)
INSERT INTO `permission_definitions`
    (`permission_key`, `max_value`, `comment`, `rank_1`, `rank_2`, `rank_3`, `rank_4`, `rank_5`, `rank_6`, `rank_7`)
VALUES
    ('cmd_update_customizeprofile', 1, 'Allows reloading custom profile identity catalogs (bubbles, icons, prefixes, nicks) for all online users.', 0, 0, 0, 0, 0, 1, 1)
ON DUPLICATE KEY UPDATE
    `max_value` = VALUES(`max_value`),
    `comment` = VALUES(`comment`),
    `rank_6` = 1,
    `rank_7` = 1;

-- 3. Legacy permissions column if table is present
ALTER TABLE `permissions` ADD COLUMN IF NOT EXISTS `cmd_update_customizeprofile` enum('0','1') NOT NULL DEFAULT '0';
UPDATE `permissions` SET `cmd_update_customizeprofile` = '1' WHERE `level` >= 6;
