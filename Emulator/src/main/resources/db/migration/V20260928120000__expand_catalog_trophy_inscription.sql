-- Keep the emulator's trophy inscription limit in sync with the catalog editor.
-- Preserve any operator-specific limit instead of overwriting a custom value.
INSERT INTO emulator_settings (`key`, `value`)
VALUES ('hotel.trophies.length.max', '1000')
ON DUPLICATE KEY UPDATE `value` = IF(`value` = '300', '1000', `value`);
