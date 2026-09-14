package com.eu.habbo.messages.outgoing.guilds;

import com.eu.habbo.messages.ServerMessage;
import com.eu.habbo.messages.outgoing.MessageComposer;
import com.eu.habbo.messages.outgoing.Outgoing;

public class GuildCustomBadgeReviewResultComposer extends MessageComposer {
    private final int requestId;
    private final boolean success;
    private final boolean approved;
    private final String message;

    public GuildCustomBadgeReviewResultComposer(int requestId, boolean success, boolean approved, String message) {
        this.requestId = requestId;
        this.success = success;
        this.approved = approved;
        this.message = message;
    }

    @Override
    protected ServerMessage composeInternal() {
        this.response.init(Outgoing.GuildCustomBadgeReviewResultComposer);
        this.response.appendInt(this.requestId);
        this.response.appendBoolean(this.success);
        this.response.appendBoolean(this.approved);
        this.response.appendString(this.message == null ? "" : this.message);
        return this.response;
    }
}
