package com.eu.habbo.messages.outgoing.mentions;

import com.eu.habbo.messages.ServerMessage;
import com.eu.habbo.messages.outgoing.MessageComposer;
import java.util.List;

public class MentionSettingsComposer extends MessageComposer {
    private final List<String> users;
    private final String error;

    public MentionSettingsComposer(List<String> users, String error) {
        this.users = users;
        this.error = error;
    }

    @Override
    protected ServerMessage composeInternal() {
        this.response.init(4806);
        this.response.appendString(this.error);
        this.response.appendInt(this.users.size());
        for (String user : this.users) this.response.appendString(user);
        return this.response;
    }
}
