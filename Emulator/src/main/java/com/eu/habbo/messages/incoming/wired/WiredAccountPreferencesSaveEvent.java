package com.eu.habbo.messages.incoming.wired;

import com.eu.habbo.habbohotel.users.Habbo;
import com.eu.habbo.messages.incoming.MessageHandler;
import com.eu.habbo.messages.outgoing.wired.WiredAccountPreferencesDataComposer;

public class WiredAccountPreferencesSaveEvent extends MessageHandler {
    @Override
    public void handle() {
        Habbo habbo = this.client.getHabbo();

        if (habbo == null) {
            return;
        }

        if (this.packet.bytesAvailable() < 3) {
            this.client.sendResponse(new WiredAccountPreferencesDataComposer(habbo));
            return;
        }

        habbo.getHabboStats().setWiredAccountPreferences(
                this.packet.readBoolean(),
                this.packet.readBoolean(),
                this.packet.readBoolean());

        this.client.sendResponse(new WiredAccountPreferencesDataComposer(habbo));
    }

    @Override
    public int getRatelimit() {
        return 250;
    }
}
