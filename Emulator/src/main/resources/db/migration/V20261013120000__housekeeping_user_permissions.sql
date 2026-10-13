SET @hk_user_previous_group_concat = @@session.group_concat_max_len;
SET SESSION group_concat_max_len = 1048576;

-- A separate rank-controlled capability; existing configured grants survive replay.
INSERT IGNORE INTO permission_definitions (permission_key, max_value, comment)
VALUES ('acc_hk_user_permissions', 1, 'Manage individual user permissions in housekeeping');
SET @hk_user_added = ROW_COUNT();
SELECT GROUP_CONCAT(CONCAT('d.`', c.column_name, '` = IF(s.`', c.column_name,
    '` = 1 AND e.`', c.column_name, '` = 1, 1, 0)') ORDER BY r.id SEPARATOR ', ')
INTO @hk_user_columns
FROM information_schema.columns c JOIN permission_ranks r ON c.column_name = CONCAT('rank_', r.id)
WHERE c.table_schema = DATABASE() AND c.table_name = 'permission_definitions';
SET @hk_user_sql = IF(@hk_user_added > 0 AND @hk_user_columns IS NOT NULL,
    CONCAT('UPDATE permission_definitions d JOIN permission_definitions s ON s.permission_key = ''acc_hk_permissions'' JOIN permission_definitions e ON e.permission_key = ''cmd_perm_edit'' SET ',
        @hk_user_columns, ' WHERE d.permission_key = ''acc_hk_user_permissions'''), 'SELECT 1');
PREPARE hk_user_stmt FROM @hk_user_sql;
EXECUTE hk_user_stmt;
DEALLOCATE PREPARE hk_user_stmt;

SET @hk_user_legacy_exists = (SELECT COUNT(*) FROM information_schema.columns
    WHERE table_schema = DATABASE() AND table_name = 'permissions' AND column_name = 'acc_hk_user_permissions');
ALTER TABLE permissions ADD COLUMN IF NOT EXISTS acc_hk_user_permissions enum('0','1') NOT NULL DEFAULT '0';
UPDATE permissions SET acc_hk_user_permissions = IF(acc_hk_permissions = '1' AND cmd_perm_edit = '1', '1', '0')
WHERE @hk_user_legacy_exists = 0;

SET SESSION group_concat_max_len = @hk_user_previous_group_concat;
