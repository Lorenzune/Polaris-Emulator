-- Protect the game worker from runaway Send Signal fan-out while keeping limits configurable.
INSERT INTO wired_emulator_settings (`key`, `value`, `comment`)
VALUES
    (
        'wired.signal.max.events.per.chain',
        '2048',
        'Maximum signal events admitted in one synchronous WIRED chain; 0 disables this limit.'
    ),
    (
        'wired.signal.max.chain.ms',
        '250',
        'Maximum synchronous duration in milliseconds for one WIRED signal chain; 0 disables this limit.'
    )
ON DUPLICATE KEY UPDATE
    `comment` = VALUES(`comment`);
