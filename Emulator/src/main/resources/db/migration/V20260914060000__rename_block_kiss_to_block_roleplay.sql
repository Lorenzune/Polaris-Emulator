-- The privacy flag applies to every roleplay interaction, not only kisses.
ALTER TABLE `users_settings`
    CHANGE COLUMN `block_kiss` `block_roleplay` enum('0','1') NOT NULL DEFAULT '0' AFTER `block_friendrequests`;

