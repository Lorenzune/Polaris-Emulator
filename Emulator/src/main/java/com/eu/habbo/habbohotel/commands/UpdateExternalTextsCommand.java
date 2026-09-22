package com.eu.habbo.habbohotel.commands;

import com.eu.habbo.Emulator;
import com.eu.habbo.habbohotel.gameclients.GameClient;
import com.eu.habbo.habbohotel.rooms.RoomChatMessageBubbles;
import com.eu.habbo.habbohotel.users.Habbo;
import com.eu.habbo.messages.outgoing.generic.ExternalTextsReloadComposer;

public class UpdateExternalTextsCommand extends Command {

    public UpdateExternalTextsCommand() {
        super(
                "cmd_update_externaltexts",
                Emulator.getTexts()
                        .getValue(
                                "commands.keys.cmd_update_externaltexts",
                                "update_externaltexts;update_external_texts;update_texts_external")
                        .split(";"));
    }

    @Override
    public boolean handle(GameClient gameClient, String[] params) {
        long timestamp = System.currentTimeMillis();
        ExternalTextsReloadComposer composer = new ExternalTextsReloadComposer(timestamp);

        for (Habbo habbo : Emulator.getGameEnvironment()
                .getHabboManager()
                .getOnlineHabbos()
                .values()) {
            if (habbo != null && habbo.getClient() != null) {
                habbo.getClient().sendResponse(composer);
            }
        }

        if (gameClient != null && gameClient.getHabbo() != null) {
            gameClient
                    .getHabbo()
                    .whisper(
                            Emulator.getTexts()
                                    .getValue(
                                            "commands.succes.cmd_update_externaltexts",
                                            "ExternalTexts ricaricato con successo con timestamp (" + timestamp
                                                    + ") e sincronizzato per tutti i giocatori online!"),
                            RoomChatMessageBubbles.ALERT);
        }

        return true;
    }
}
