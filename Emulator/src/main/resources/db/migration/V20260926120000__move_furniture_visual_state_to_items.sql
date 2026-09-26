ALTER TABLE `items`
    ADD COLUMN IF NOT EXISTS `wired_opacity` TINYINT UNSIGNED NOT NULL DEFAULT 100;
ALTER TABLE `items`
    ADD COLUMN IF NOT EXISTS `wired_scaling` SMALLINT UNSIGNED NOT NULL DEFAULT 100;

UPDATE `items` AS item
INNER JOIN `room_furni_opacity` AS visual_state ON visual_state.`furni_id` = item.`id`
    AND visual_state.`room_id` = item.`room_id`
SET item.`wired_opacity` = visual_state.`opacity`,
    item.`wired_scaling` = CASE
        WHEN visual_state.`scaling` < 38 THEN 25
        WHEN visual_state.`scaling` < 75 THEN 50
        WHEN visual_state.`scaling` < 150 THEN 100
        WHEN visual_state.`scaling` < 250 THEN 200
        ELSE 300
    END;

DROP TABLE `room_furni_opacity`;
