package com.eu.habbo.messages.outgoing.rooms.items;

import com.eu.habbo.messages.ServerMessage;
import com.eu.habbo.messages.outgoing.MessageComposer;

public final class WiredContractTransactionComposer extends MessageComposer {
    private final String offer;

    public WiredContractTransactionComposer(String offer) {
        this.offer = offer;
    }

    public String getOffer() {
        return offer;
    }

    @Override
    protected ServerMessage composeInternal() {
        response.init(com.eu.habbo.messages.outgoing.Outgoing.WiredContractTransactionComposer);
        response.appendString(offer);
        return response;
    }
}
