package com.eu.habbo.habbohotel.commands;

import com.eu.habbo.Emulator;
import com.eu.habbo.habbohotel.gameclients.GameClient;
import com.eu.habbo.habbohotel.rooms.RoomChatMessageBubbles;
import com.eu.habbo.habbohotel.users.Habbo;
import com.eu.habbo.messages.outgoing.generic.alerts.GenericAlertComposer;
import com.eu.habbo.messages.outgoing.inventory.chatbubbles.UserChatBubblesComposer;
import com.eu.habbo.messages.outgoing.inventory.nickicons.UserNickIconsComposer;
import com.eu.habbo.messages.outgoing.inventory.nicks.UserNickStylesComposer;

public class UpdateCustomizeProfileCommand extends Command {

    public UpdateCustomizeProfileCommand() {
        super("cmd_update_customizeprofile", Emulator.getTexts().getValue("commands.keys.cmd_update_customizeprofile", "update_customizeprofile").split(";"));
    }

    @Override
    public boolean handle(GameClient gameClient, String[] params) {
        RoomChatMessageBubbles.removeDynamicBubbles();
        Emulator.getGameEnvironment().getRoomChatBubbleManager().reload();

        String alertMessage = Emulator.getTexts().getValue(
            "commands.broadcast.cmd_update_customizeprofile",
            "Il negozio di personalizzazione identità chat è stato aggiornato con nuovi contenuti!"
        );

        for (Habbo habbo : Emulator.getGameEnvironment().getHabboManager().getOnlineHabbos().values()) {
            if (habbo.getClient() != null) {
                habbo.getClient().sendResponse(new UserChatBubblesComposer(habbo));
                habbo.getClient().sendResponse(new UserNickIconsComposer(habbo));
                habbo.getClient().sendResponse(new UserNickStylesComposer(habbo));
                habbo.getClient().sendResponse(new GenericAlertComposer(alertMessage));
            }
        }

        if (gameClient != null && gameClient.getHabbo() != null) {
            gameClient.getHabbo().whisper(
                Emulator.getTexts().getValue("commands.succes.cmd_update_customizeprofile", "Profilo di personalizzazione (fumetti, icone, prefissi e stili nick) aggiornato con successo per tutti i giocatori online!"),
                RoomChatMessageBubbles.ALERT
            );
        }

        return true;
    }
}
