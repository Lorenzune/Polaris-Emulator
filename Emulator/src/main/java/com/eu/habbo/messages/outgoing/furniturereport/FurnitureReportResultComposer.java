package com.eu.habbo.messages.outgoing.furniturereport;

import com.eu.habbo.messages.ServerMessage;
import com.eu.habbo.messages.outgoing.MessageComposer;
import com.eu.habbo.messages.outgoing.Outgoing;

public class FurnitureReportResultComposer extends MessageComposer {
    private final boolean success;
    private final int issueId;
    private final String message;

    public FurnitureReportResultComposer(boolean success, int issueId, String message) {
        this.success = success;
        this.issueId = issueId;
        this.message = message == null ? "" : message;
    }

    @Override
    protected ServerMessage composeInternal() {
        this.response.init(Outgoing.FurnitureReportResultComposer);
        this.response.appendBoolean(this.success);
        this.response.appendInt(this.issueId);
        this.response.appendString(this.message);
        return this.response;
    }
}
