SET @hk_toolbar_previous_group_concat = @@session.group_concat_max_len;
SET SESSION group_concat_max_len = 1048576;

-- A separate rank-controlled capability; existing configured grants survive replay.
INSERT IGNORE INTO permission_definitions (permission_key, max_value, comment)
VALUES ('acc_hk_toolbar', 1, 'Manage global toolbar currencies and widgets');
SET @hk_toolbar_added = ROW_COUNT();
SELECT GROUP_CONCAT(CONCAT('d.`', c.column_name, '` = IF(s.`', c.column_name,
    '` = 1, 1, 0)') ORDER BY r.id SEPARATOR ', ')
INTO @hk_toolbar_columns
FROM information_schema.columns c JOIN permission_ranks r ON c.column_name = CONCAT('rank_', r.id)
WHERE c.table_schema = DATABASE() AND c.table_name = 'permission_definitions';
SET @hk_toolbar_sql = IF(@hk_toolbar_added > 0 AND @hk_toolbar_columns IS NOT NULL,
    CONCAT('UPDATE permission_definitions d JOIN permission_definitions s ON s.permission_key = ''acc_hk_hotel'' SET ',
        @hk_toolbar_columns, ' WHERE d.permission_key = ''acc_hk_toolbar'''), 'SELECT 1');
PREPARE hk_toolbar_stmt FROM @hk_toolbar_sql;
EXECUTE hk_toolbar_stmt;
DEALLOCATE PREPARE hk_toolbar_stmt;

SET @hk_toolbar_legacy_exists = (SELECT COUNT(*) FROM information_schema.columns
    WHERE table_schema = DATABASE() AND table_name = 'permissions' AND column_name = 'acc_hk_toolbar');
ALTER TABLE permissions ADD COLUMN IF NOT EXISTS acc_hk_toolbar enum('0','1') NOT NULL DEFAULT '0';
UPDATE permissions SET acc_hk_toolbar = IF(acc_hk_hotel = '1', '1', '0')
WHERE @hk_toolbar_legacy_exists = 0;

SET SESSION group_concat_max_len = @hk_toolbar_previous_group_concat;

CREATE TABLE IF NOT EXISTS toolbar_configuration (
    id TINYINT NOT NULL PRIMARY KEY,
    revision INT NOT NULL DEFAULT 0,
    configuration MEDIUMTEXT NULL,
    updated_by INT NOT NULL DEFAULT 0,
    updated_at INT NOT NULL DEFAULT 0
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
INSERT IGNORE INTO toolbar_configuration(id) VALUES(1);
