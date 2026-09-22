-- Fix gift wrappers mapping to real present_wrap* furni items (IDs 3195..3204)
-- Replaces old outdated Flash sprite IDs (3372..3381) with official wrapper items.

DELETE FROM `gift_wrappers` WHERE `type` = 'wrapper';

INSERT INTO `gift_wrappers` (`id`, `sprite_id`, `item_id`, `type`) VALUES
    (8, 3195, 3195, 'wrapper'),
    (9, 3197, 3197, 'wrapper'),
    (10, 3198, 3198, 'wrapper'),
    (11, 3199, 3199, 'wrapper'),
    (12, 3200, 3200, 'wrapper'),
    (13, 3201, 3201, 'wrapper'),
    (14, 3202, 3202, 'wrapper'),
    (15, 3203, 3203, 'wrapper'),
    (16, 3204, 3204, 'wrapper'),
    (17, 3196, 3196, 'wrapper')
ON DUPLICATE KEY UPDATE
    `sprite_id` = VALUES(`sprite_id`),
    `item_id` = VALUES(`item_id`),
    `type` = VALUES(`type`);

-- Repair existing gifts corrupted with old wrapper IDs (3372..3381)
UPDATE `items` SET `item_id` = 3195 WHERE `item_id` BETWEEN 3372 AND 3381 AND `extra_data` LIKE '%\t%';
