package com.eu.habbo.messages.incoming.inventory.habbicons;

import com.eu.habbo.messages.incoming.MessageHandler;
import com.eu.habbo.messages.outgoing.inventory.habbicons.UserHabbiconsComposer;

public class RequestUserHabbiconsEvent extends MessageHandler {
    @Override
    public void handle() throws Exception {
        if (this.client.getHabbo() != null) {
            this.client.sendResponse(new UserHabbiconsComposer(this.client.getHabbo()));
        }
    }
}
