-- Roleplay Commands System Migration
-- Creates dynamic roleplay commands table, permissions and seed data

-- 1. Create table `roleplay_commands`
CREATE TABLE IF NOT EXISTS `roleplay_commands` (
    `id` int(11) NOT NULL AUTO_INCREMENT,
    `name` varchar(100) NOT NULL,
    `keys` varchar(255) NOT NULL,
    `min_rank` int(11) NOT NULL DEFAULT 1,
    `permission` varchar(100) DEFAULT NULL,
    `enabled` tinyint(1) NOT NULL DEFAULT 1,
    `requires_target` tinyint(1) NOT NULL DEFAULT 1,
    `allow_self` tinyint(1) NOT NULL DEFAULT 0,
    `max_distance` int(11) NOT NULL DEFAULT 1,
    `face_to_face` tinyint(1) NOT NULL DEFAULT 1,
    `knockback_tiles` int(11) NOT NULL DEFAULT 0,
    `sender_effect` int(11) NOT NULL DEFAULT 0,
    `sender_effect_duration` int(11) NOT NULL DEFAULT -1,
    `target_effect` int(11) NOT NULL DEFAULT 0,
    `target_effect_duration` int(11) NOT NULL DEFAULT -1,
    `sender_handitem` int(11) NOT NULL DEFAULT 0,
    `target_handitem` int(11) NOT NULL DEFAULT 0,
    `sender_posture` varchar(20) NOT NULL DEFAULT 'none',
    `target_posture` varchar(20) NOT NULL DEFAULT 'none',
    `bubble_id` int(11) NOT NULL DEFAULT 0,
    `sender_messages` text NOT NULL,
    `target_messages` text DEFAULT NULL,
    `error_forgot_target` varchar(255) DEFAULT 'Specifica il nome dell''utente!',
    `error_not_found` varchar(255) DEFAULT 'Utente %target% non trovato in questa stanza!',
    `error_self` varchar(255) DEFAULT 'Non puoi fare questa azione su te stesso!',
    `error_cant_reach` varchar(255) DEFAULT 'Sei troppo lontano da %target%!',
    `error_disabled` varchar(255) DEFAULT '%target% ha disattivato le interazioni roleplay!',
    `cooldown_seconds` int(11) NOT NULL DEFAULT 0,
    `created_at` timestamp NULL DEFAULT CURRENT_TIMESTAMP,
    `updated_at` timestamp NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 2. Emulator texts and permissions for :update_roleplay command
INSERT INTO `emulator_texts` (`key`, `value`) VALUES
    ('commands.description.cmd_update_roleplay', ':update_roleplay'),
    ('commands.keys.cmd_update_roleplay', 'update_roleplay;update_roleplay_commands;update_rp;updateroleplay'),
    ('commands.succes.cmd_update_roleplay', 'Comandi Roleplay ricaricati con successo dal database (%count% attivi)!')
ON DUPLICATE KEY UPDATE `value` = VALUES(`value`);

INSERT INTO `permission_definitions`
    (`permission_key`, `max_value`, `comment`, `rank_1`, `rank_2`, `rank_3`, `rank_4`, `rank_5`, `rank_6`, `rank_7`)
VALUES
    ('cmd_update_roleplay', 1, 'Allows reloading dynamic roleplay commands from database.', 0, 0, 0, 0, 0, 1, 1)
ON DUPLICATE KEY UPDATE
    `max_value` = VALUES(`max_value`),
    `comment` = VALUES(`comment`),
    `rank_6` = 1,
    `rank_7` = 1;

ALTER TABLE `permissions` ADD COLUMN IF NOT EXISTS `cmd_update_roleplay` enum('0','1') NOT NULL DEFAULT '0';
UPDATE `permissions` SET `cmd_update_roleplay` = '1' WHERE `level` >= 6;

-- 3. Seed default popular roleplay commands
INSERT INTO `roleplay_commands` 
    (`id`, `name`, `keys`, `min_rank`, `enabled`, `requires_target`, `allow_self`, `max_distance`, `face_to_face`, `knockback_tiles`, `sender_effect`, `sender_effect_duration`, `target_effect`, `target_effect_duration`, `sender_handitem`, `target_handitem`, `sender_posture`, `target_posture`, `bubble_id`, `sender_messages`, `target_messages`, `error_forgot_target`, `error_not_found`, `error_self`, `error_cant_reach`, `error_disabled`, `cooldown_seconds`)
VALUES
    (1, 'Bacio', 'bacia;kiss;frenchkiss', 1, 1, 1, 0, 1, 1, 0, 9, -1, 9, -1, 0, 0, 'none', 'none', 0, 
     '@red@*bacia %target%*|@red@*dà un bacio appassionato a %target%*|@red@*stampa un bacio affettuoso sulla guancia di %target%*', 
     '@green@*%sender% mi ha baciato :P*|@green@*riceve un dolce bacio da %sender% <3*', 
     'Specifica il nome dell''utente che vuoi baciare!', 'Utente %target% non trovato in questa stanza!', 'Non puoi baciarti da solo!', 'Sei troppo lontano da %target% per baciarlo!', '%target% ha disattivato i baci nelle impostazioni privacy!', 0),
    
    (2, 'Abbraccio', 'abbraccia;hug;cuddle', 1, 1, 1, 0, 1, 1, 0, 9, -1, 9, -1, 0, 0, 'none', 'none', 0, 
     '@blue@*stringe forte in un abbraccio %target%*|@blue@*dà un caldo e sincero abbraccio a %target%*', 
     '@blue@*si sente coccolato dall''abbraccio di %sender%*', 
     'Specifica il nome dell''utente che vuoi abbracciare!', 'Utente %target% non trovato in questa stanza!', 'Non puoi abbracciarti da solo!', 'Sei troppo lontano da %target% per abbracciarlo!', '%target% ha disattivato le interazioni nelle impostazioni privacy!', 0),

    (3, 'Schiaffo', 'schiaffeggia;slap;schiaffo', 1, 1, 1, 0, 1, 1, 1, 0, -1, 53, 3, 0, 0, 'none', 'none', 0, 
     '@red@*tira un sonoro schiaffo a %target%!*|@red@*schiaffeggia improvvisamente %target%!*', 
     '@red@*si massaggia la guancia dolorante dopo lo schiaffo di %sender%...*', 
     'Specifica l''utente da schiaffeggiare!', 'Utente %target% non trovato in questa stanza!', 'Non puoi schiaffeggiarti da solo!', 'Sei troppo lontano da %target%!', '%target% ha disattivato le interazioni!', 0),

    (4, 'Pugno', 'pugno;punch;hit', 1, 1, 1, 0, 1, 1, 2, 0, -1, 53, 4, 0, 0, 'none', 'none', 0, 
     '@red@*sferra un pugno potente e devastante a %target%!*', 
     '@red@*vola all''indietro stordito dal pugno di %sender%!*', 
     'Specifica chi colpire con un pugno!', 'Utente %target% non trovato in questa stanza!', 'Non puoi darti un pugno da solo!', 'Sei troppo lontano da %target%!', '%target% ha disattivato le interazioni!', 0),

    (5, 'Dona Rosa', 'rosa;rose;fiorerosa', 1, 1, 1, 0, 1, 1, 0, 9, -1, 9, -1, 1001, 1001, 'none', 'none', 0, 
     '@gold@*dona una splendida rosa rossa a %target%*|@gold@*porge con eleganza una rosa profumata a %target%*', 
     '@green@*accetta la rosa di %sender% con un dolce sorriso*', 
     'Specifica a chi vuoi donare una rosa!', 'Utente %target% non trovato in questa stanza!', 'Non puoi donare una rosa a te stesso!', 'Sei troppo lontano da %target%!', '%target% ha disattivato le interazioni!', 0),

    (6, 'Offri Birra', 'offribirra;birra;beer', 1, 1, 1, 0, 2, 1, 0, 0, -1, 0, -1, 24, 24, 'none', 'none', 0, 
     '@gold@*offre una birra fresca a %target%! Saluti!*|@gold@*passa un boccale di birra a %target%! Cin cin!*', 
     '@green@*stappa la birra offerta da %sender%! Alla salute!*', 
     'Specifica a chi offrire una birra!', 'Utente %target% non trovato in questa stanza!', 'Non puoi offrirti una birra da solo!', 'Sei troppo lontano da %target%!', '%target% ha disattivato le interazioni!', 0),

    (7, 'Proposta Matrimonio', 'sposa;marry;proposta', 1, 1, 1, 0, 1, 1, 0, 9, -1, 9, -1, 0, 0, 'sit', 'none', 0, 
     '@purple@*si inginocchia davanti a %target% e mostra un prezioso anello chiedendo la sua mano!*|@purple@*dichiara il suo amore eterno a %target% chiedendole/gli di sposarsi!*', 
     '@purple@*guarda emozionato/a e a bocca aperta la proposta di %sender%!*', 
     'Specifica a chi fare la proposta!', 'Utente %target% non trovato in questa stanza!', 'Non puoi fare la proposta a te stesso!', 'Sei troppo lontano da %target%!', '%target% ha disattivato le interazioni!', 0)
ON DUPLICATE KEY UPDATE
    `name` = VALUES(`name`),
    `keys` = VALUES(`keys`),
    `sender_messages` = VALUES(`sender_messages`),
    `target_messages` = VALUES(`target_messages`);
