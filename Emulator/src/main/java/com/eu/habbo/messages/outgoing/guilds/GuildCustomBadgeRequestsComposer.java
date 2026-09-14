package com.eu.habbo.messages.outgoing.guilds;

import com.eu.habbo.habbohotel.guilds.GuildCustomBadgeRequestManager;
import com.eu.habbo.messages.ServerMessage;
import com.eu.habbo.messages.outgoing.MessageComposer;
import com.eu.habbo.messages.outgoing.Outgoing;

import java.util.List;

public class GuildCustomBadgeRequestsComposer extends MessageComposer {
    private final List<GuildCustomBadgeRequestManager.Request> requests;

    public GuildCustomBadgeRequestsComposer(List<GuildCustomBadgeRequestManager.Request> requests) {
        this.requests = requests;
    }

    @Override
    protected ServerMessage composeInternal() {
        this.response.init(Outgoing.GuildCustomBadgeRequestsComposer);
        this.response.appendInt(this.requests.size());
        for (GuildCustomBadgeRequestManager.Request request : this.requests) {
            this.response.appendInt(request.id);
            this.response.appendInt(request.guildId);
            this.response.appendInt(request.requesterId);
            this.response.appendString(request.requesterName == null ? "" : request.requesterName);
            this.response.appendString(request.guildName == null ? "" : request.guildName);
            this.response.appendString(request.badgeCode);
            this.response.appendInt(request.createdAt);
        }
        return this.response;
    }
}
