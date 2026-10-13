-- Preserve the previous reference fields before converting the currency label to its wallet ID.
CREATE TABLE IF NOT EXISTS rare_system_metadata (`key` VARCHAR(64) PRIMARY KEY, `value` VARCHAR(255) NOT NULL) ENGINE=InnoDB;
SET @rare_upgrade_first=NOT EXISTS(SELECT 1 FROM rare_system_metadata WHERE `key`='reference_currency_upgrade');
CREATE TABLE IF NOT EXISTS rare_reference_upgrade_backup (
 base_item_id INT UNSIGNED PRIMARY KEY, reference_price DECIMAL(12,2) NOT NULL,
 currency_label VARCHAR(32) NOT NULL
) ENGINE=InnoDB;
INSERT IGNORE INTO rare_reference_upgrade_backup
 SELECT id, `value`, CAST(value_type AS CHAR) FROM items_base;

CREATE TABLE IF NOT EXISTS rare_currency_types (
 id INT PRIMARY KEY, label VARCHAR(64) NOT NULL, enabled TINYINT NOT NULL DEFAULT 1
) ENGINE=InnoDB;
INSERT IGNORE INTO rare_currency_types(id,label) VALUES (-1,'Crediti'),(0,'Duckets'),(4,'Conchiglie'),(5,'Diamanti');
INSERT IGNORE INTO rare_currency_types(id,label)
 SELECT DISTINCT type, CONCAT('Valuta ',type) FROM users_currency WHERE type>=0;
ALTER TABLE items_base ADD COLUMN IF NOT EXISTS catalog_price DECIMAL(12,2) UNSIGNED NOT NULL DEFAULT 0;
UPDATE items_base b JOIN rare_reference_upgrade_backup old ON old.base_item_id=b.id
 SET b.catalog_price=old.reference_price,
 b.value_type=CASE
 WHEN TRIM(old.currency_label) REGEXP '^-?[0-9]+$' THEN CAST(old.currency_label AS SIGNED)
 WHEN LOWER(TRIM(old.currency_label)) IN ('diamanti','diamonds','diamond') THEN 5
 WHEN LOWER(TRIM(old.currency_label)) IN ('ducket','duckets') THEN 0
 WHEN LOWER(TRIM(old.currency_label)) IN ('conchiglie','shell','shells') THEN 4
 ELSE -1 END WHERE @rare_upgrade_first=1;
ALTER TABLE items_base MODIFY COLUMN value_type INT NOT NULL DEFAULT -1;
INSERT IGNORE INTO rare_currency_types(id,label)
 SELECT DISTINCT value_type, CONCAT('Valuta ',value_type) FROM items_base WHERE value_type>=0;

-- Marketplace offers retain their currency independently of future hotel settings.
SET @legacy_market_currency=(SELECT CAST(`value` AS SIGNED) FROM emulator_settings WHERE `key`='hotel.marketplace.currency' LIMIT 1);
SET @legacy_market_currency=IFNULL(NULLIF(@legacy_market_currency,0),-1);
ALTER TABLE marketplace_items ADD COLUMN IF NOT EXISTS currency_type INT NOT NULL DEFAULT -1;
UPDATE marketplace_items m LEFT JOIN logs_economy e ON e.operation_id=CONCAT('marketplace:offer:',m.id,':buyer')
 SET m.currency_type=COALESCE(e.currency_type,@legacy_market_currency) WHERE @rare_upgrade_first=1;
ALTER TABLE marketplace_items ENGINE=InnoDB;
ALTER TABLE logs_shop_purchases ENGINE=InnoDB;

CREATE TABLE IF NOT EXISTS rare_emissions (
 item_id INT PRIMARY KEY, base_item_id INT UNSIGNED NOT NULL, source VARCHAR(24) NOT NULL,
 issuer_id INT NOT NULL DEFAULT 0, issued_at BIGINT NOT NULL, catalog_offer_id INT NOT NULL DEFAULT 0, cancelled TINYINT NOT NULL DEFAULT 0,
 KEY rare_emissions_base(base_item_id)
) ENGINE=InnoDB;
CREATE TABLE IF NOT EXISTS rare_emission_values (
 item_id INT NOT NULL, currency_type INT NOT NULL, unit_value DECIMAL(20,6) NULL,
 PRIMARY KEY(item_id,currency_type)
) ENGINE=InnoDB;
CREATE TABLE IF NOT EXISTS rare_market_sales (
 offer_id INT PRIMARY KEY, item_id INT NOT NULL, base_item_id INT UNSIGNED NOT NULL,
 seller_id INT NOT NULL, buyer_id INT NOT NULL DEFAULT 0, currency_type INT NOT NULL,
 unit_price INT NOT NULL, sold_at BIGINT NOT NULL, limited_data VARCHAR(32) NOT NULL DEFAULT '0:0',
 KEY rare_market_base_currency(base_item_id,currency_type)
) ENGINE=InnoDB;
CREATE TABLE IF NOT EXISTS rare_catalog_releases (
 id INT AUTO_INCREMENT PRIMARY KEY, catalog_offer_id INT NOT NULL, base_item_id INT UNSIGNED NOT NULL,
 price INT NOT NULL, currency_type INT NOT NULL, starts_at BIGINT NOT NULL, ends_at BIGINT NOT NULL,
 repeat_mode VARCHAR(8) NOT NULL DEFAULT 'none', enabled TINYINT NOT NULL DEFAULT 1,
 created_by INT NOT NULL, created_at BIGINT NOT NULL,
 KEY rare_release_offer(catalog_offer_id), KEY rare_release_base(base_item_id)
) ENGINE=InnoDB;

-- Existing copies form an explicitly labelled reference baseline. No historical payment is invented.
INSERT IGNORE INTO rare_emissions(item_id,base_item_id,source,issuer_id,issued_at)
 SELECT i.id,b.id,'legacy_reference',i.user_id,UNIX_TIMESTAMP() FROM items i
 JOIN items_base b ON b.id=i.item_id WHERE b.is_rare=1;
INSERT IGNORE INTO rare_emission_values(item_id,currency_type,unit_value)
 SELECT e.item_id,b.value_type,NULLIF(b.catalog_price,0) FROM rare_emissions e
 JOIN items_base b ON b.id=e.base_item_id WHERE @rare_upgrade_first=1;
INSERT IGNORE INTO rare_market_sales(offer_id,item_id,base_item_id,seller_id,buyer_id,currency_type,unit_price,sold_at,limited_data)
 SELECT m.id,m.item_id,i.item_id,m.user_id,COALESCE(l.user_id,0),m.currency_type,m.price,
 IF(m.sold_timestamp>0,m.sold_timestamp,m.timestamp),i.limited_data FROM marketplace_items m
 JOIN items i ON i.id=m.item_id
 LEFT JOIN logs_economy l ON l.operation_id=CONCAT('marketplace:offer:',m.id,':buyer') WHERE m.state=2;

DELIMITER //
CREATE OR REPLACE TRIGGER rare_reference_insert BEFORE INSERT ON items_base FOR EACH ROW
BEGIN
 IF NEW.catalog_price>0 THEN SET NEW.`value`=NEW.catalog_price;
 ELSEIF NEW.`value`>0 THEN SET NEW.catalog_price=NEW.`value`; END IF;
END//
CREATE OR REPLACE TRIGGER rare_items_emission_insert AFTER INSERT ON items FOR EACH ROW
BEGIN
 INSERT IGNORE INTO rare_emissions(item_id,base_item_id,source,issuer_id,issued_at)
 SELECT NEW.id,b.id,'generated',NEW.user_id,UNIX_TIMESTAMP() FROM items_base b WHERE b.id=NEW.item_id AND b.is_rare=1;
 INSERT IGNORE INTO rare_emission_values(item_id,currency_type,unit_value)
 SELECT NEW.id,b.value_type,NULLIF(b.catalog_price,0) FROM items_base b WHERE b.id=NEW.item_id AND b.is_rare=1;
END//
CREATE OR REPLACE TRIGGER rare_items_market_buyer AFTER UPDATE ON items FOR EACH ROW
BEGIN
 IF OLD.user_id=-1 AND NEW.user_id>0 THEN
  UPDATE rare_market_sales SET buyer_id=NEW.user_id WHERE item_id=NEW.id AND buyer_id=0;
 END IF;
END//
CREATE OR REPLACE TRIGGER rare_reference_sync BEFORE UPDATE ON items_base FOR EACH ROW
BEGIN
 IF NEW.catalog_price<>OLD.catalog_price THEN SET NEW.`value`=NEW.catalog_price;
 ELSEIF NEW.`value`<>OLD.`value` THEN SET NEW.catalog_price=NEW.`value`; END IF;
END//
CREATE OR REPLACE TRIGGER rare_reference_enabled AFTER UPDATE ON items_base FOR EACH ROW
BEGIN
 IF NEW.is_rare=1 AND OLD.is_rare<>1 THEN
  INSERT INTO rare_system_metadata VALUES('catalog_history_revision','1')
   ON DUPLICATE KEY UPDATE `value`=CAST(`value` AS UNSIGNED)+1;
  INSERT IGNORE INTO rare_emissions(item_id,base_item_id,source,issuer_id,issued_at)
   SELECT id,NEW.id,'legacy_reference',user_id,UNIX_TIMESTAMP() FROM items WHERE item_id=NEW.id;
  INSERT IGNORE INTO rare_emission_values(item_id,currency_type,unit_value)
   SELECT e.item_id,NEW.value_type,NULLIF(NEW.catalog_price,0) FROM rare_emissions e
   WHERE e.base_item_id=NEW.id AND NOT EXISTS(SELECT 1 FROM rare_emission_values v WHERE v.item_id=e.item_id);
 END IF;
END//
CREATE OR REPLACE TRIGGER rare_catalog_purchase_insert AFTER INSERT ON logs_shop_purchases FOR EACH ROW
BEGIN
 DECLARE copies INT DEFAULT 0;
 DECLARE remaining_ids TEXT;
 DECLARE token TEXT;
 DECLARE current_id INT;
 IF IFNULL(NEW.cost_credits,0)>0 OR IFNULL(NEW.cost_points,0)>0 THEN
  SET remaining_ids=REPLACE(IFNULL(NEW.item_ids,''),';',',');
  WHILE remaining_ids<>'' DO
   SET token=TRIM(SUBSTRING_INDEX(remaining_ids,',',1));
   SET remaining_ids=IF(INSTR(remaining_ids,',')>0,SUBSTRING(remaining_ids,INSTR(remaining_ids,',')+1),'');
   IF token REGEXP '^[0-9]+$' AND CAST(token AS UNSIGNED)>0 THEN SET copies=copies+1; END IF;
  END WHILE;
  IF copies>0 THEN
   SET remaining_ids=REPLACE(NEW.item_ids,';',',');
   WHILE remaining_ids<>'' DO
    SET token=TRIM(SUBSTRING_INDEX(remaining_ids,',',1));
    SET remaining_ids=IF(INSTR(remaining_ids,',')>0,SUBSTRING(remaining_ids,INSTR(remaining_ids,',')+1),'');
    IF token REGEXP '^[0-9]+$' AND CAST(token AS UNSIGNED)>0 THEN
     SET current_id=CAST(token AS UNSIGNED);
     IF EXISTS(SELECT 1 FROM rare_emissions WHERE item_id=current_id AND source IN ('generated','legacy_reference') AND cancelled=0) THEN
      DELETE FROM rare_emission_values WHERE item_id=current_id;
      UPDATE rare_emissions SET source='catalog',issuer_id=NEW.user_id,issued_at=NEW.timestamp,catalog_offer_id=NEW.catalog_item_id WHERE item_id=current_id;
      IF IFNULL(NEW.cost_credits,0)>0 THEN
       INSERT INTO rare_emission_values VALUES(current_id,-1,NEW.cost_credits/copies);
      END IF;
      IF IFNULL(NEW.cost_points,0)>0 THEN
       INSERT INTO rare_emission_values VALUES(current_id,NEW.points_type,NEW.cost_points/copies);
      END IF;
     END IF;
    END IF;
   END WHILE;
  END IF;
 END IF;
END//
CREATE OR REPLACE TRIGGER rare_market_sale_update AFTER UPDATE ON marketplace_items FOR EACH ROW
BEGIN
 IF NEW.state=2 AND OLD.state<>2 THEN
  INSERT IGNORE INTO rare_market_sales(offer_id,item_id,base_item_id,seller_id,currency_type,unit_price,sold_at,limited_data)
   SELECT NEW.id,NEW.item_id,item_id,NEW.user_id,NEW.currency_type,NEW.price,NEW.sold_timestamp,limited_data
   FROM items WHERE id=NEW.item_id;
 END IF;
END//
DELIMITER ;
INSERT IGNORE INTO rare_system_metadata VALUES ('reference_currency_upgrade','1');
