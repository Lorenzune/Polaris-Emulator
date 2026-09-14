ALTER TABLE `bots`
    ADD COLUMN `infostand_border_id` int(11) NOT NULL DEFAULT 0 AFTER `bubble_id`,
    ADD COLUMN `badge_1` varchar(50) NOT NULL DEFAULT '' AFTER `infostand_border_id`,
    ADD COLUMN `badge_2` varchar(50) NOT NULL DEFAULT '' AFTER `badge_1`,
    ADD COLUMN `badge_3` varchar(50) NOT NULL DEFAULT '' AFTER `badge_2`,
    ADD COLUMN `badge_4` varchar(50) NOT NULL DEFAULT '' AFTER `badge_3`,
    ADD COLUMN `badge_5` varchar(50) NOT NULL DEFAULT '' AFTER `badge_4`;
