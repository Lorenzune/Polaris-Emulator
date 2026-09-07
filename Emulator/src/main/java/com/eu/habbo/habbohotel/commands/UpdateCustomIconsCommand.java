package com.eu.habbo.habbohotel.commands;

import com.eu.habbo.Emulator;
import com.eu.habbo.habbohotel.gameclients.GameClient;
import com.eu.habbo.habbohotel.rooms.RoomChatMessageBubbles;
import com.eu.habbo.habbohotel.users.Habbo;
import com.eu.habbo.messages.outgoing.inventory.nickicons.UserNickIconsComposer;

public class UpdateCustomIconsCommand extends Command {

    public UpdateCustomIconsCommand() {
        super("cmd_update_customicons", Emulator.getTexts().getValue("commands.keys.cmd_update_customicons", "update_customicons;update_custom_icons;update_icons;update_nickicons").split(";"));
    }

    @Override
    public boolean handle(GameClient gameClient, String[] params) {
        for (Habbo habbo : Emulator.getGameEnvironment().getHabboManager().getOnlineHabbos().values()) {
            if (habbo.getClient() != null) {
                habbo.getClient().sendResponse(new UserNickIconsComposer(habbo));
            }
        }

        if (gameClient != null && gameClient.getHabbo() != null) {
            gameClient.getHabbo().whisper(
                Emulator.getTexts().getValue("commands.succes.cmd_update_customicons", "Catalogo icone personalizzate ricaricato con successo per tutti gli utenti online!"),
                RoomChatMessageBubbles.ALERT
            );
        }

        return true;
    }
}
