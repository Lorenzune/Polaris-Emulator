-- Add Achievement linkage columns to roleplay_commands table

ALTER TABLE `roleplay_commands`
    ADD COLUMN IF NOT EXISTS `sender_achievement` varchar(100) DEFAULT NULL AFTER `cooldown_seconds`,
    ADD COLUMN IF NOT EXISTS `sender_achievement_progress` int(11) NOT NULL DEFAULT 1 AFTER `sender_achievement`,
    ADD COLUMN IF NOT EXISTS `target_achievement` varchar(100) DEFAULT NULL AFTER `sender_achievement_progress`,
    ADD COLUMN IF NOT EXISTS `target_achievement_progress` int(11) NOT NULL DEFAULT 1 AFTER `target_achievement`;
