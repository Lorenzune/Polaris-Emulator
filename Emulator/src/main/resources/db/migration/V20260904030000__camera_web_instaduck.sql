-- Add caption column to camera_web for Instaduck captions
ALTER TABLE `camera_web` ADD COLUMN IF NOT EXISTS `caption` VARCHAR(255) NOT NULL DEFAULT '';
