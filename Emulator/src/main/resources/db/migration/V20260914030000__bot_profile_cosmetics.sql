ALTER TABLE `bots`
    ADD COLUMN `background_id` int(11) NOT NULL DEFAULT 0 AFTER `bubble_id`,
    ADD COLUMN `background_stand_id` int(11) NOT NULL DEFAULT 0 AFTER `background_id`,
    ADD COLUMN `background_card_id` int(11) NOT NULL DEFAULT 0 AFTER `background_stand_id`,
    ADD COLUMN `vip_slots_active` tinyint(1) NOT NULL DEFAULT 0 AFTER `background_card_id`,
    ADD COLUMN `badge_6` varchar(50) NOT NULL DEFAULT '' AFTER `badge_5`,
    ADD COLUMN `badge_7` varchar(50) NOT NULL DEFAULT '' AFTER `badge_6`;
