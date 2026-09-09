-- Seed Duck & Frank Habbicons into custom_habbicons_catalog and setup categories

-- 1. Ensure Categories Exist
INSERT INTO `custom_habbicon_categories` (`id`, `name`, `badge`, `sort_order`, `enabled`)
VALUES
    (1, 'Paperelle', '', 1, 1),
    (2, 'Frank', '', 2, 1),
    (3, 'Custom & Speciali', '', 3, 1),
    (4, 'Animati GIF', '', 4, 1),
    (5, 'Meme & Fun', '', 5, 1),
    (6, 'VIP & Esclusivi', '', 6, 1)
ON DUPLICATE KEY UPDATE
    `name` = VALUES(`name`),
    `sort_order` = VALUES(`sort_order`),
    `enabled` = VALUES(`enabled`);

-- 2. Insert Duck Habbicons (IDs 28 to 49)
-- Free starter ducks: 28 to 33
-- Shop ducks: 34 to 49
INSERT INTO `custom_habbicons_catalog` (`id`, `category_id`, `image_file`, `display_name`, `points`, `points_type`, `min_rank`, `is_free`, `enabled`, `sort_order`)
VALUES
    (28, 1, 'duck_duck.png', 'Paperella Base', 0, 0, 0, 1, 1, 28),
    (29, 1, 'duck_happy.png', 'Paperella Felice', 0, 0, 0, 1, 1, 29),
    (30, 1, 'duck_sad.png', 'Paperella Triste', 0, 0, 0, 1, 1, 30),
    (31, 1, 'duck_shock.png', 'Paperella Scioccata', 0, 0, 0, 1, 1, 31),
    (32, 1, 'duck_think.png', 'Paperella Pensierosa', 0, 0, 0, 1, 1, 32),
    (33, 1, 'duck_nohear.png', 'Paperella Non Sento', 0, 0, 0, 1, 1, 33),
    (34, 1, 'duck_nosee.png', 'Paperella Non Vedo', 10, 0, 0, 0, 1, 34),
    (35, 1, 'duck_nosay.png', 'Paperella Non Parlo', 10, 0, 0, 0, 1, 35),
    (36, 1, 'duck_angel.png', 'Paperella Angelo', 10, 0, 0, 0, 1, 36),
    (37, 1, 'duck_devil.png', 'Paperella Diavoletto', 10, 0, 0, 0, 1, 37),
    (38, 1, 'duck_spinning.png', 'Paperella Rotante', 25, 0, 0, 0, 1, 38),
    (39, 1, 'duck_cool.png', 'Paperella Occhiali da Sole', 15, 0, 0, 0, 1, 39),
    (40, 1, 'duck_pleased.png', 'Paperella Soddisfatta', 10, 0, 0, 0, 1, 40),
    (41, 1, 'duck_laughing.png', 'Paperella Che Ride', 10, 0, 0, 0, 1, 41),
    (42, 1, 'duck_grimace.png', 'Paperella Smorfia', 10, 0, 0, 0, 1, 42),
    (43, 1, 'duck_devious.png', 'Paperella Furba', 10, 0, 0, 0, 1, 43),
    (44, 1, 'duck_metal.png', 'Paperella Rocker', 15, 0, 0, 0, 1, 44),
    (45, 1, 'duck_pleading.png', 'Paperella Supplichevole', 10, 0, 0, 0, 1, 45),
    (46, 1, 'duck_silly.png', 'Paperella Buffa', 10, 0, 0, 0, 1, 46),
    (47, 1, 'duck_wink.png', 'Paperella Occhiolino', 10, 0, 0, 0, 1, 47),
    (48, 1, 'duck_party.png', 'Paperella Festa', 15, 0, 0, 0, 1, 48),
    (49, 1, 'duck_love.png', 'Paperella Innamorata', 25, 0, 0, 0, 1, 49)
ON DUPLICATE KEY UPDATE
    `category_id` = VALUES(`category_id`),
    `display_name` = VALUES(`display_name`),
    `enabled` = VALUES(`enabled`);

-- 3. Insert Frank Habbicons (IDs 50 to 60)
INSERT INTO `custom_habbicons_catalog` (`id`, `category_id`, `image_file`, `display_name`, `points`, `points_type`, `min_rank`, `is_free`, `enabled`, `sort_order`)
VALUES
    (50, 2, 'frank_frank.png', 'Frank Classico', 10, 0, 0, 0, 1, 50),
    (51, 2, 'frank_smile.png', 'Frank Sorriso', 10, 0, 0, 0, 1, 51),
    (52, 2, 'frank_happy.png', 'Frank Felice', 10, 0, 0, 0, 1, 52),
    (53, 2, 'frank_sad.png', 'Frank Triste', 10, 0, 0, 0, 1, 53),
    (54, 2, 'frank_scared.png', 'Frank Spaventato', 10, 0, 0, 0, 1, 54),
    (55, 2, 'frank_surprised.png', 'Frank Sorpreso', 10, 0, 0, 0, 1, 55),
    (56, 2, 'frank_thinking.png', 'Frank Pensieroso', 10, 0, 0, 0, 1, 56),
    (57, 2, 'frank_silly.png', 'Frank Buffo', 10, 0, 0, 0, 1, 57),
    (58, 2, 'frank_relief.png', 'Frank Rassicurato', 10, 0, 0, 0, 1, 58),
    (59, 2, 'frank_wink.png', 'Frank Occhiolino', 10, 0, 0, 0, 1, 59),
    (60, 2, 'frank_stareyes.png', 'Frank Occhi a Stella', 25, 0, 0, 0, 1, 60)
ON DUPLICATE KEY UPDATE
    `category_id` = VALUES(`category_id`),
    `display_name` = VALUES(`display_name`),
    `enabled` = VALUES(`enabled`);
