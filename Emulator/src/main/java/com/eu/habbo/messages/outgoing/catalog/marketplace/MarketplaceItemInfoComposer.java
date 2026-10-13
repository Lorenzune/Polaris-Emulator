package com.eu.habbo.messages.outgoing.catalog.marketplace;

import com.eu.habbo.habbohotel.catalog.marketplace.MarketPlace;
import com.eu.habbo.messages.ServerMessage;
import com.eu.habbo.messages.outgoing.MessageComposer;
import com.eu.habbo.messages.outgoing.Outgoing;

public class MarketplaceItemInfoComposer extends MessageComposer {
    private final int itemId;
    private final int currency;
    private final int type;

    public MarketplaceItemInfoComposer(int itemId) {
        this(itemId, MarketPlace.legacyCurrency(), 1);
    }

    public MarketplaceItemInfoComposer(int itemId, int currency, int type) {
        this.itemId = itemId;
        this.currency = currency;
        this.type = type;
    }

    @Override
    protected ServerMessage composeInternal() {
        this.response.init(Outgoing.MarketplaceItemInfoComposer);
        MarketPlace.serializeItemInfo(this.itemId, this.response, this.currency, this.type);
        return this.response;
    }

    public int getItemId() {
        return itemId;
    }
}
