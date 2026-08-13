-- Category mapping for the SWF TimeSpentRoomType* achievements.
-- Values are comma/semicolon-separated navigator_flatcats.caption_save values
-- (or exact category captions) and can be extended without code changes.
INSERT INTO `emulator_settings` (`key`, `value`, `comment`) VALUES
    ('achievements.room_time.chat.categories', 'caption_save_chat,caption_save_discussion', 'Room categories counted for TimeSpentRoomTypeChat'),
    ('achievements.room_time.trade.categories', 'caption_save_trade,caption_save_marketplace', 'Room categories counted for TimeSpentRoomTypeTrade'),
    ('achievements.room_time.rpg.categories', 'caption_save_rpg,caption_save_agency,caption_save_roleplay', 'Room categories counted for TimeSpentRoomTypeRPG'),
    ('achievements.room_time.games.categories', 'caption_save_games', 'Room categories counted for TimeSpentRoomTypeGames')
ON DUPLICATE KEY UPDATE `value` = VALUES(`value`), `comment` = VALUES(`comment`);
