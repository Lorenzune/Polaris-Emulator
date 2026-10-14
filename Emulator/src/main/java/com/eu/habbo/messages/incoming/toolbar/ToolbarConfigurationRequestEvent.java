package com.eu.habbo.messages.incoming.toolbar;

import com.eu.habbo.habbohotel.toolbar.ToolbarConfigurationStore;
import com.eu.habbo.habbohotel.toolbar.ToolbarConfigurationSubscribers;
import com.eu.habbo.messages.incoming.MessageHandler;
import com.eu.habbo.messages.outgoing.housekeeping.ToolbarConfigurationComposer;

/** Public, authenticated read: no housekeeping or private settings are exposed. */
public class ToolbarConfigurationRequestEvent extends MessageHandler {
    @Override
    public void handle() throws Exception {
        if (this.client.getHabbo() == null) return;
        ToolbarConfigurationSubscribers.subscribe(this.client);
        this.client.sendResponse(new ToolbarConfigurationComposer(ToolbarConfigurationStore.load()));
    }

    @Override
    public int getRatelimit() {
        return 1000;
    }
}
