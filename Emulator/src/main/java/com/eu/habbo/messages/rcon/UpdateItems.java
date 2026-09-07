package com.eu.habbo.messages.rcon;

import com.eu.habbo.Emulator;
import com.google.gson.Gson;

public class UpdateItems extends RCONMessage<UpdateItems.JSONUpdateItems> {

    public UpdateItems() {
        super(JSONUpdateItems.class);
    }

    @Override
    public void handle(Gson gson, JSONUpdateItems json) {
        Emulator.getGameEnvironment().reloadItemsAsync(null);
    }

    static class JSONUpdateItems {
    }
}
