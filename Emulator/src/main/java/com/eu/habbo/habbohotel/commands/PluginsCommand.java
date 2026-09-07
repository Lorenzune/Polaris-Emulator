package com.eu.habbo.habbohotel.commands;

import com.eu.habbo.habbohotel.gameclients.GameClient;

public class PluginsCommand extends Command {
    public PluginsCommand() {
        super(null, new String[] {});
    }

    @Override
    public boolean handle(GameClient gameClient, String[] params) throws Exception {
        return true;
    }
}
