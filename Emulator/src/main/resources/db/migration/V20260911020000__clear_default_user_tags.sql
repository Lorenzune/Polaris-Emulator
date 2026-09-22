-- Clear default 'Arcturus Emulator;' tag and update default column value to empty string

ALTER TABLE `users_settings` MODIFY COLUMN `tags` varchar(255) NOT NULL DEFAULT '';

UPDATE `users_settings`
SET `tags` = ''
WHERE `tags` = 'Arcturus Emulator;' OR `tags` = 'Arcturus Emulator';
