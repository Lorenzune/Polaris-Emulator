package com.eu.habbo.messages.incoming.users;

import com.eu.habbo.Emulator;
import com.eu.habbo.messages.incoming.MessageHandler;
import com.eu.habbo.messages.outgoing.users.AvatarLevelComposer;

public class RequestAvatarLevelEvent extends MessageHandler {
    @Override public int getRatelimit() { return 1000; }
    @Override public void handle() throws Exception {
        var snapshot = Emulator.getGameEnvironment().getAvatarLevelManager().refresh(this.client.getHabbo());
        this.client.sendResponse(new AvatarLevelComposer(snapshot));
    }
}
