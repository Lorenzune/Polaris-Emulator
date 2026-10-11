SET @permission_previous_group_concat = @@session.group_concat_max_len;
SET SESSION group_concat_max_len = 1048576;
-- Initialize new keys only; preserve grants already configured by operators.
INSERT IGNORE INTO permission_definitions (permission_key, max_value, comment)
VALUES ('cmd_perm', 1, 'Read permission explanations and audit');
SET @permission_added = ROW_COUNT();
SELECT GROUP_CONCAT(CONCAT('d.`', c.column_name, '` = s.`', c.column_name, '`') ORDER BY r.id SEPARATOR ', ')
INTO @permission_columns
FROM information_schema.columns c JOIN permission_ranks r ON c.column_name = CONCAT('rank_', r.id)
WHERE c.table_schema = DATABASE() AND c.table_name = 'permission_definitions';
SET @permission_sql = IF(@permission_added > 0 AND @permission_columns IS NOT NULL,
    CONCAT('UPDATE permission_definitions d JOIN permission_definitions s ON s.permission_key = ''cmd_update_permissions'' SET ', @permission_columns, ' WHERE d.permission_key = ''cmd_perm'''), 'SELECT 1');
PREPARE permission_stmt FROM @permission_sql;
EXECUTE permission_stmt;
DEALLOCATE PREPARE permission_stmt;

INSERT IGNORE INTO permission_definitions (permission_key, max_value, comment)
VALUES ('cmd_perm_edit', 1, 'Manage per-user permission overrides');
SET @permission_added = ROW_COUNT();
SELECT GROUP_CONCAT(CONCAT('d.`', c.column_name, '` = IF(s.`', c.column_name, '` = 1 AND g.`', c.column_name, '` = 1, 1, 0)') ORDER BY r.id SEPARATOR ', ')
INTO @permission_columns
FROM information_schema.columns c JOIN permission_ranks r ON c.column_name = CONCAT('rank_', r.id)
WHERE c.table_schema = DATABASE() AND c.table_name = 'permission_definitions';
SET @permission_sql = IF(@permission_added > 0 AND @permission_columns IS NOT NULL,
    CONCAT('UPDATE permission_definitions d JOIN permission_definitions s ON s.permission_key = ''cmd_update_permissions'' JOIN permission_definitions g ON g.permission_key = ''cmd_give_rank'' SET ', @permission_columns, ' WHERE d.permission_key = ''cmd_perm_edit'''), 'SELECT 1');
PREPARE permission_stmt FROM @permission_sql;
EXECUTE permission_stmt;
DEALLOCATE PREPARE permission_stmt;

SELECT COUNT(*) INTO @legacy_read_exists FROM information_schema.columns
WHERE table_schema = DATABASE() AND table_name = 'permissions' AND column_name = 'cmd_perm';
SELECT COUNT(*) INTO @legacy_edit_exists FROM information_schema.columns
WHERE table_schema = DATABASE() AND table_name = 'permissions' AND column_name = 'cmd_perm_edit';
ALTER TABLE permissions ADD COLUMN IF NOT EXISTS cmd_perm enum('0','1') NOT NULL DEFAULT '0';
ALTER TABLE permissions ADD COLUMN IF NOT EXISTS cmd_perm_edit enum('0','1') NOT NULL DEFAULT '0';
UPDATE permissions SET cmd_perm = IF(cmd_update_permissions = '1', '1', '0') WHERE @legacy_read_exists = 0;
UPDATE permissions SET cmd_perm_edit = IF(cmd_update_permissions = '1' AND cmd_give_rank = '1', '1', '0') WHERE @legacy_edit_exists = 0;
INSERT IGNORE INTO emulator_texts (`key`, `value`) VALUES
('commands.description.cmd_perm', ':perm check/log/list/set/unset'),
('commands.description.cmd_perm_edit', 'Modifica degli override individuali');

SET SESSION group_concat_max_len = @permission_previous_group_concat;
