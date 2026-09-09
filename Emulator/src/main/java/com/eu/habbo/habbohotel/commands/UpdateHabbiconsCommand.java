package com.eu.habbo.habbohotel.commands;

import com.eu.habbo.Emulator;
import com.eu.habbo.habbohotel.gameclients.GameClient;
import com.eu.habbo.habbohotel.rooms.RoomChatMessageBubbles;
import com.eu.habbo.habbohotel.users.Habbo;
import com.eu.habbo.messages.outgoing.inventory.habbicons.UserHabbiconsComposer;

public class UpdateHabbiconsCommand extends Command {

    public UpdateHabbiconsCommand() {
        super("cmd_update_habbicons", Emulator.getTexts().getValue("commands.keys.cmd_update_habbicons", "update_habbicons;update_habbicon").split(";"));
    }

    @Override
    public boolean handle(GameClient gameClient, String[] params) {
        for (Habbo habbo : Emulator.getGameEnvironment().getHabboManager().getOnlineHabbos().values()) {
            if (habbo.getClient() != null) {
                habbo.getClient().sendResponse(new UserHabbiconsComposer(habbo));
            }
        }

        if (gameClient != null && gameClient.getHabbo() != null) {
            gameClient.getHabbo().whisper(
                Emulator.getTexts().getValue("commands.succes.cmd_update_habbicons", "Catalogo Habbicons ricaricato con successo per tutti gli utenti online!"),
                RoomChatMessageBubbles.ALERT
            );
        }

        return true;
    }
}
