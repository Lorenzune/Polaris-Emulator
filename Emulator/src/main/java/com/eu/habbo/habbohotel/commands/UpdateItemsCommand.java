package com.eu.habbo.habbohotel.commands;

import com.eu.habbo.Emulator;
import com.eu.habbo.habbohotel.gameclients.GameClient;
import com.eu.habbo.habbohotel.rooms.RoomChatMessageBubbles;

public class UpdateItemsCommand extends Command {
    public UpdateItemsCommand() {
        super("cmd_update_items", Emulator.getTexts().getValue("commands.keys.cmd_update_items").split(";"));
    }

    @Override
    public boolean handle(GameClient gameClient, String[] params) {
        boolean scheduled = Emulator.getGameEnvironment().reloadItemsAsync(() -> {
            Emulator.getGameServer().getGameClientManager().sendBroadcastResponse(
                    new com.eu.habbo.messages.outgoing.furniture.FurnitureDataReloadComposer(
                            com.eu.habbo.messages.outgoing.furniture.FurnitureDataReloadComposer.MODE_RELOAD_HINT,
                            null
                    )
            );

            if (gameClient.getChannel().isOpen() && gameClient.getHabbo() != null) {
                gameClient.getHabbo().whisper(
                        Emulator.getTexts().getValue("commands.succes.cmd_update_items"),
                        RoomChatMessageBubbles.ALERT);
            }
        });

        if (!scheduled && gameClient.getChannel().isOpen() && gameClient.getHabbo() != null) {
            gameClient.getHabbo().whisper("Item update is already in progress.", RoomChatMessageBubbles.ALERT);
        }

        return true;
    }
}
