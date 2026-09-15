-- API key used by YouTube Party search and YouTube TV playlist lookups.
-- Leave it empty by default; operators can configure their own key in emulator_settings.
INSERT IGNORE INTO `emulator_settings` (`key`, `value`, `comment`)
VALUES (
    'youtube.apikey',
    '',
    'YouTube Data API v3 key used for YouTube search and playlist metadata.'
);
