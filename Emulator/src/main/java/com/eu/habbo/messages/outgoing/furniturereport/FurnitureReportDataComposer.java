package com.eu.habbo.messages.outgoing.furniturereport;

import com.eu.habbo.messages.ServerMessage;
import com.eu.habbo.messages.outgoing.MessageComposer;
import com.eu.habbo.messages.outgoing.Outgoing;

public class FurnitureReportDataComposer extends MessageComposer {
    private final boolean success;
    private final String error;
    private final String dataJson;

    public FurnitureReportDataComposer(boolean success, String error, String dataJson) {
        this.success = success;
        this.error = error == null ? "" : error;
        this.dataJson = dataJson == null ? "{}" : dataJson;
    }

    @Override
    protected ServerMessage composeInternal() {
        this.response.init(Outgoing.FurnitureReportDataComposer);
        this.response.appendBoolean(this.success);
        this.response.appendString(this.error);
        this.response.appendString(this.dataJson);
        return this.response;
    }
}
