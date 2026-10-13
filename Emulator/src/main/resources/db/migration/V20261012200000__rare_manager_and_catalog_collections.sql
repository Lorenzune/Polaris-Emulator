ALTER TABLE catalog_pages ADD COLUMN IF NOT EXISTS rare_page TINYINT UNSIGNED NOT NULL DEFAULT 0;
ALTER TABLE catalog_pages ADD COLUMN IF NOT EXISTS rare_visible TINYINT UNSIGNED NOT NULL DEFAULT 0;
ALTER TABLE collectible_categories MODIFY COLUMN reward_id INT NULL;
ALTER TABLE collectible_categories ADD COLUMN IF NOT EXISTS catalog_page_id INT NULL;
CREATE UNIQUE INDEX IF NOT EXISTS collectible_catalog_page ON collectible_categories(catalog_page_id);

-- Only the rare trees explicitly selected for this hotel are initially marked.
SET @rare_manager_first=NOT EXISTS(SELECT 1 FROM rare_system_metadata WHERE `key`='rare_manager_categories_initialized');
UPDATE catalog_pages p JOIN (
 WITH RECURSIVE rare_tree AS (
  SELECT id FROM catalog_pages WHERE id IN (20004634,20004394,1756693,20004329,20004357)
  UNION DISTINCT SELECT child.id FROM catalog_pages child JOIN rare_tree parent ON child.parent_id=parent.id
 ) SELECT id FROM rare_tree
) selected ON selected.id=p.id SET p.rare_page=1 WHERE @rare_manager_first=1;

-- Permission grants live in permission_definitions.rank_<permission_ranks.id>.
-- Initialize once from existing furniture administration; preserve later custom grants.
SET @rare_previous_group_concat=@@session.group_concat_max_len;
SET SESSION group_concat_max_len=1048576;
INSERT IGNORE INTO permission_definitions(permission_key,max_value,comment)
VALUES ('acc_rare_manager',1,'Manage rare categories, prices, releases and collection rewards.');
SET @rare_permission_added=ROW_COUNT();
SELECT GROUP_CONCAT(CONCAT('d.`',c.column_name,'`=s.`',c.column_name,'`') ORDER BY r.id SEPARATOR ',')
INTO @rare_rank_columns FROM information_schema.columns c
JOIN permission_ranks r ON c.column_name=CONCAT('rank_',r.id)
WHERE c.table_schema=DATABASE() AND c.table_name='permission_definitions';
SET @rare_permission_sql=IF(@rare_permission_added>0 AND @rare_rank_columns IS NOT NULL,
 CONCAT('UPDATE permission_definitions d JOIN permission_definitions s ON s.permission_key=''acc_catalogfurni'' SET ',@rare_rank_columns,' WHERE d.permission_key=''acc_rare_manager'''), 'SELECT 1');
PREPARE rare_permission_stmt FROM @rare_permission_sql;
EXECUTE rare_permission_stmt;
DEALLOCATE PREPARE rare_permission_stmt;
SELECT COUNT(*) INTO @rare_legacy_exists FROM information_schema.columns
WHERE table_schema=DATABASE() AND table_name='permissions' AND column_name='acc_rare_manager';
ALTER TABLE permissions ADD COLUMN IF NOT EXISTS acc_rare_manager enum('0','1') NOT NULL DEFAULT '0';
UPDATE permissions SET acc_rare_manager=acc_catalogfurni WHERE @rare_legacy_exists=0;
SET SESSION group_concat_max_len=@rare_previous_group_concat;
INSERT IGNORE INTO rare_system_metadata(`key`,`value`) VALUES('rare_manager_categories_initialized','1');
