-- Keep any existing visual scale close to its nearest integer sprite multiplier.
UPDATE `room_furni_opacity`
SET `scaling` = CASE
    WHEN `scaling` < 150 THEN 100
    WHEN `scaling` < 250 THEN 200
    ELSE 300
END
WHERE `scaling` NOT IN (100, 200, 300);
