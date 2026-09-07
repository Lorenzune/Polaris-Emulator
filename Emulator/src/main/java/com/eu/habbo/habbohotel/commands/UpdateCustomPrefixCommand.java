package com.eu.habbo.habbohotel.commands;

import com.eu.habbo.Emulator;
import com.eu.habbo.habbohotel.gameclients.GameClient;
import com.eu.habbo.habbohotel.rooms.RoomChatMessageBubbles;
import com.eu.habbo.habbohotel.users.Habbo;
import com.eu.habbo.messages.outgoing.inventory.nickicons.UserNickIconsComposer;

public class UpdateCustomPrefixCommand extends Command {

    public UpdateCustomPrefixCommand() {
        super("cmd_update_custom_prefix", Emulator.getTexts().getValue("commands.keys.cmd_update_custom_prefix", "update_custom_prefix;update_customprefix;update_prefix;update_prefixes").split(";"));
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
                Emulator.getTexts().getValue("commands.succes.cmd_update_custom_prefix", "Impostazioni ed effetti dei prefissi ricaricati con successo per tutti gli utenti online!"),
                RoomChatMessageBubbles.ALERT
            );
        }

        return true;
    }
}
