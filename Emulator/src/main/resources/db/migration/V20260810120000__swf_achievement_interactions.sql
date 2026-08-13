-- SWF achievement interaction defaults.  The achievement catalogue itself is
-- loaded from gamedata/achievements.json; this migration only supplies the
-- server-side thresholds used by the new interaction hooks.
INSERT INTO `emulator_settings` (`key`, `value`, `comment`)
VALUES ('achievements.ltd.early_bird_limit', '10', 'Highest LTD number that qualifies for LTDEarlyBird')
ON DUPLICATE KEY UPDATE `value` = VALUES(`value`), `comment` = VALUES(`comment`);
