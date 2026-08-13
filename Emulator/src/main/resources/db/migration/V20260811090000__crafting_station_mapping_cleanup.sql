-- Replace the temporary source-page station metadata with the confirmed SWF
-- crafting-table IDs.  The JSON catalog stores station_ids as a JSON array;
-- page 10 has no matching furniture in this installation, so it is
-- deliberately marked with the [0] sentinel.
UPDATE `crafting_recipes`
SET `station_ids` = CASE `source_page`
    WHEN 1  THEN '[8178]'
    WHEN 2  THEN '[8983]'
    WHEN 3  THEN '[10084]'
    WHEN 4  THEN '[10622]'
    WHEN 5  THEN '[9226]'
    WHEN 6  THEN '[9846]'
    WHEN 7  THEN '[13711]'
    WHEN 8  THEN '[14495]'
    WHEN 9  THEN '[10725]'
    WHEN 10 THEN '[0]'
    WHEN 11 THEN '[8752]'
    WHEN 12 THEN '[9417]'
    WHEN 13 THEN '[9988]'
    WHEN 14 THEN '[10448]'
    WHEN 15 THEN '[10546]'
    WHEN 16 THEN '[10983]'
    WHEN 17 THEN '[11019]'
    WHEN 18 THEN '[11421]'
    WHEN 19 THEN '[11434]'
    WHEN 20 THEN '[11686]'
    WHEN 21 THEN '[11833]'
    WHEN 22 THEN '[11889]'
    WHEN 23 THEN '[11969]'
    WHEN 24 THEN '[11991]'
    WHEN 25 THEN '[13068]'
    WHEN 26 THEN '[13117]'
    WHEN 27 THEN '[13249]'
    WHEN 28 THEN '[13401]'
    WHEN 29 THEN '[13432]'
    WHEN 30 THEN '[13694]'
    WHEN 31 THEN '[13859]'
    WHEN 32 THEN '[14093]'
    WHEN 33 THEN '[14355]'
    WHEN 34 THEN '[14402]'
    WHEN 35 THEN '[14495]'
    WHEN 36 THEN '[14785]'
    WHEN 37 THEN '[15067]'
    WHEN 38 THEN '[15579]'
    ELSE '[0]'
END;

-- source_page/source_url are import-only fields and are no longer read by the
-- emulator after station_ids has been resolved.
ALTER TABLE `crafting_recipes`
    DROP INDEX `source_page`;

ALTER TABLE `crafting_recipes`
    DROP COLUMN `source_url`,
    DROP COLUMN `source_page`;
