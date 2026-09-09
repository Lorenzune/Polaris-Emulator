package com.eu.habbo.messages.outgoing.events.calendar;

import com.eu.habbo.messages.ServerMessage;
import com.eu.habbo.messages.outgoing.MessageComposer;
import com.eu.habbo.messages.outgoing.Outgoing;

public class CalendarEditorResultComposer extends MessageComposer {
    private final boolean success;
    private final String message;

    public CalendarEditorResultComposer(boolean success, String message) {
        this.success = success;
        this.message = message;
    }

    @Override
    protected ServerMessage composeInternal() {
        this.response.init(Outgoing.CalendarEditorResultComposer);
        this.response.appendBoolean(this.success);
        this.response.appendString(this.message != null ? this.message : "");
        return this.response;
    }
}
