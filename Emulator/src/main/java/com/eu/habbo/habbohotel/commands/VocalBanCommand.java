package com.eu.habbo.habbohotel.commands;

import com.eu.habbo.Emulator;
import com.eu.habbo.habbohotel.gameclients.GameClient;
import com.eu.habbo.habbohotel.rooms.RoomChatMessageBubbles;
import com.eu.habbo.habbohotel.users.Habbo;
import com.eu.habbo.messages.outgoing.rooms.RoomVoiceSignalComposer;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;

public class VocalBanCommand extends Command {
    public VocalBanCommand() {
        super(
                "cmd_vocalban",
                Emulator.getTexts()
                        .getValue("commands.keys.cmd_vocalban", "vocalban;banvocal;voiceban")
                        .split(";"));
    }

    @Override
    public boolean handle(GameClient gameClient, String[] params) throws Exception {
        if (params.length < 2) {
            gameClient.getHabbo().whisper("Utilizzo: :vocalban <username> [ore / perm]", RoomChatMessageBubbles.ALERT);
            return true;
        }

        String targetUsername = params[1];
        int durationHours = Integer.MAX_VALUE;

        if (params.length >= 3) {
            String durParam = params[2].trim().toLowerCase();
            if (!durParam.equals("perm") && !durParam.equals("permanente")) {
                try {
                    durationHours = Integer.parseInt(durParam);
                    if (durationHours <= 0) {
                        gameClient
                                .getHabbo()
                                .whisper("La durata in ore deve essere maggiore di 0.", RoomChatMessageBubbles.ALERT);
                        return true;
                    }
                } catch (NumberFormatException e) {
                    gameClient
                            .getHabbo()
                            .whisper(
                                    "Durata non valida. Inserisci un numero di ore o 'perm'.",
                                    RoomChatMessageBubbles.ALERT);
                    return true;
                }
            }
        }

        int now = Emulator.getIntUnixTimestamp();
        int expiresTimestamp = (durationHours == Integer.MAX_VALUE || durationHours > 876000)
                ? Integer.MAX_VALUE
                : now + (durationHours * 3600);

        Habbo target = Emulator.getGameEnvironment().getHabboManager().getHabbo(targetUsername);

        if (target != null) {
            if (target == gameClient.getHabbo()) {
                gameClient.getHabbo().whisper("Non puoi bannarti da solo dalla vocal.", RoomChatMessageBubbles.ALERT);
                return true;
            }

            if (!CommandTargetGuard.canTarget(gameClient.getHabbo(), target)) {
                gameClient
                        .getHabbo()
                        .whisper(
                                Emulator.getTexts().getValue("commands.error.cmd_ban.target_rank_higher"),
                                RoomChatMessageBubbles.ALERT);
                return true;
            }

            target.getHabboStats().setVocalBannedUntil(expiresTimestamp);

            if (target.getHabboInfo().getCurrentRoom() != null) {
                target.getHabboInfo()
                        .getCurrentRoom()
                        .getVoiceUsers()
                        .remove(target.getHabboInfo().getId());
                int remainingSeconds =
                        expiresTimestamp == Integer.MAX_VALUE ? (86400 * 3650) : (expiresTimestamp - now);
                target.getClient()
                        .sendResponse(new RoomVoiceSignalComposer(
                                0,
                                "{\"type\":\"mod_action\",\"targetUserId\":"
                                        + target.getHabboInfo().getId()
                                        + ",\"payload\":{\"action\":\"disconnect\",\"durationSeconds\":"
                                        + remainingSeconds + "}}"));
            }

            target.alert("Sei stato escluso dalla chat vocale da un moderatore.");
            gameClient
                    .getHabbo()
                    .whisper(
                            "Hai escluso " + target.getHabboInfo().getUsername() + " dalla chat vocale "
                                    + (expiresTimestamp == Integer.MAX_VALUE
                                            ? "permanentemente."
                                            : ("per " + durationHours + " ore.")),
                            RoomChatMessageBubbles.ALERT);
        } else {
            try (Connection connection = Emulator.getDatabase().getDataSource().getConnection();
                    PreparedStatement statement = connection.prepareStatement(
                            "UPDATE users_settings INNER JOIN users ON users.id = users_settings.user_id SET users_settings.vocal_banned_until = ? WHERE users.username = ? LIMIT 1")) {
                statement.setInt(1, expiresTimestamp);
                statement.setString(2, targetUsername);
                int updated = statement.executeUpdate();
                if (updated > 0) {
                    gameClient
                            .getHabbo()
                            .whisper(
                                    "L'utente offline " + targetUsername + " è stato escluso dalla chat vocale "
                                            + (expiresTimestamp == Integer.MAX_VALUE
                                                    ? "permanentemente."
                                                    : ("per " + durationHours + " ore.")),
                                    RoomChatMessageBubbles.ALERT);
                } else {
                    gameClient
                            .getHabbo()
                            .whisper("Utente " + targetUsername + " non trovato.", RoomChatMessageBubbles.ALERT);
                }
            } catch (SQLException e) {
                gameClient
                        .getHabbo()
                        .whisper("Errore durante il ban vocale: " + e.getMessage(), RoomChatMessageBubbles.ALERT);
            }
        }

        return true;
    }
}
