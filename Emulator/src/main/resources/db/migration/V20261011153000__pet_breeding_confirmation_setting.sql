-- Optional: enable only together with a client handling the existing breeding packets.
-- Keep older deployments on automatic nest breeding until they opt in.
INSERT INTO emulator_settings (`key`, `value`)
SELECT 'pet.breeding.confirmation.enabled', '0'
WHERE NOT EXISTS (SELECT 1 FROM emulator_settings WHERE `key` = 'pet.breeding.confirmation.enabled');
