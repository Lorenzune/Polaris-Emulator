-- Run room cycles on a bounded worker pool separate from shared game tasks.
-- Preserve any values already tuned by the hotel.
INSERT INTO `emulator_settings` (`key`, `value`, `comment`) VALUES
    ('room.cycle.workers', '0', 'Room-cycle workers; 0 selects 2 to 16 based on available processors; positive values are clamped to 1-64. Restart required.'),
    ('room.cycle.slow_ms', '250', 'Log a warning when one room cycle exceeds this many milliseconds.')
ON DUPLICATE KEY UPDATE `comment` = VALUES(`comment`);
