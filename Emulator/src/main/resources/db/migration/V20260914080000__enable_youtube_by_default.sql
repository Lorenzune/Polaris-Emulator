-- YouTube is available in every room by default.
ALTER TABLE `rooms`
    MODIFY COLUMN `youtube_enabled` tinyint(1) NOT NULL DEFAULT 1;

UPDATE `rooms`
SET `youtube_enabled` = 1;
