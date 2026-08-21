package com.eu.habbo.messages.incoming.catalog;

import com.eu.habbo.messages.incoming.MessageHandler;
import com.eu.habbo.habbohotel.catalog.CatalogPageType;
import com.eu.habbo.habbohotel.rooms.BuildersClubRoomSupport;
import com.eu.habbo.messages.outgoing.catalog.CatalogPagesListComposer;

public class RequestCatalogModeEvent extends MessageHandler {
    @Override
    public void handle() throws Exception {
        CatalogPageType requestedType = CatalogPageType.fromString(this.packet.readString());
        this.client.sendResponse(new CatalogPagesListComposer(this.client.getHabbo(), requestedType.toWireMode()));

        if (requestedType == CatalogPageType.BUILDER) {
            BuildersClubRoomSupport.sendPlacementStatus(this.client.getHabbo());
        }

    }
}
