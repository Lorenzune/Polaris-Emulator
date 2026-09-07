package com.eu.habbo.messages.rcon;

import com.eu.habbo.Emulator;
import com.google.gson.Gson;

public class UpdateCatalog extends RCONMessage<UpdateCatalog.JSONUpdateCatalog> {

    public UpdateCatalog() {
        super(JSONUpdateCatalog.class);
    }

    @Override
    public void handle(Gson gson, JSONUpdateCatalog json) {
        Emulator.getGameEnvironment().reloadCatalogAndCraftingAsync(
                () -> Emulator.getGameEnvironment().broadcastCatalogReload());
    }

    static class JSONUpdateCatalog {
    }
}
