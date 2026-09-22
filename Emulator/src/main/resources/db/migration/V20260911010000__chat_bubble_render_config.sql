ALTER TABLE `chat_bubbles`
    ADD COLUMN IF NOT EXISTS `render_config` JSON NULL AFTER `image_file`;
