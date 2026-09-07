-- Keep the starter-room template command available to hotel administrators
-- and make its two-step confirmation understandable in the Italian client.
INSERT INTO `emulator_texts` (`key`, `value`) VALUES
    ('commands.description.cmd_setroom_template', ':setroom_template'),
    ('commands.keys.cmd_setroom_template', 'setroom_template;set_room_template'),
    ('commands.succes.cmd_setroom_template.verify', 'Vuoi salvare la stanza "%roomname%" come modello iniziale? Digita :setroom_template si per confermare.'),
    ('commands.succes.cmd_setroom_template', 'Stanza salvata come modello %id% con %items% furni (%skipped% ignorati).'),
    ('commands.error.cmd_setroom_template', 'Impossibile salvare la stanza come modello. Controlla il log del server.'),
    ('commands.error.cmd_setroom_template.no_room', 'Devi essere dentro una stanza per usare questo comando.')
ON DUPLICATE KEY UPDATE `value` = VALUES(`value`);

-- Staff only by default. Some upgraded databases received the permission
-- definition without a rank grant, which made the registered command silent.
INSERT INTO `permission_definitions`
    (`permission_key`, `max_value`, `comment`, `rank_1`, `rank_2`, `rank_3`, `rank_4`, `rank_5`, `rank_6`, `rank_7`)
VALUES
    ('cmd_setroom_template', 1, 'Allows saving the current room as a starter-room template.', 0, 0, 0, 0, 0, 0, 1)
ON DUPLICATE KEY UPDATE
    `max_value` = VALUES(`max_value`),
    `comment` = VALUES(`comment`),
    `rank_7` = 1;
