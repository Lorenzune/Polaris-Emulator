package com.eu.habbo.messages.incoming.inventory.chatbubbles;

import com.eu.habbo.messages.incoming.MessageHandler;
import com.eu.habbo.messages.outgoing.inventory.chatbubbles.UserChatBubblesComposer;

public class RequestUserChatBubblesEvent extends MessageHandler {
    @Override
    public void handle() throws Exception {
        if (this.client.getHabbo() != null) {
            this.client.sendResponse(new UserChatBubblesComposer(this.client.getHabbo()));
        }
    }
}
