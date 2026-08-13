package com.eu.habbo.messages.outgoing.inventory;

import com.eu.habbo.habbohotel.users.Habbo;
import com.eu.habbo.habbohotel.users.HabboBadge;
import com.eu.habbo.messages.ServerMessage;
import com.eu.habbo.messages.outgoing.MessageComposer;
import com.eu.habbo.messages.outgoing.Outgoing;

import java.util.List;

public class InventoryBadgesComposer extends MessageComposer {
    private final Habbo habbo;

    public InventoryBadgesComposer(Habbo habbo) {
        this.habbo = habbo;
    }

    @Override
    protected ServerMessage composeInternal() {
        if (this.habbo == null)
            return null;

        List<HabboBadge> badges = this.habbo.getInventory().getBadgesComponent().getBadgesSnapshot();
        List<HabboBadge> equippedBadges = this.habbo.getInventory().getBadgesComponent().getWearingBadges();

        this.response.init(Outgoing.InventoryBadgesComposer);

        this.response.appendInt(badges.size());
        for (HabboBadge badge : badges) {
            this.response.appendInt(badge.getId());
            this.response.appendString(badge.getCode());
        }

        this.response.appendInt(equippedBadges.size());

        for (HabboBadge badge : equippedBadges) {
            this.response.appendInt(badge.getSlot());
            this.response.appendString(badge.getCode());
        }

        return this.response;
    }

    public Habbo getHabbo() {
        return habbo;
    }
}
