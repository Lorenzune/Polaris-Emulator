-- Existing items have unknown provenance; never infer their first owner from their current owner.
ALTER TABLE items
 ADD COLUMN first_owner_id INT NOT NULL DEFAULT 0,
 ADD COLUMN ownership_transferred TINYINT NOT NULL DEFAULT 0;

-- Capture all creation paths, including catalog, bundles and external integrations.
CREATE TRIGGER items_capture_first_owner BEFORE INSERT ON items FOR EACH ROW
 SET NEW.first_owner_id = IF(NEW.user_id > 0, NEW.user_id, 0), NEW.ownership_transferred = 0;

-- Preserve provenance on placement/pickup; a transfer permanently disqualifies the instance.
CREATE TRIGGER items_preserve_first_owner BEFORE UPDATE ON items FOR EACH ROW
 SET NEW.first_owner_id = OLD.first_owner_id,
     NEW.ownership_transferred = IF(OLD.ownership_transferred <> 0 OR NOT (NEW.user_id <=> OLD.user_id), 1, 0);
