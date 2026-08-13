-- Complete the SWF achievement links for the JSON crafting catalog and the
-- crackable furniture catalog.  The runtime already dispatches these fields:
-- crafting_recipes.achievement is dispatched when a recipe is crafted, while
-- items_crackable.achievement_cracked is dispatched after the crack finishes.

-- Crafting achievements are tied to the station used by the SWF recipe pages.
-- station_ids is a JSON array, but REGEXP keeps this migration compatible with
-- both MySQL and MariaDB installations used by the emulator.
UPDATE `crafting_recipes`
SET `achievement` = CASE
    WHEN `station_ids` REGEXP '(^|[^0-9])9603([^0-9]|$)' THEN 'bazaar17'
    WHEN `station_ids` REGEXP '(^|[^0-9])9412([^0-9]|$)' THEN 'Chef'
    WHEN `station_ids` REGEXP '(^|[^0-9])10634([^0-9]|$)' THEN 'Festival'
    WHEN `station_ids` REGEXP '(^|[^0-9])10032([^0-9]|$)' THEN 'ffusion'
    WHEN `station_ids` REGEXP '(^|[^0-9])11205([^0-9]|$)' THEN 'india'
    WHEN `station_ids` REGEXP '(^|[^0-9])10732([^0-9]|$)' THEN 'Suncity'
    WHEN `station_ids` REGEXP '(^|[^0-9])10174([^0-9]|$)' THEN 'Tokyo'
    WHEN `station_ids` REGEXP '(^|[^0-9])9794([^0-9]|$)' THEN 'victailor'
    WHEN `station_ids` REGEXP '(^|[^0-9])10394([^0-9]|$)' THEN 'wintercity'
    WHEN `station_ids` REGEXP '(^|[^0-9])10808([^0-9]|$)' THEN 'WitchesCoven'
    WHEN `station_ids` REGEXP '(^|[^0-9])10911([^0-9]|$)' THEN 'Xmascrystal'
    ELSE `achievement`
END
WHERE `enabled` = '1';

-- Every mapping below is a completed transformation/opening, therefore it is
-- assigned to achievement_cracked (one increment per completed crack).  Clear
-- tick links for these rows so a multi-tick crack cannot count twice.
UPDATE `items_crackable`
SET `achievement_tick` = '', `achievement_cracked` = 'Horticulturist'
WHERE `item_name` LIKE 'jungle_c16_flower%';

UPDATE `items_crackable`
SET `achievement_tick` = '', `achievement_cracked` = 'GhostHunter'
WHERE `item_name` IN ('hween_r16_crackable2', 'hween_c16_crackable1');

UPDATE `items_crackable`
SET `achievement_tick` = '', `achievement_cracked` = 'CreatureRearer'
WHERE `item_name` IN (
    'xmas_c16_creature7', 'xmas_c16_egg', 'xmas_c16_creature1',
    'xmas_c16_creature4'
);

UPDATE `items_crackable`
SET `achievement_tick` = '', `achievement_cracked` = 'Farmer'
WHERE `item_name` = 'easter_c17_seedbag'
   OR `item_name` LIKE 'easter_c18_seedpack%';

UPDATE `items_crackable`
SET `achievement_tick` = '', `achievement_cracked` = 'Restorer'
WHERE `item_name` LIKE 'santorini_c17_artefact%';

UPDATE `items_crackable`
SET `achievement_tick` = '', `achievement_cracked` = 'flamingknight'
WHERE `item_name` = 'hween_c17_flamingknight';

UPDATE `items_crackable`
SET `achievement_tick` = '', `achievement_cracked` = 'AdvancedHorticulturist'
WHERE `item_name` IN (
       'easter_c17_floweringbush', 'easter_c17_leafsprout',
       'easter_c17_sapling', 'easter_c17_seeds'
   )
   OR `item_name` LIKE 'easter_c18_snowdrop%'
   OR `item_name` LIKE 'easter_c18_lupin%'
   OR `item_name` LIKE 'easter_c18_tulip%'
   OR `item_name` LIKE 'easter_c18_rose%';

UPDATE `items_crackable`
SET `achievement_tick` = '', `achievement_cracked` = 'EasterCreatures'
WHERE `item_name` IN (
    'easter_c19_book1', 'easter_c19_book2',
    'easter_c19_book3', 'easter_c19_book4'
);

-- These rows are present only when the 2023 Tombs furniture pack has been
-- imported.  The conditional UPDATE keeps older databases valid and links the
-- rows automatically when that pack is installed.
UPDATE `items_crackable`
SET `achievement_tick` = '', `achievement_cracked` = 'HweenTombsCrackable'
WHERE `item_name` IN ('hween_c23_seth', 'hween_c23_seth3');
