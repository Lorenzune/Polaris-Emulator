-- Housekeeping upstream integration; existing grants and deliberate denials are preserved on replay.
-- Permission keys are canonical ASCII identifiers. Binary joins avoid inherited table collation conflicts.
-- Internal staff notes on a user, shown on the user's housekeeping page.
CREATE TABLE IF NOT EXISTS `housekeeping_user_notes` (
  `id` INT NOT NULL AUTO_INCREMENT,
  `user_id` INT NOT NULL,
  `author_id` INT NOT NULL,
  `author_name` VARCHAR(64) NOT NULL DEFAULT '',
  `note` VARCHAR(500) NOT NULL DEFAULT '',
  `created_at` INT NOT NULL,
  PRIMARY KEY (`id`),
  KEY `user_id` (`user_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TEMPORARY TABLE IF NOT EXISTS hk_new_permissions (permission_key VARCHAR(100) PRIMARY KEY);
TRUNCATE TABLE hk_new_permissions;
INSERT INTO hk_new_permissions SELECT 'acc_hk_users' WHERE NOT EXISTS (SELECT 1 FROM permission_definitions WHERE permission_key='acc_hk_users');
INSERT IGNORE INTO permission_definitions (permission_key,max_value,comment) VALUES ('acc_hk_users',1,'Housekeeping: Users.');
INSERT INTO hk_new_permissions SELECT 'acc_hk_bans' WHERE NOT EXISTS (SELECT 1 FROM permission_definitions WHERE permission_key='acc_hk_bans');
INSERT IGNORE INTO permission_definitions (permission_key,max_value,comment) VALUES ('acc_hk_bans',1,'Housekeeping: Bans.');
INSERT INTO hk_new_permissions SELECT 'acc_hk_economy' WHERE NOT EXISTS (SELECT 1 FROM permission_definitions WHERE permission_key='acc_hk_economy');
INSERT IGNORE INTO permission_definitions (permission_key,max_value,comment) VALUES ('acc_hk_economy',1,'Housekeeping: Economy.');
INSERT INTO hk_new_permissions SELECT 'acc_hk_rooms' WHERE NOT EXISTS (SELECT 1 FROM permission_definitions WHERE permission_key='acc_hk_rooms');
INSERT IGNORE INTO permission_definitions (permission_key,max_value,comment) VALUES ('acc_hk_rooms',1,'Housekeeping: Rooms.');
INSERT INTO hk_new_permissions SELECT 'acc_hk_permissions' WHERE NOT EXISTS (SELECT 1 FROM permission_definitions WHERE permission_key='acc_hk_permissions');
INSERT IGNORE INTO permission_definitions (permission_key,max_value,comment) VALUES ('acc_hk_permissions',1,'Housekeeping: Permission matrix.');
INSERT INTO hk_new_permissions SELECT 'acc_hk_hotel' WHERE NOT EXISTS (SELECT 1 FROM permission_definitions WHERE permission_key='acc_hk_hotel');
INSERT IGNORE INTO permission_definitions (permission_key,max_value,comment) VALUES ('acc_hk_hotel',1,'Housekeeping: Hotel tools.');
INSERT INTO hk_new_permissions SELECT 'acc_hk_view_private' WHERE NOT EXISTS (SELECT 1 FROM permission_definitions WHERE permission_key='acc_hk_view_private');
INSERT IGNORE INTO permission_definitions (permission_key,max_value,comment) VALUES ('acc_hk_view_private',1,'Housekeeping: Unmasked IP addresses.');
INSERT INTO hk_new_permissions SELECT 'cmd_maintenance' WHERE NOT EXISTS (SELECT 1 FROM permission_definitions WHERE permission_key='cmd_maintenance');
INSERT IGNORE INTO permission_definitions (permission_key,max_value,comment) VALUES ('cmd_maintenance',1,'Housekeeping: Maintenance from Housekeeping.');

SET @hk_assignments = NULL;
SELECT GROUP_CONCAT(CONCAT('dst.`',c.column_name,'`=IF(src.`',c.column_name,'`>0,1,0)') ORDER BY r.id SEPARATOR ', ')
INTO @hk_assignments FROM information_schema.columns c JOIN permission_ranks r ON c.column_name=CONCAT('rank_',r.id)
WHERE c.table_schema=DATABASE() AND c.table_name='permission_definitions';
SET @hk_sql=IF(@hk_assignments IS NULL,'SELECT 1',CONCAT('UPDATE permission_definitions dst JOIN hk_new_permissions new_keys ON BINARY new_keys.permission_key=BINARY dst.permission_key JOIN permission_definitions src ON src.permission_key=''acc_housekeeping'' SET ',@hk_assignments,' WHERE dst.permission_key<>''acc_hk_view_private'''));
PREPARE hk_statement FROM @hk_sql; EXECUTE hk_statement; DEALLOCATE PREPARE hk_statement;
SET @hk_private_assignments=NULL;
SELECT GROUP_CONCAT(CONCAT('dst.`',c.column_name,'`=1') ORDER BY r.id SEPARATOR ', ')
INTO @hk_private_assignments FROM information_schema.columns c JOIN permission_ranks r ON c.column_name=CONCAT('rank_',r.id)
WHERE c.table_schema=DATABASE() AND c.table_name='permission_definitions' AND r.level>=7;
SET @hk_sql=IF(@hk_private_assignments IS NULL,'SELECT 1',CONCAT('UPDATE permission_definitions dst JOIN hk_new_permissions new_keys ON BINARY new_keys.permission_key=BINARY dst.permission_key SET ',@hk_private_assignments,' WHERE dst.permission_key=''acc_hk_view_private'''));
PREPARE hk_statement FROM @hk_sql; EXECUTE hk_statement; DEALLOCATE PREPARE hk_statement;
DROP TEMPORARY TABLE hk_new_permissions;

-- The legacy permissions table need not have acc_housekeeping; seed from the canonical definitions.
SET @hk_legacy_cases=NULL;
SELECT GROUP_CONCAT(CONCAT('WHEN ',r.id,' THEN IF(src.`',c.column_name,'`>0,''1'',''0'')') ORDER BY r.id SEPARATOR ' ')
INTO @hk_legacy_cases FROM information_schema.columns c JOIN permission_ranks r ON c.column_name=CONCAT('rank_',r.id)
WHERE c.table_schema=DATABASE() AND c.table_name='permission_definitions';

SET @hk_legacy_exists=(SELECT COUNT(*) FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='permissions' AND column_name='acc_hk_users');
ALTER TABLE permissions ADD COLUMN IF NOT EXISTS `acc_hk_users` ENUM('0','1') NOT NULL DEFAULT '0';
SET @hk_sql=IF(@hk_legacy_exists<>0 OR @hk_legacy_cases IS NULL,'SELECT 1',CONCAT('UPDATE permissions dst JOIN permission_definitions src ON src.permission_key=''acc_hk_users'' SET dst.`acc_hk_users`=CASE dst.id ',@hk_legacy_cases,' ELSE ''0'' END'));
PREPARE hk_statement FROM @hk_sql; EXECUTE hk_statement; DEALLOCATE PREPARE hk_statement;

SET @hk_legacy_exists=(SELECT COUNT(*) FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='permissions' AND column_name='acc_hk_bans');
ALTER TABLE permissions ADD COLUMN IF NOT EXISTS `acc_hk_bans` ENUM('0','1') NOT NULL DEFAULT '0';
SET @hk_sql=IF(@hk_legacy_exists<>0 OR @hk_legacy_cases IS NULL,'SELECT 1',CONCAT('UPDATE permissions dst JOIN permission_definitions src ON src.permission_key=''acc_hk_bans'' SET dst.`acc_hk_bans`=CASE dst.id ',@hk_legacy_cases,' ELSE ''0'' END'));
PREPARE hk_statement FROM @hk_sql; EXECUTE hk_statement; DEALLOCATE PREPARE hk_statement;

SET @hk_legacy_exists=(SELECT COUNT(*) FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='permissions' AND column_name='acc_hk_economy');
ALTER TABLE permissions ADD COLUMN IF NOT EXISTS `acc_hk_economy` ENUM('0','1') NOT NULL DEFAULT '0';
SET @hk_sql=IF(@hk_legacy_exists<>0 OR @hk_legacy_cases IS NULL,'SELECT 1',CONCAT('UPDATE permissions dst JOIN permission_definitions src ON src.permission_key=''acc_hk_economy'' SET dst.`acc_hk_economy`=CASE dst.id ',@hk_legacy_cases,' ELSE ''0'' END'));
PREPARE hk_statement FROM @hk_sql; EXECUTE hk_statement; DEALLOCATE PREPARE hk_statement;

SET @hk_legacy_exists=(SELECT COUNT(*) FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='permissions' AND column_name='acc_hk_rooms');
ALTER TABLE permissions ADD COLUMN IF NOT EXISTS `acc_hk_rooms` ENUM('0','1') NOT NULL DEFAULT '0';
SET @hk_sql=IF(@hk_legacy_exists<>0 OR @hk_legacy_cases IS NULL,'SELECT 1',CONCAT('UPDATE permissions dst JOIN permission_definitions src ON src.permission_key=''acc_hk_rooms'' SET dst.`acc_hk_rooms`=CASE dst.id ',@hk_legacy_cases,' ELSE ''0'' END'));
PREPARE hk_statement FROM @hk_sql; EXECUTE hk_statement; DEALLOCATE PREPARE hk_statement;

SET @hk_legacy_exists=(SELECT COUNT(*) FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='permissions' AND column_name='acc_hk_permissions');
ALTER TABLE permissions ADD COLUMN IF NOT EXISTS `acc_hk_permissions` ENUM('0','1') NOT NULL DEFAULT '0';
SET @hk_sql=IF(@hk_legacy_exists<>0 OR @hk_legacy_cases IS NULL,'SELECT 1',CONCAT('UPDATE permissions dst JOIN permission_definitions src ON src.permission_key=''acc_hk_permissions'' SET dst.`acc_hk_permissions`=CASE dst.id ',@hk_legacy_cases,' ELSE ''0'' END'));
PREPARE hk_statement FROM @hk_sql; EXECUTE hk_statement; DEALLOCATE PREPARE hk_statement;

SET @hk_legacy_exists=(SELECT COUNT(*) FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='permissions' AND column_name='acc_hk_hotel');
ALTER TABLE permissions ADD COLUMN IF NOT EXISTS `acc_hk_hotel` ENUM('0','1') NOT NULL DEFAULT '0';
SET @hk_sql=IF(@hk_legacy_exists<>0 OR @hk_legacy_cases IS NULL,'SELECT 1',CONCAT('UPDATE permissions dst JOIN permission_definitions src ON src.permission_key=''acc_hk_hotel'' SET dst.`acc_hk_hotel`=CASE dst.id ',@hk_legacy_cases,' ELSE ''0'' END'));
PREPARE hk_statement FROM @hk_sql; EXECUTE hk_statement; DEALLOCATE PREPARE hk_statement;

SET @hk_legacy_exists=(SELECT COUNT(*) FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='permissions' AND column_name='acc_hk_view_private');
ALTER TABLE permissions ADD COLUMN IF NOT EXISTS `acc_hk_view_private` ENUM('0','1') NOT NULL DEFAULT '0';
SET @hk_sql=IF(@hk_legacy_exists<>0 OR @hk_legacy_cases IS NULL,'SELECT 1',CONCAT('UPDATE permissions dst JOIN permission_definitions src ON src.permission_key=''acc_hk_view_private'' SET dst.`acc_hk_view_private`=CASE dst.id ',@hk_legacy_cases,' ELSE ''0'' END'));
PREPARE hk_statement FROM @hk_sql; EXECUTE hk_statement; DEALLOCATE PREPARE hk_statement;

SET @hk_legacy_exists=(SELECT COUNT(*) FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='permissions' AND column_name='cmd_maintenance');
ALTER TABLE permissions ADD COLUMN IF NOT EXISTS `cmd_maintenance` ENUM('0','1') NOT NULL DEFAULT '0';
SET @hk_sql=IF(@hk_legacy_exists<>0 OR @hk_legacy_cases IS NULL,'SELECT 1',CONCAT('UPDATE permissions dst JOIN permission_definitions src ON src.permission_key=''cmd_maintenance'' SET dst.`cmd_maintenance`=CASE dst.id ',@hk_legacy_cases,' ELSE ''0'' END'));
PREPARE hk_statement FROM @hk_sql; EXECUTE hk_statement; DEALLOCATE PREPARE hk_statement;
