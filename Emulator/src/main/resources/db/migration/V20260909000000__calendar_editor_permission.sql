-- Rank 7 in-client calendar rewards editor permission.
INSERT INTO `permission_definitions`
    (`permission_key`, `max_value`, `comment`, `rank_1`, `rank_2`, `rank_3`, `rank_4`, `rank_5`, `rank_6`, `rank_7`)
VALUES
    ('acc_calendar_editor', 1, 'Allows rank 7 staff to edit, add, and delete calendar campaign rewards from the client.', 0, 0, 0, 0, 0, 0, 1)
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
