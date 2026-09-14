-- Add the official Toast Habbicons introduced in Habbo's 28301bf asset bundle.

INSERT INTO `custom_habbicon_categories` (`id`, `name`, `badge`, `sort_order`, `enabled`)
VALUES (7, 'Toast', '', 3, 1)
ON DUPLICATE KEY UPDATE
    `name` = VALUES(`name`),
    `sort_order` = VALUES(`sort_order`),
    `enabled` = VALUES(`enabled`);

INSERT INTO `custom_habbicons_catalog` (`id`, `category_id`, `image_file`, `display_name`, `points`, `points_type`, `min_rank`, `is_free`, `enabled`, `sort_order`)
VALUES
    (61, 7, 'toast_toast.png', 'Toast Classico', 10, 0, 0, 0, 1, 61),
    (62, 7, 'toast_happy.png', 'Toast Felice', 10, 0, 0, 0, 1, 62),
    (63, 7, 'toast_cute.png', 'Toast Tenero', 10, 0, 0, 0, 1, 63),
    (64, 7, 'toast_wink.png', 'Toast Occhiolino', 10, 0, 0, 0, 1, 64),
    (65, 7, 'toast_sad.png', 'Toast Triste', 10, 0, 0, 0, 1, 65),
    (66, 7, 'toast_cry.png', 'Toast Piangente', 10, 0, 0, 0, 1, 66),
    (67, 7, 'toast_grumpy.png', 'Toast Scontroso', 10, 0, 0, 0, 1, 67),
    (68, 7, 'toast_sleep.png', 'Toast Assonnato', 10, 0, 0, 0, 1, 68),
    (69, 7, 'toast_shock.png', 'Toast Scioccato', 10, 0, 0, 0, 1, 69),
    (70, 7, 'toast_flustered.png', 'Toast Imbarazzato', 15, 0, 0, 0, 1, 70),
    (71, 7, 'toast_fine.png', 'Toast Tutto Bene', 25, 0, 0, 0, 1, 71)
ON DUPLICATE KEY UPDATE
    `category_id` = VALUES(`category_id`),
    `image_file` = VALUES(`image_file`),
    `display_name` = VALUES(`display_name`),
    `enabled` = VALUES(`enabled`),
    `sort_order` = VALUES(`sort_order`);
