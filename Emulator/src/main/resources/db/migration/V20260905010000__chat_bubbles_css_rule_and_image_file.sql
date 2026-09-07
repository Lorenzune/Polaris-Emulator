-- Add image_file and css_rule columns to chat_bubbles for HK custom management
ALTER TABLE `chat_bubbles`
    ADD COLUMN IF NOT EXISTS `image_file` varchar(255) DEFAULT NULL AFTER `enabled`,
    ADD COLUMN IF NOT EXISTS `css_rule` longtext DEFAULT NULL AFTER `image_file`;
