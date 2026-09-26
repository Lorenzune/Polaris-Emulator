ALTER TABLE `items`
    ADD COLUMN IF NOT EXISTS `custom_color` MEDIUMTEXT NULL AFTER `wired_scaling`;
