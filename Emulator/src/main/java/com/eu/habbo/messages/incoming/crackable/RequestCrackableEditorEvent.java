package com.eu.habbo.messages.incoming.crackable;

import com.eu.habbo.messages.incoming.MessageHandler;

public class RequestCrackableEditorEvent extends MessageHandler {
    @Override
    public void handle() {
        CrackableEditorSupport.sendState(this.client, this.packet.readInt());
    }
}
