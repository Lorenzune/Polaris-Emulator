package com.eu.habbo.habbohotel.commands;

import com.eu.habbo.Emulator;
import com.eu.habbo.habbohotel.commands.roleplay.RoleplayCommandManager;
import com.eu.habbo.habbohotel.gameclients.GameClient;
import com.eu.habbo.habbohotel.rooms.RoomChatMessageBubbles;

public class UpdateRoleplayCommandsCommand extends Command {

    public UpdateRoleplayCommandsCommand() {
        super(
                "cmd_update_roleplay",
                Emulator.getTexts()
                        .getValue(
                                "commands.keys.cmd_update_roleplay",
                                "update_roleplay;update_roleplay_commands;update_rp;updateroleplay")
                        .split(";"));
    }

    @Override
    public boolean handle(GameClient gameClient, String[] params) {
        int count = RoleplayCommandManager.getInstance().reload();

        // Broadcast updated commands to ALL online clients in real-time
        Emulator.getGameServer()
                .getGameClientManager()
                .sendBroadcastResponse(new com.eu.habbo.messages.outgoing.roleplay.RoleplayCommandsComposer(
                        RoleplayCommandManager.getInstance().getCommandData()));

        if (gameClient != null && gameClient.getHabbo() != null) {
            String message = Emulator.getTexts()
                    .getValue(
                            "commands.succes.cmd_update_roleplay",
                            "Comandi Roleplay ricaricati con successo dal database (%count% attivi)!")
                    .replace("%count%", String.valueOf(count));

            gameClient.getHabbo().whisper(message, RoomChatMessageBubbles.ALERT);
        }

        return true;
    }
}
