package com.eu.habbo.messages.outgoing.housekeeping;

import com.eu.habbo.habbohotel.toolbar.ToolbarConfiguration;
import com.eu.habbo.habbohotel.toolbar.ToolbarConfigurationStore;
import com.eu.habbo.messages.ServerMessage;
import com.eu.habbo.messages.outgoing.MessageComposer;
import com.eu.habbo.messages.outgoing.Outgoing;

public class ToolbarConfigurationComposer extends MessageComposer {
    private final ToolbarConfigurationStore.State state;

    public ToolbarConfigurationComposer(ToolbarConfigurationStore.State state) {
        this.state = state;
    }

    @Override
    protected ServerMessage composeInternal() {
        this.response.init(Outgoing.ToolbarConfigurationComposer);
        this.response.appendInt(state.revision());
        this.response.appendString(ToolbarConfiguration.JSON.toJson(state.configuration()));
        return this.response;
    }
}
