package com.eu.habbo.messages.outgoing.users;

import com.eu.habbo.messages.ServerMessage;
import com.eu.habbo.messages.outgoing.MessageComposer;
import com.eu.habbo.messages.outgoing.Outgoing;

import java.util.List;

public class BlockedUsersComposer extends MessageComposer {
    private final List<String> usernames;

    public BlockedUsersComposer(List<String> usernames) {
        this.usernames = List.copyOf(usernames);
    }

    @Override
    protected ServerMessage composeInternal() {
        this.response.init(Outgoing.BlockedUsersComposer);
        this.response.appendInt(this.usernames.size());

        for (String username : this.usernames) {
            this.response.appendString(username);
        }

        return this.response;
    }
}
