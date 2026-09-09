-- Builders Club subscription boxes must redeem on their first interaction,
-- matching the HC boxes and the expected catalog behaviour.
UPDATE `items_crackable`
SET `count` = 1
WHERE `item_name` IN ('bc_gift_14days', 'bc_gift_31days')
  AND `subscription_type` = 'bc';

-- Boxes already clicked once stored a crack counter of 1. Reset it so they
-- can be redeemed after this one-use correction (at the next interaction).
UPDATE `items` AS `instance`
INNER JOIN `items_crackable` AS `crackable` ON `crackable`.`item_id` = `instance`.`item_id`
SET `instance`.`extra_data` = '0'
WHERE `crackable`.`item_name` IN ('bc_gift_14days', 'bc_gift_31days')
  AND `crackable`.`subscription_type` = 'bc'
  AND `instance`.`extra_data` <> '0';
