package com.eu.habbo.messages.incoming.catalog.catalogadmin;

import com.eu.habbo.Emulator;
import com.eu.habbo.habbohotel.permissions.Permission;
import com.eu.habbo.messages.incoming.MessageHandler;
import com.eu.habbo.messages.outgoing.catalog.CatalogUpdatedComposer;
import com.eu.habbo.messages.outgoing.catalog.catalogadmin.CatalogAdminResultComposer;

public class CatalogAdminPublishEvent extends MessageHandler {

    @Override
    public void handle() throws Exception {
        if (!this.client.getHabbo().hasPermission(Permission.ACC_CATALOGFURNI)) {
            this.client.sendResponse(new CatalogAdminResultComposer(false, "No permission"));
            return;
        }

        boolean scheduled = Emulator.getGameEnvironment().reloadCatalogAsync(() -> {
            // Keep the catalog-admin protocol unchanged: it has historically
            // sent CatalogUpdated only, rather than the full staff-command
            // refresh sequence.
            Emulator.getGameServer().getGameClientManager().sendBroadcastResponse(
                    new CatalogUpdatedComposer());

            if (this.client.getChannel().isOpen()) {
                this.client.sendResponse(new CatalogAdminResultComposer(true, "Catalog published"));
            }
        });

        if (!scheduled) {
            this.client.sendResponse(new CatalogAdminResultComposer(false, "Catalog publish already in progress"));
        }
    }
}
