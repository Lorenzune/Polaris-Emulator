package com.eu.habbo.messages.outgoing.wired;

import com.eu.habbo.habbohotel.users.Habbo;
import com.eu.habbo.messages.ServerMessage;
import com.eu.habbo.messages.outgoing.MessageComposer;
import com.eu.habbo.messages.outgoing.Outgoing;

public class WiredAccountPreferencesDataComposer extends MessageComposer {
    private final Habbo habbo;

    public WiredAccountPreferencesDataComposer(Habbo habbo) {
        this.habbo = habbo;
    }

    @Override
    protected ServerMessage composeInternal() {
        this.response.init(Outgoing.WiredAccountPreferencesDataComposer);

        boolean showToolbarButton = this.habbo != null
                && this.habbo.getHabboStats() != null
                && this.habbo.getHabboStats().wiredShowToolbarButton;
        boolean showInspectButton = this.habbo != null
                && this.habbo.getHabboStats() != null
                && this.habbo.getHabboStats().wiredShowInspectButton;
        boolean showSystemNotifications = this.habbo != null
                && this.habbo.getHabboStats() != null
                && this.habbo.getHabboStats().wiredShowSystemNotifications;
        this.response.appendBoolean(showToolbarButton);
        this.response.appendBoolean(showInspectButton);
        this.response.appendBoolean(showSystemNotifications);
        return this.response;
    }
}
