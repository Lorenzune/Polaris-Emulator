-- The four BC/HC gift boxes are catalogued as crackables. Existing databases
-- can assign different numeric item IDs, so match their stable furnidata names
-- instead of the IDs found in the base dump.
UPDATE `items_base`
SET `interaction_type` = 'crackable_subscription_box'
WHERE `item_name` IN ('bc_gift_31days', 'hc_gift_14days', 'hc_gift_31days', 'bc_gift_14days');

INSERT INTO `items_crackable`
    (`item_id`, `item_name`, `count`, `prizes`, `achievement_tick`, `achievement_cracked`, `required_effect`, `subscription_duration`, `subscription_type`)
SELECT
    `id`,
    `item_name`,
    CASE `item_name`
        WHEN 'bc_gift_31days' THEN 2
        WHEN 'hc_gift_14days' THEN 1
        WHEN 'hc_gift_31days' THEN 1
        WHEN 'bc_gift_14days' THEN 2
    END,
    '',
    '',
    '',
    0,
    CASE `item_name`
        WHEN 'bc_gift_31days' THEN 31
        WHEN 'hc_gift_14days' THEN 14
        WHEN 'hc_gift_31days' THEN 31
        WHEN 'bc_gift_14days' THEN 14
    END,
    CASE
        WHEN `item_name` LIKE 'bc_%' THEN 'bc'
        ELSE 'hc'
    END
FROM `items_base`
WHERE `item_name` IN ('bc_gift_31days', 'hc_gift_14days', 'hc_gift_31days', 'bc_gift_14days')
ON DUPLICATE KEY UPDATE
    `item_name` = VALUES(`item_name`),
    `count` = VALUES(`count`),
    `prizes` = VALUES(`prizes`),
    `achievement_tick` = VALUES(`achievement_tick`),
    `achievement_cracked` = VALUES(`achievement_cracked`),
    `required_effect` = VALUES(`required_effect`),
    `subscription_duration` = VALUES(`subscription_duration`),
    `subscription_type` = VALUES(`subscription_type`);
