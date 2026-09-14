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

        if (this.habbo == null) {
            this.response.appendBoolean(false);
            this.response.appendBoolean(false);
            this.response.appendBoolean(false);
            return this.response;
        }

        this.response.appendBoolean(this.habbo.getHabboStats().wiredShowToolbarButton);
        this.response.appendBoolean(this.habbo.getHabboStats().wiredShowInspectButton);
        this.response.appendBoolean(this.habbo.getHabboStats().wiredShowSystemNotifications);
        return this.response;
    }
}
