package com.eu.habbo.messages.outgoing.catalog;

import com.eu.habbo.Emulator;
import com.eu.habbo.messages.ServerMessage;
import com.eu.habbo.messages.outgoing.MessageComposer;
import com.eu.habbo.messages.outgoing.Outgoing;

public class RecyclerLogicComposer extends MessageComposer {
    @Override
    protected ServerMessage composeInternal() {
        this.response.init(Outgoing.RecyclerLogicComposer);
        var configuration = Emulator.getGameEnvironment().getCatalogManager().getRecyclerConfigurationSnapshot();
        var levels = configuration.levels().stream().filter(level -> !level.items().isEmpty()).toList();
        this.response.appendInt(levels.size());
        for (var level : levels) {
            this.response.appendInt(level.rarity());
            this.response.appendInt(level.probabilityDenominator());
            this.response.appendInt(level.items().size());
            for (var item : level.items()) {
                this.response.appendString(item.getName());
                this.response.appendInt(1);
                this.response.appendString(item.getType().code.toLowerCase());
                this.response.appendInt(item.getSpriteId());
            }
        }
        return this.response;
    }
}
