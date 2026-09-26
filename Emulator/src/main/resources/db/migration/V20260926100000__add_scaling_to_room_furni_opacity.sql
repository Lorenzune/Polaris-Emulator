-- Existing hotels may already have room_furni_opacity from an earlier
-- CREATE TABLE IF NOT EXISTS migration that did not add this column.
-- MariaDB's IF NOT EXISTS also makes this safe for clean installs where the
-- preceding table-creation migration already includes scaling.
ALTER TABLE `room_furni_opacity`
    ADD COLUMN IF NOT EXISTS `scaling` SMALLINT UNSIGNED NOT NULL DEFAULT 100;
