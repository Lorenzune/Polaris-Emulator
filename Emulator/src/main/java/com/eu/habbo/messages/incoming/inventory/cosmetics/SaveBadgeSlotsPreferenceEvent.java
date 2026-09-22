package com.eu.habbo.messages.incoming.inventory.cosmetics;

import com.eu.habbo.habbohotel.users.Habbo;
import com.eu.habbo.messages.incoming.MessageHandler;
import com.eu.habbo.messages.outgoing.inventory.cosmetics.UserProfileCosmeticsComposer;

public class SaveBadgeSlotsPreferenceEvent extends MessageHandler {
    @Override
    public void handle() throws Exception {
        Habbo habbo = this.client.getHabbo();
        if (habbo == null || habbo.getHabboStats() == null) return;

        boolean requested = this.packet.readBoolean();
        boolean ownsSlots = habbo.getInventory() != null
                && habbo.getInventory().getProfileCosmeticsComponent() != null
                && habbo.getInventory().getProfileCosmeticsComponent().hasAnyCosmetic("slot");

        habbo.getHabboStats().setBadgeSlotsActive(requested && ownsSlots);
        this.client.sendResponse(new UserProfileCosmeticsComposer(habbo));
    }
}
