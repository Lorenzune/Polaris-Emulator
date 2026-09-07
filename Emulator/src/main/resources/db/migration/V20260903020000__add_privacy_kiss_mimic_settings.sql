ALTER TABLE `users_settings`
    ADD COLUMN IF NOT EXISTS `block_kiss` enum('0','1') NOT NULL DEFAULT '0' AFTER `block_friendrequests`,
    ADD COLUMN IF NOT EXISTS `block_mimic` enum('0','1') NOT NULL DEFAULT '0' AFTER `block_kiss`;
