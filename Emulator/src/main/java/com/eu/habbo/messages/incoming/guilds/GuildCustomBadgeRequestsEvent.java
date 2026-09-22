package com.eu.habbo.messages.incoming.guilds;

import com.eu.habbo.habbohotel.guilds.GuildCustomBadgeRequestManager;
import com.eu.habbo.habbohotel.permissions.Permission;
import com.eu.habbo.habbohotel.users.Habbo;
import com.eu.habbo.messages.incoming.MessageHandler;
import com.eu.habbo.messages.outgoing.guilds.GuildCustomBadgeRequestsComposer;

public class GuildCustomBadgeRequestsEvent extends MessageHandler {
    @Override
    public void handle() {
        if (!isReviewer(this.client.getHabbo())) return;
        this.client.sendResponse(new GuildCustomBadgeRequestsComposer(GuildCustomBadgeRequestManager.listPending()));
    }

    static boolean isReviewer(Habbo habbo) {
        return habbo != null
                && habbo.getHabboInfo().getRank() != null
                && habbo.getHabboInfo().getRank().getId() >= 7
                && habbo.hasPermission(Permission.ACC_SUPPORTTOOL);
    }
}
