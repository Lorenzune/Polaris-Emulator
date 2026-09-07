package com.eu.habbo.habbohotel.commands;

import com.eu.habbo.habbohotel.gameclients.GameClient;

public class AboutCommand extends Command {
    public static final String NITRO_INFO_SENTINEL = "[NITRO_INFO_V1]";
    public static final String REPORT_ISSUES_URL = "https://github.com/duckietm/Nitro-V3/issues";

    public AboutCommand() {
        super(null, new String[] {});
    }

    @Override
    public boolean handle(GameClient gameClient, String[] params) {
        return true;
    }
}
