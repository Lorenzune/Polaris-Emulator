package com.eu.habbo.messages.outgoing.crackable;

import com.eu.habbo.messages.ServerMessage;
import com.eu.habbo.messages.outgoing.MessageComposer;
import com.eu.habbo.messages.outgoing.Outgoing;

public class CrackableEditorResultComposer extends MessageComposer {
    private final boolean success;
    private final String message;

    public CrackableEditorResultComposer(boolean success, String message) {
        this.success = success;
        this.message = message;
    }

    @Override
    protected ServerMessage composeInternal() {
        this.response.init(Outgoing.CrackableEditorResultComposer);
        this.response.appendBoolean(this.success);
        this.response.appendString(this.message == null ? "" : this.message);
        return this.response;
    }
}
