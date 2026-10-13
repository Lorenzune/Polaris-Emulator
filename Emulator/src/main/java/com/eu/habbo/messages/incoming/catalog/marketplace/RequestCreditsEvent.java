package com.eu.habbo.messages.incoming.catalog.marketplace;

import com.eu.habbo.habbohotel.catalog.marketplace.MarketPlace;
import com.eu.habbo.messages.incoming.MessageHandler;

public class RequestCreditsEvent extends MessageHandler {
    @Override
    public int getRatelimit() {
        return 2000;
    }

    @Override
    public void handle() throws Exception {
        boolean multi = this.packet.bytesAvailable() > 0;
        int currency = this.packet.bytesAvailable() > 0 ? this.packet.readInt() : MarketPlace.legacyCurrency();
        MarketPlace.getCredits(this.client, currency);
        this.client.sendResponse(new com.eu.habbo.messages.outgoing.catalog.marketplace.MarketplaceOwnItemsComposer(
                this.client.getHabbo(), multi));
    }
}
