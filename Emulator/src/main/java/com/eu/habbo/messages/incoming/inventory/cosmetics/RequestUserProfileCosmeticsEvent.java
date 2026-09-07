package com.eu.habbo.messages.incoming.inventory.cosmetics;

import com.eu.habbo.habbohotel.users.Habbo;
import com.eu.habbo.messages.incoming.MessageHandler;
import com.eu.habbo.messages.outgoing.inventory.cosmetics.UserProfileCosmeticsComposer;

public class RequestUserProfileCosmeticsEvent extends MessageHandler {
    @Override
    public void handle() throws Exception {
        Habbo habbo = this.client.getHabbo();
        if (habbo == null) return;

        this.client.sendResponse(new UserProfileCosmeticsComposer(habbo));
    }
}
