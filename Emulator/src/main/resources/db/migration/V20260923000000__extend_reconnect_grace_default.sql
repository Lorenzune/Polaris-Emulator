-- Extend only the legacy 5-second default. Leave other configured values unchanged.
UPDATE `emulator_settings`
SET `value` = '30'
WHERE `key` = 'session.reconnect.grace.seconds'
  AND `value` = '5';
