-- Add size, offset_x, and offset_y columns to custom_habbicons_catalog

ALTER TABLE `custom_habbicons_catalog` 
    ADD COLUMN IF NOT EXISTS `size` int(11) NOT NULL DEFAULT 42 AFTER `sort_order`,
    ADD COLUMN IF NOT EXISTS `offset_x` int(11) NOT NULL DEFAULT 0 AFTER `size`,
    ADD COLUMN IF NOT EXISTS `offset_y` int(11) NOT NULL DEFAULT 0 AFTER `offset_x`;
