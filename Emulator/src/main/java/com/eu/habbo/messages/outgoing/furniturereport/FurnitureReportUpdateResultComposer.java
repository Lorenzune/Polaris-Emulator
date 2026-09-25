package com.eu.habbo.messages.outgoing.furniturereport;

import com.eu.habbo.messages.ServerMessage;
import com.eu.habbo.messages.outgoing.MessageComposer;
import com.eu.habbo.messages.outgoing.Outgoing;

public class FurnitureReportUpdateResultComposer extends MessageComposer {
    private final boolean success;
    private final int issueId;
    private final String message;
    private final String dataJson;

    public FurnitureReportUpdateResultComposer(boolean success, int issueId, String message, String dataJson) {
        this.success = success;
        this.issueId = issueId;
        this.message = message == null ? "" : message;
        this.dataJson = dataJson == null ? "{}" : dataJson;
    }

    @Override
    protected ServerMessage composeInternal() {
        this.response.init(Outgoing.FurnitureReportUpdateResultComposer);
        this.response.appendBoolean(this.success);
        this.response.appendInt(this.issueId);
        this.response.appendString(this.message);
        this.response.appendString(this.dataJson);
        return this.response;
    }
}
