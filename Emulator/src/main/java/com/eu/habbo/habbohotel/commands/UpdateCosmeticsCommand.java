package com.eu.habbo.habbohotel.commands;

import com.eu.habbo.Emulator;
import com.eu.habbo.habbohotel.gameclients.GameClient;
import com.eu.habbo.habbohotel.rooms.RoomChatMessageBubbles;
import com.eu.habbo.habbohotel.users.Habbo;
import com.eu.habbo.messages.outgoing.inventory.cosmetics.UserProfileCosmeticsComposer;

public class UpdateCosmeticsCommand extends Command {

    public UpdateCosmeticsCommand() {
        super("cmd_update_cosmetics", Emulator.getTexts().getValue("commands.keys.cmd_update_cosmetics", "update_cosmetics;update_backgrounds;update_stands").split(";"));
    }

    @Override
    public boolean handle(GameClient gameClient, String[] params) {
        if (Emulator.getGameEnvironment().getInfostandBackgroundManager() != null) {
            Emulator.getGameEnvironment().getInfostandBackgroundManager().reload();
        }

        for (Habbo habbo : Emulator.getGameEnvironment().getHabboManager().getOnlineHabbos().values()) {
            if (habbo != null && habbo.getClient() != null) {
                habbo.getClient().sendResponse(new UserProfileCosmeticsComposer(habbo));
            }
        }

        if (gameClient != null && gameClient.getHabbo() != null) {
            gameClient.getHabbo().whisper(
                Emulator.getTexts().getValue("commands.succes.cmd_update_cosmetics", "Catalogo cosmetici profilo ricaricato con successo dal database e sincronizzato per tutti i giocatori online!"),
                RoomChatMessageBubbles.ALERT
            );
        }

        return true;
    }
}
