CREATE TABLE IF NOT EXISTS `room_furni_opacity` (
    `room_id` INT(11) NOT NULL,
    `furni_id` INT(11) NOT NULL,
    `opacity` TINYINT UNSIGNED NOT NULL DEFAULT 100,
    `click_through` TINYINT(1) NOT NULL DEFAULT 0,
    `scaling` SMALLINT UNSIGNED NOT NULL DEFAULT 100,
    PRIMARY KEY (`room_id`, `furni_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
