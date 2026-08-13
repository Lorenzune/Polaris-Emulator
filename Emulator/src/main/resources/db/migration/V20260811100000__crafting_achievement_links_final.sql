-- Rebind station-specific SWF achievements after the import-only source columns
-- have been removed by V20260811090000.  The values are JSON arrays, so the
-- boundary-aware expression also works with MariaDB's LONGTEXT representation.
UPDATE `crafting_recipes`
SET `achievement` = CASE
    WHEN `station_ids` REGEXP '(^|[^0-9])10622([^0-9]|$)' THEN 'WitchesCoven'
    WHEN `station_ids` REGEXP '(^|[^0-9])9226([^0-9]|$)' THEN 'Chef'
    WHEN `station_ids` REGEXP '(^|[^0-9])9846([^0-9]|$)' THEN 'ffusion'
    WHEN `station_ids` REGEXP '(^|[^0-9])13711([^0-9]|$)' THEN 'victailor'
    WHEN `station_ids` REGEXP '(^|[^0-9])14495([^0-9]|$)' THEN 'wintercity'
    WHEN `station_ids` REGEXP '(^|[^0-9])10725([^0-9]|$)' THEN 'Xmascrystal'
    WHEN `station_ids` REGEXP '(^|[^0-9])9417([^0-9]|$)' THEN 'bazaar17'
    WHEN `station_ids` REGEXP '(^|[^0-9])9988([^0-9]|$)' THEN 'Tokyo'
    WHEN `station_ids` REGEXP '(^|[^0-9])10448([^0-9]|$)' THEN 'Festival'
    WHEN `station_ids` REGEXP '(^|[^0-9])10546([^0-9]|$)' THEN 'Suncity'
    WHEN `station_ids` REGEXP '(^|[^0-9])11019([^0-9]|$)' THEN 'india'
    ELSE `achievement`
END
WHERE `enabled` = '1';
