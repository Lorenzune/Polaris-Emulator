package com.eu.habbo.messages.incoming.wired;

import com.eu.habbo.messages.incoming.MessageHandler;
import com.eu.habbo.messages.outgoing.wired.WiredAccountPreferencesDataComposer;

public class WiredAccountPreferencesRequestEvent extends MessageHandler {
    @Override
    public void handle() {
        if (this.client.getHabbo() == null) {
            return;
        }

        this.client.sendResponse(new WiredAccountPreferencesDataComposer(this.client.getHabbo()));
    }

    @Override
    public int getRatelimit() {
        return 250;
    }
}
