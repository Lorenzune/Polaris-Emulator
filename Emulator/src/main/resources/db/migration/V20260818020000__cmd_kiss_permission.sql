-- Ensure cmd_kiss column exists in permissions table and is set to '1' for all ranks
ALTER TABLE `permissions` ADD COLUMN IF NOT EXISTS `cmd_kiss` enum('0','1','2') NOT NULL DEFAULT '1';

UPDATE `permissions` SET `cmd_kiss` = '1';

-- Add or update emulator_texts for cmd_kiss
INSERT INTO `emulator_texts` (`key`, `value`) VALUES
('commands.keys.cmd_kiss', 'bacia;kiss;frenchkiss'),
('commands.description.cmd_kiss', ':bacia <username>'),
('commands.error.cmd_kiss.forgot_username', 'Specifica il nome dell''utente che vuoi baciare!'),
('commands.error.cmd_kiss.not_found', 'Utente %user% non trovato in questa stanza!'),
('commands.error.cmd_kiss.self', 'Non puoi baciarti da solo!'),
('commands.error.cmd_kiss.cant_reach', 'Sei troppo lontano da %user% per baciarlo!')
ON DUPLICATE KEY UPDATE `value` = VALUES(`value`);
