-- Expand items extra_data column and ensure ads_bg items interaction_type

SET @dbname = DATABASE();
SET @tablename = "items";
SET @indexname = "extra_data";
SET @preparedStatement = (SELECT IF(
  (
    SELECT COUNT(*) FROM INFORMATION_SCHEMA.STATISTICS
    WHERE
      (table_name = @tablename)
      AND (table_schema = @dbname)
      AND (index_name = @indexname)
  ) > 0,
  'ALTER TABLE `items` DROP INDEX `extra_data`;',
  'SELECT 1;'
));
PREPARE stmt FROM @preparedStatement;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

ALTER TABLE `items` MODIFY COLUMN `extra_data` TEXT NOT NULL;

-- Ensure room background and advertisement items are set to ads_bg interaction_type
UPDATE `items_base` 
SET `interaction_type` = 'ads_bg' 
WHERE `item_name` IN ('ads_background', 'ads_mpu_720', 'ads_mpu_300', 'ads_mpu_160', 'mutearea_sign', 'buildarea_sign');
