package com.eu.habbo.messages.incoming.catalog.recycler;

import com.eu.habbo.Emulator;
import com.eu.habbo.habbohotel.permissions.Permission;
import com.eu.habbo.messages.incoming.MessageHandler;
import com.eu.habbo.messages.outgoing.catalog.recycler.RecyclerAdminResultComposer;
import com.eu.habbo.messages.outgoing.catalog.recycler.RecyclerAdminStateComposer;

public class RecyclerAdminRequestEvent extends MessageHandler {
    @Override
    public void handle() throws Exception {
        if (!this.client.getHabbo().hasPermission(Permission.ACC_RECYCLER_EDITOR)) {
            this.client.sendResponse(new RecyclerAdminResultComposer(false, "No permission"));
            return;
        }

        this.client.sendResponse(new RecyclerAdminStateComposer(
                Emulator.getGameEnvironment().getCatalogManager().getRecyclerConfigurationSnapshot()));
    }
}
