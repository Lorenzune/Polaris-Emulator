package com.eu.habbo.messages.outgoing.generic;

import com.eu.habbo.messages.ServerMessage;
import com.eu.habbo.messages.outgoing.MessageComposer;
import com.eu.habbo.messages.outgoing.Outgoing;

public class ExternalTextsReloadComposer extends MessageComposer {
    private final long timestamp;
    private final String urlHint;

    public ExternalTextsReloadComposer(long timestamp) {
        this(timestamp, "");
    }

    public ExternalTextsReloadComposer(long timestamp, String urlHint) {
        this.timestamp = timestamp;
        this.urlHint = urlHint != null ? urlHint : "";
    }

    @Override
    protected ServerMessage composeInternal() {
        this.response.init(Outgoing.ExternalTextsReloadComposer);
        this.response.appendString(String.valueOf(this.timestamp));
        this.response.appendString(this.urlHint);
        return this.response;
    }
}
