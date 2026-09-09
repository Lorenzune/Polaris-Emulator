-- Pet type 0 is the fallback used while loading pets. Some live databases lost
-- the base row even though it is part of the canonical seed, causing every pet
-- load to attempt a runtime repair and still report missing fallback data.
INSERT INTO `pet_actions`
    (`pet_type`, `pet_name`, `offspring_type`, `happy_actions`, `tired_actions`, `random_actions`, `can_swim`)
SELECT
    0, 'Dog', 29, '', '', '', '0'
WHERE NOT EXISTS (
    SELECT 1
    FROM `pet_actions`
    WHERE `pet_type` = 0
);
