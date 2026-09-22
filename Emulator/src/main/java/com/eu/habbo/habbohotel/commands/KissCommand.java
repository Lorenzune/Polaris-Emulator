package com.eu.habbo.habbohotel.commands;

import com.eu.habbo.Emulator;
import com.eu.habbo.habbohotel.gameclients.GameClient;
import com.eu.habbo.habbohotel.rooms.Room;
import com.eu.habbo.habbohotel.rooms.RoomChatMessage;
import com.eu.habbo.habbohotel.rooms.RoomChatMessageBubbles;
import com.eu.habbo.habbohotel.users.Habbo;
import com.eu.habbo.messages.outgoing.rooms.users.RoomUserTalkComposer;

public class KissCommand extends Command {
    public KissCommand() {
        super(
                "cmd_kiss",
                Emulator.getTexts()
                        .getValue("commands.keys.cmd_kiss", "bacia;kiss;frenchkiss")
                        .split(";"));
    }

    @Override
    public boolean handle(GameClient gameClient, String[] params) throws Exception {
        var texts = Emulator.getTexts();
        if (params.length < 2) {
            gameClient
                    .getHabbo()
                    .whisper(
                            texts.getValue(
                                    "commands.error.cmd_kiss.forgot_username",
                                    "Specifica il nome dell'utente che vuoi baciare!"),
                            RoomChatMessageBubbles.ALERT);
            return true;
        }

        Room currentRoom = gameClient.getHabbo().getHabboInfo().getCurrentRoom();
        if (currentRoom == null) return true;

        Habbo target = currentRoom.getHabbo(params[1]);

        if (target == null) {
            gameClient
                    .getHabbo()
                    .whisper(
                            texts.getValue(
                                            "commands.error.cmd_kiss.not_found",
                                            "Utente %user% non trovato in questa stanza!")
                                    .replace("%user%", params[1]),
                            RoomChatMessageBubbles.ALERT);
            return true;
        }

        if (target == gameClient.getHabbo()) {
            gameClient
                    .getHabbo()
                    .whisper(
                            texts.getValue("commands.error.cmd_kiss.self", "Non puoi baciarti da solo!"),
                            RoomChatMessageBubbles.ALERT);
            return true;
        }

        if (target.getHabboStats().blockRoleplay || target.getHabboStats().blockKiss) {
            gameClient
                    .getHabbo()
                    .whisper(
                            texts.getValue(
                                            "commands.error.cmd_kiss.disabled",
                                            "%user% ha disattivato i baci nelle impostazioni privacy!")
                                    .replace("%user%", target.getHabboInfo().getUsername()),
                            RoomChatMessageBubbles.ALERT);
            return true;
        }

        int distanceX = Math.abs(target.getRoomUnit().getX()
                - gameClient.getHabbo().getRoomUnit().getX());
        int distanceY = Math.abs(target.getRoomUnit().getY()
                - gameClient.getHabbo().getRoomUnit().getY());

        if (distanceX > 1 || distanceY > 1) {
            gameClient
                    .getHabbo()
                    .whisper(
                            texts.getValue(
                                            "commands.error.cmd_kiss.cant_reach",
                                            "Sei troppo lontano da %user% per baciarlo!")
                                    .replace("%user%", target.getHabboInfo().getUsername()),
                            RoomChatMessageBubbles.ALERT);
            return true;
        }

        // Apply effect 9 (hearts) to both users
        currentRoom.giveEffect(gameClient.getHabbo(), 9, -1);
        currentRoom.giveEffect(target, 9, -1);

        // Sender says *bacia <target>* with red text
        RoomChatMessage senderMsg = new RoomChatMessage(
                "@red@*bacia " + target.getHabboInfo().getUsername() + "*",
                gameClient.getHabbo(),
                RoomChatMessageBubbles.NORMAL);
        currentRoom.sendComposer(new RoomUserTalkComposer(senderMsg).compose());

        // Kissed target says *<sender> mi ha baciato :P* with cyan/light-blue text
        RoomChatMessage targetMsg = new RoomChatMessage(
                "@green@*" + gameClient.getHabbo().getHabboInfo().getUsername() + " mi ha baciato :P*",
                target,
                RoomChatMessageBubbles.NORMAL);
        currentRoom.sendComposer(new RoomUserTalkComposer(targetMsg).compose());

        return true;
    }
}
