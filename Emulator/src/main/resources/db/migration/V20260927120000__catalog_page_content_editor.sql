ALTER TABLE `catalog_pages`
    ADD COLUMN IF NOT EXISTS `page_headline_x` INT NOT NULL DEFAULT 0,
    ADD COLUMN IF NOT EXISTS `page_headline_y` INT NOT NULL DEFAULT 0,
    ADD COLUMN IF NOT EXISTS `page_headline_width` INT NOT NULL DEFAULT 0,
    ADD COLUMN IF NOT EXISTS `page_headline_height` INT NOT NULL DEFAULT 0,
    ADD COLUMN IF NOT EXISTS `page_teaser_x` INT NOT NULL DEFAULT 0,
    ADD COLUMN IF NOT EXISTS `page_teaser_y` INT NOT NULL DEFAULT 0,
    ADD COLUMN IF NOT EXISTS `page_teaser_width` INT NOT NULL DEFAULT 0,
    ADD COLUMN IF NOT EXISTS `page_teaser_height` INT NOT NULL DEFAULT 0,
    ADD COLUMN IF NOT EXISTS `page_text1_x` INT NOT NULL DEFAULT 0,
    ADD COLUMN IF NOT EXISTS `page_text1_y` INT NOT NULL DEFAULT 0;

ALTER TABLE `catalog_pages_bc`
    ADD COLUMN IF NOT EXISTS `page_headline_x` INT NOT NULL DEFAULT 0,
    ADD COLUMN IF NOT EXISTS `page_headline_y` INT NOT NULL DEFAULT 0,
    ADD COLUMN IF NOT EXISTS `page_headline_width` INT NOT NULL DEFAULT 0,
    ADD COLUMN IF NOT EXISTS `page_headline_height` INT NOT NULL DEFAULT 0,
    ADD COLUMN IF NOT EXISTS `page_teaser_x` INT NOT NULL DEFAULT 0,
    ADD COLUMN IF NOT EXISTS `page_teaser_y` INT NOT NULL DEFAULT 0,
    ADD COLUMN IF NOT EXISTS `page_teaser_width` INT NOT NULL DEFAULT 0,
    ADD COLUMN IF NOT EXISTS `page_teaser_height` INT NOT NULL DEFAULT 0,
    ADD COLUMN IF NOT EXISTS `page_text1_x` INT NOT NULL DEFAULT 0,
    ADD COLUMN IF NOT EXISTS `page_text1_y` INT NOT NULL DEFAULT 0;

INSERT IGNORE INTO `emulator_settings` (`key`, `value`, `comment`) VALUES
    ('catalog.admin.asset.upload.path', '', 'Absolute folder where catalog admin image uploads are saved. Leave blank to disable uploads. Serve this folder through the URL configured by catalog.asset.image.url in the client.');
