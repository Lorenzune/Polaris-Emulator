-- :update_externaltexts command
-- Broadcasts ExternalTexts reload packet with timestamp to all connected clients so new badge names & descriptions appear immediately.

INSERT INTO `emulator_texts` (`key`, `value`) VALUES
    ('commands.description.cmd_update_externaltexts', ':update_externaltexts'),
    ('commands.keys.cmd_update_externaltexts', 'update_externaltexts;update_external_texts;update_texts_external'),
    ('commands.succes.cmd_update_externaltexts', 'ExternalTexts ricaricato con successo con timestamp e sincronizzato per tutti i giocatori online!')
ON DUPLICATE KEY UPDATE `value` = VALUES(`value`);

INSERT INTO `permission_definitions`
    (`permission_key`, `max_value`, `comment`, `rank_1`, `rank_2`, `rank_3`, `rank_4`, `rank_5`, `rank_6`, `rank_7`)
VALUES
    ('cmd_update_externaltexts', 1, 'Allows reloading ExternalTexts for all online clients with fresh timestamp.', 0, 0, 0, 0, 0, 1, 1)
ON DUPLICATE KEY UPDATE
    `max_value` = VALUES(`max_value`),
    `comment` = VALUES(`comment`),
    `rank_6` = 1,
    `rank_7` = 1;

ALTER TABLE `permissions` ADD COLUMN IF NOT EXISTS `cmd_update_externaltexts` enum('0','1') NOT NULL DEFAULT '0';
UPDATE `permissions` SET `cmd_update_externaltexts` = '1' WHERE `level` >= 6;
