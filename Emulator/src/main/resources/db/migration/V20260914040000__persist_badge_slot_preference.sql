ALTER TABLE `users_settings`
    ADD COLUMN IF NOT EXISTS `badge_slots_active` tinyint(1) NOT NULL DEFAULT 0;

UPDATE `users_settings` us
SET `badge_slots_active` = 1
WHERE EXISTS (
    SELECT 1
    FROM `users_profile_cosmetics` pc
    WHERE pc.`user_id` = us.`user_id`
      AND LOWER(pc.`category`) = 'slot'
);
