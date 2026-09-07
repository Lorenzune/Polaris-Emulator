package com.eu.habbo.habbohotel.commands;

import com.eu.habbo.Emulator;
import com.eu.habbo.habbohotel.gameclients.GameClient;
import com.eu.habbo.habbohotel.messenger.MessengerBuddy;
import com.eu.habbo.habbohotel.rooms.RoomChatMessageBubbles;
import com.eu.habbo.habbohotel.users.Habbo;

public class VisitCommand extends Command {
    public VisitCommand() {
        super(null, new String[] {"visit"});
    }

    @Override
    public boolean handle(GameClient gameClient, String[] params) {
        if (gameClient == null || gameClient.getHabbo() == null) {
            return false;
        }

        if (params.length < 2 || params[1].trim().isEmpty()) {
            gameClient.getHabbo().whisper("Specifica il nome utente dell'amico che vuoi visitare! Es: :visit <nomeutente>", RoomChatMessageBubbles.ALERT);
            return true;
        }

        String targetUsername = params[1].trim();

        if (gameClient.getHabbo().getHabboInfo().getUsername().equalsIgnoreCase(targetUsername)) {
            gameClient.getHabbo().whisper("Non puoi visitare te stesso!", RoomChatMessageBubbles.ALERT);
            return true;
        }

        Habbo targetHabbo = Emulator.getGameEnvironment().getHabboManager().getHabbo(targetUsername);
        if (targetHabbo == null || !targetHabbo.isOnline()) {
            gameClient.getHabbo().whisper(targetUsername + " sembra non essere online.", RoomChatMessageBubbles.ALERT);
            return true;
        }

        MessengerBuddy buddy = gameClient.getHabbo().getMessenger().getFriend(targetHabbo.getHabboInfo().getId());
        if (buddy == null) {
            gameClient.getHabbo().whisper(targetHabbo.getHabboInfo().getUsername() + " non è nella tua lista amici! Puoi visitare solo i tuoi amici.", RoomChatMessageBubbles.ALERT);
            return true;
        }

        if (targetHabbo.getHabboStats().blockFollowing && !gameClient.getHabbo().hasPermission("acc_can_stalk")) {
            gameClient.getHabbo().whisper(targetHabbo.getHabboInfo().getUsername() + " non permette di essere seguito/a.", RoomChatMessageBubbles.ALERT);
            return true;
        }

        if (targetHabbo.getHabboInfo().getCurrentRoom() == null) {
            gameClient.getHabbo().whisper(targetHabbo.getHabboInfo().getUsername() + " non si trova in nessuna stanza al momento.", RoomChatMessageBubbles.ALERT);
            return true;
        }

        if (gameClient.getHabbo().getHabboInfo().getCurrentRoom() == targetHabbo.getHabboInfo().getCurrentRoom()) {
            gameClient.getHabbo().whisper("Ti trovi già nella stessa stanza di " + targetHabbo.getHabboInfo().getUsername() + "!", RoomChatMessageBubbles.ALERT);
            return true;
        }

        gameClient.getHabbo().goToRoom(targetHabbo.getHabboInfo().getCurrentRoom().getId());
        Emulator.getGameEnvironment().getRewardTrackManager()
                .addProgress(gameClient.getHabbo().getHabboInfo().getId(), "follow_friend", 1);
        return true;
    }
}
