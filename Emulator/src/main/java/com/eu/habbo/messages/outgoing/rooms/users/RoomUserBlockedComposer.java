package com.eu.habbo.messages.outgoing.rooms.users;

import com.eu.habbo.messages.ServerMessage;
import com.eu.habbo.messages.outgoing.MessageComposer;
import com.eu.habbo.messages.outgoing.Outgoing;

public class RoomUserBlockedComposer extends MessageComposer {
    public final static int BLOCKED = 1;
    public final static int UNBLOCKED = 3;

    private final String username;
    private final int state;

    public RoomUserBlockedComposer(String username, int state) {
        this.username = username != null ? username : "";
        this.state = state;
    }

    @Override
    protected ServerMessage composeInternal() {
        this.response.init(Outgoing.RoomUserBlockedComposer);
        this.response.appendInt(this.state);
        this.response.appendString(this.username);
        return this.response;
    }

    public String getUsername() {
        return this.username;
    }

    public int getState() {
        return this.state;
    }
}
