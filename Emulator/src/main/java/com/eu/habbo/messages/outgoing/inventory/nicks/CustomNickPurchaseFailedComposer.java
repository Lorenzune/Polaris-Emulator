package com.eu.habbo.messages.outgoing.inventory.nicks;

import com.eu.habbo.messages.ServerMessage;
import com.eu.habbo.messages.outgoing.MessageComposer;
import com.eu.habbo.messages.outgoing.Outgoing;

public class CustomNickPurchaseFailedComposer extends MessageComposer {
    private final String message;

    public CustomNickPurchaseFailedComposer(String message) {
        this.message = message != null ? message : "";
    }

    @Override
    protected ServerMessage composeInternal() {
        this.response.init(Outgoing.CustomNickPurchaseFailedComposer);
        this.response.appendString(this.message);
        return this.response;
    }
}
