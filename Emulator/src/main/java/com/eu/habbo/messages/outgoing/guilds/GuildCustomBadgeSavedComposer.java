package com.eu.habbo.messages.outgoing.guilds;

import com.eu.habbo.messages.ServerMessage;
import com.eu.habbo.messages.outgoing.MessageComposer;
import com.eu.habbo.messages.outgoing.Outgoing;

public class GuildCustomBadgeSavedComposer extends MessageComposer {
    private final int guildId;
    private final String badgeCode;
    private final String status;

    public GuildCustomBadgeSavedComposer(int guildId, String badgeCode) {
        this(guildId, badgeCode, "approved");
    }

    public GuildCustomBadgeSavedComposer(int guildId, String badgeCode, String status) {
        this.guildId = guildId;
        this.badgeCode = badgeCode;
        this.status = status;
    }

    @Override
    protected ServerMessage composeInternal() {
        this.response.init(Outgoing.GuildCustomBadgeSavedComposer);
        this.response.appendInt(this.guildId);
        this.response.appendString(this.badgeCode);
        this.response.appendString(this.status);
        return this.response;
    }
}
