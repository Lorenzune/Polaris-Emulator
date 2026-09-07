package com.eu.habbo.messages.outgoing.catalog.recycler;

import com.eu.habbo.habbohotel.catalog.CatalogManager.RecyclerConfiguration;
import com.eu.habbo.messages.ServerMessage;
import com.eu.habbo.messages.outgoing.MessageComposer;
import com.eu.habbo.messages.outgoing.Outgoing;

/** Staff-only full Furni-Matic configuration, kept separate from the SWF recycler packet. */
public class RecyclerAdminStateComposer extends MessageComposer {
    private final RecyclerConfiguration configuration;

    public RecyclerAdminStateComposer(RecyclerConfiguration configuration) {
        this.configuration = configuration;
    }

    @Override
    protected ServerMessage composeInternal() {
        this.response.init(Outgoing.RecyclerAdminStateComposer);
        this.response.appendInt(this.configuration.ducketCost());
        this.response.appendInt(this.configuration.levels().size());

        for (var level : this.configuration.levels()) {
            this.response.appendInt(level.rarity());
            this.response.appendInt(level.probabilityDenominator());
            this.response.appendInt(level.items().size());

            for (var item : level.items()) {
                this.response.appendInt(item.getId());
                this.response.appendString(item.getName());
                this.response.appendString(item.getType().code.toLowerCase());
                this.response.appendInt(item.getSpriteId());
            }
        }

        return this.response;
    }
}
