package com.eu.habbo.habbohotel.commands;

import com.eu.habbo.Emulator;
import com.eu.habbo.habbohotel.gameclients.GameClient;
import com.eu.habbo.habbohotel.rooms.RoomChatMessageBubbles;
import com.eu.habbo.habbohotel.users.Habbo;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;

public class UnvocalBanCommand extends Command {
    public UnvocalBanCommand() {
        super("cmd_unvocalban", Emulator.getTexts().getValue("commands.keys.cmd_unvocalban", "unvocalban;unbanvocal;unvoiceban").split(";"));
    }

    @Override
    public boolean handle(GameClient gameClient, String[] params) throws Exception {
        if (params.length < 2) {
            gameClient.getHabbo().whisper("Utilizzo: :unvocalban <username>", RoomChatMessageBubbles.ALERT);
            return true;
        }

        String targetUsername = params[1];
        Habbo target = Emulator.getGameEnvironment().getHabboManager().getHabbo(targetUsername);

        if (target != null) {
            target.getHabboStats().setVocalBannedUntil(0);
            if (target.getClient() != null) {
                target.getClient().sendResponse(new com.eu.habbo.messages.outgoing.rooms.RoomVoiceSignalComposer(0,
                        "{\"type\":\"mod_action\",\"targetUserId\":" + target.getHabboInfo().getId() + ",\"payload\":{\"action\":\"unban\",\"durationSeconds\":0}}"));
            }
            target.whisper("La tua chat vocale è stata riabilitata da un moderatore.", RoomChatMessageBubbles.ALERT);
            gameClient.getHabbo().whisper("Hai riabilitato la chat vocale per " + target.getHabboInfo().getUsername() + ".", RoomChatMessageBubbles.ALERT);
        } else {
            try (Connection connection = Emulator.getDatabase().getDataSource().getConnection();
                 PreparedStatement statement = connection.prepareStatement(
                         "UPDATE users_settings INNER JOIN users ON users.id = users_settings.user_id SET users_settings.vocal_banned_until = 0 WHERE users.username = ? LIMIT 1")) {
                statement.setString(1, targetUsername);
                int updated = statement.executeUpdate();
                if (updated > 0) {
                    gameClient.getHabbo().whisper("La chat vocale per l'utente offline " + targetUsername + " è stata riabilitata.", RoomChatMessageBubbles.ALERT);
                } else {
                    gameClient.getHabbo().whisper("Utente " + targetUsername + " non trovato.", RoomChatMessageBubbles.ALERT);
                }
            } catch (SQLException e) {
                gameClient.getHabbo().whisper("Errore durante lo sban vocale: " + e.getMessage(), RoomChatMessageBubbles.ALERT);
            }
        }

        return true;
    }
}
