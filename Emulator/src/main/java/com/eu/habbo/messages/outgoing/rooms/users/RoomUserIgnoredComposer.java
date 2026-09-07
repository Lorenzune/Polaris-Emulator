package com.eu.habbo.messages.outgoing.rooms.users;

import com.eu.habbo.habbohotel.users.Habbo;
import com.eu.habbo.messages.ServerMessage;
import com.eu.habbo.messages.outgoing.MessageComposer;
import com.eu.habbo.messages.outgoing.Outgoing;

public class RoomUserIgnoredComposer extends MessageComposer {
    public final static int IGNORED = 1;
    public final static int MUTED = 2;
    public final static int UNIGNORED = 3;

    private final Habbo habbo;
    private final String username;
    private final int state;

    public RoomUserIgnoredComposer(Habbo habbo, int state) {
        this.habbo = habbo;
        this.username = (habbo != null && habbo.getHabboInfo() != null) ? habbo.getHabboInfo().getUsername() : "";
        this.state = state;
    }

    public RoomUserIgnoredComposer(String username, int state) {
        this.habbo = null;
        this.username = username != null ? username : "";
        this.state = state;
    }

    @Override
    protected ServerMessage composeInternal() {
        this.response.init(Outgoing.RoomUserIgnoredComposer);
        this.response.appendInt(this.state);
        this.response.appendString(this.username);
        return this.response;
    }

    public Habbo getHabbo() {
        return this.habbo;
    }

    public String getUsername() {
        return this.username;
    }

    public int getState() {
        return this.state;
    }
}
