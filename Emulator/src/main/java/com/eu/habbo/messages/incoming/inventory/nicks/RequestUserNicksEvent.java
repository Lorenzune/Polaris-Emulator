package com.eu.habbo.messages.incoming.inventory.nicks;

import com.eu.habbo.messages.incoming.MessageHandler;
import com.eu.habbo.messages.outgoing.inventory.nicks.UserNickStylesComposer;

public class RequestUserNicksEvent extends MessageHandler {
    @Override
    public void handle() throws Exception {
        this.client.sendResponse(new UserNickStylesComposer(this.client.getHabbo()));
    }
}
