-- Add category, icon, and sort_order to roleplay_commands table

ALTER TABLE `roleplay_commands`
    ADD COLUMN IF NOT EXISTS `category` VARCHAR(100) NULL DEFAULT NULL AFTER `name`,
    ADD COLUMN IF NOT EXISTS `icon` VARCHAR(50) NULL DEFAULT '' AFTER `category`,
    ADD COLUMN IF NOT EXISTS `sort_order` INT(11) NOT NULL DEFAULT 0 AFTER `icon`;

UPDATE `roleplay_commands` SET `category` = 'Affetto & Romance', `icon` = '💋', `sort_order` = 1 WHERE `name` = 'Bacio';
UPDATE `roleplay_commands` SET `category` = 'Affetto & Romance', `icon` = '🤗', `sort_order` = 2 WHERE `name` = 'Abbraccio';
UPDATE `roleplay_commands` SET `category` = 'Affetto & Romance', `icon` = '💍', `sort_order` = 3 WHERE `name` = 'Proposta Matrimonio';
UPDATE `roleplay_commands` SET `category` = 'Affetto & Romance', `icon` = '🌹', `sort_order` = 4 WHERE `name` = 'Dona Rosa';
UPDATE `roleplay_commands` SET `category` = 'Azione & Lotta', `icon` = '👋', `sort_order` = 1 WHERE `name` = 'Schiaffo';
UPDATE `roleplay_commands` SET `category` = 'Azione & Lotta', `icon` = '🥊', `sort_order` = 2 WHERE `name` = 'Pugno';
UPDATE `roleplay_commands` SET `category` = 'Social & Svago', `icon` = '🍺', `sort_order` = 1 WHERE `name` = 'Offri Birra';
