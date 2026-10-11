package com.eu.habbo.messages.outgoing.rooms.items;

import com.eu.habbo.messages.ServerMessage;
import com.eu.habbo.messages.outgoing.MessageComposer;
import com.eu.habbo.messages.outgoing.Outgoing;

public class ChestDepositResultComposer extends MessageComposer {
    private final int chestId;
    private final boolean success;

    public ChestDepositResultComposer(int chestId, boolean success) {
        this.chestId = chestId;
        this.success = success;
    }

    @Override
    protected ServerMessage composeInternal() {
        response.init(Outgoing.ChestDepositResultComposer);
        response.appendInt(chestId);
        response.appendBoolean(success);
        return response;
    }
}
