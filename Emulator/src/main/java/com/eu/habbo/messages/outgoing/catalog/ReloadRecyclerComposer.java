package com.eu.habbo.messages.outgoing.catalog;

import com.eu.habbo.Emulator;
import com.eu.habbo.messages.ServerMessage;
import com.eu.habbo.messages.outgoing.MessageComposer;
import com.eu.habbo.messages.outgoing.Outgoing;

public class ReloadRecyclerComposer extends MessageComposer {
    @Override
    protected ServerMessage composeInternal() {
        this.response.init(Outgoing.ReloadRecyclerComposer);
        this.response.appendInt(1);
        this.response.appendInt(0);
        this.response.appendInt(Emulator.getGameEnvironment().getCatalogManager().getRecyclerDucketCost());
        return this.response;
    }
}
