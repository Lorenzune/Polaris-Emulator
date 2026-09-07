package com.eu.habbo.habbohotel.commands;

import com.eu.habbo.Emulator;
import com.eu.habbo.habbohotel.gameclients.GameClient;

public class UpdateHotelViewCommand extends Command {
    protected UpdateHotelViewCommand() {
        super("cmd_update_hotel_view", Emulator.getTexts().getValue("commands.keys.cmd_update_hotel_view").split(";"));
    }

    @Override
    public boolean handle(GameClient gameClient, String[] params) {
        boolean scheduled = Emulator.getGameEnvironment().getHotelViewManager().reloadAndBroadcastLandingViewAsync(
                () -> {
                    Emulator.getGameEnvironment().getHotelViewManager().getNewsList().reload();
                    Emulator.getGameEnvironment().getHotelViewManager().getHallOfFame().reload();
                },
                () -> {
                    if (gameClient.getChannel().isOpen() && gameClient.getHabbo() != null) {
                        gameClient.getHabbo().whisper(
                                Emulator.getTexts().getValue("commands.succes.cmd_update_hotel_view"));
                    }
                });

        if (!scheduled && gameClient.getChannel().isOpen() && gameClient.getHabbo() != null) {
            gameClient.getHabbo().whisper("Hotel view update is already in progress.");
        }

        return true;
    }
}
