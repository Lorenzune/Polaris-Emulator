ALTER TABLE `users_settings`
    ADD COLUMN IF NOT EXISTS `wired_show_toolbar` enum('0','1') NOT NULL DEFAULT '0',
    ADD COLUMN IF NOT EXISTS `wired_show_inspect` enum('0','1') NOT NULL DEFAULT '0',
    ADD COLUMN IF NOT EXISTS `wired_show_system_notifications` enum('0','1') NOT NULL DEFAULT '0';
