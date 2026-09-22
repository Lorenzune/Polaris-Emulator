-- Furni-Matic configuration is normalized exactly like the SWF packet:
-- a rarity level owns a 1-in-N denominator and its prizes are uniform.
-- Some production databases already added an `id` column to this legacy
-- table. The recycler does not need that column, so do not impose either
-- schema shape here; only make its writes transaction-safe.
-- The base dump creates this legacy table as MyISAM ROW_FORMAT=FIXED.
-- InnoDB does not accept ROW_FORMAT=FIXED, so set a valid row format while
-- converting it. This keeps the conversion valid across supported MariaDB
-- versions instead of failing with errno 140 (Wrong create options).
ALTER TABLE `recycler_prizes`
    ENGINE = InnoDB,
    ROW_FORMAT = DYNAMIC;

CREATE TABLE IF NOT EXISTS `recycler_prize_levels` (
    `rarity` INT UNSIGNED NOT NULL,
    `probability_denominator` INT UNSIGNED NOT NULL DEFAULT 1,
    PRIMARY KEY (`rarity`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

INSERT INTO `recycler_prize_levels` (`rarity`, `probability_denominator`) VALUES
    (1, 1),
    (2, 4),
    (3, 40),
    (4, 200),
    (5, 2000)
ON DUPLICATE KEY UPDATE `probability_denominator` = `probability_denominator`;

CREATE TABLE IF NOT EXISTS `recycler_settings` (
    `id` TINYINT UNSIGNED NOT NULL,
    `ducket_cost` INT UNSIGNED NOT NULL DEFAULT 10,
    PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

INSERT INTO `recycler_settings` (`id`, `ducket_cost`) VALUES (1, 10)
ON DUPLICATE KEY UPDATE `ducket_cost` = `ducket_cost`;

UPDATE `items_base`
SET `interaction_type` = 'default'
WHERE `item_name` IN ('ecotron_box', 'matic_box');

INSERT INTO `permission_definitions`
    (`permission_key`, `max_value`, `comment`, `rank_1`, `rank_2`, `rank_3`, `rank_4`, `rank_5`, `rank_6`, `rank_7`)
VALUES
    ('acc_recycler_editor', 1, 'Allows rank 7 users to configure Furni-Matic prizes, odds, and Ducket cost from the catalogue.', 0, 0, 0, 0, 0, 0, 1)
ON DUPLICATE KEY UPDATE
    `max_value` = VALUES(`max_value`),
    `comment` = VALUES(`comment`),
    `rank_1` = VALUES(`rank_1`),
    `rank_2` = VALUES(`rank_2`),
    `rank_3` = VALUES(`rank_3`),
    `rank_4` = VALUES(`rank_4`),
    `rank_5` = VALUES(`rank_5`),
    `rank_6` = VALUES(`rank_6`),
    `rank_7` = VALUES(`rank_7`);
