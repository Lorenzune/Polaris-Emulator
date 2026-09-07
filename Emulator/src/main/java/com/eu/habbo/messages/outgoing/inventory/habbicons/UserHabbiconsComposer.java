package com.eu.habbo.messages.outgoing.inventory.habbicons;

import com.eu.habbo.habbohotel.permissions.Permission;
import com.eu.habbo.habbohotel.users.Habbo;
import com.eu.habbo.messages.ServerMessage;
import com.eu.habbo.messages.outgoing.MessageComposer;
import com.eu.habbo.messages.outgoing.Outgoing;

import java.util.Set;

public class UserHabbiconsComposer extends MessageComposer {
    private final Habbo habbo;

    public UserHabbiconsComposer(Habbo habbo) {
        this.habbo = habbo;
    }

    @Override
    protected ServerMessage composeInternal() {
        this.response.init(Outgoing.UserHabbiconsComposer);

        if (this.habbo == null) {
            this.response.appendInt(0);
            this.response.appendBoolean(false);
            return this.response;
        }

        Set<Integer> ownedHabbicons = this.habbo.getInventory().getHabbiconsComponent() != null
                ? this.habbo.getInventory().getHabbiconsComponent().getOwnedHabbicons()
                : Set.of();

        boolean hasAllAccess = this.habbo.hasPermission(Permission.ACC_ANYHABBICON)
                || this.habbo.getHabboInfo().getRank().getId() >= 6;

        this.response.appendInt(ownedHabbicons.size());
        for (int habbiconId : ownedHabbicons) {
            this.response.appendInt(habbiconId);
        }
        this.response.appendBoolean(hasAllAccess);

        return this.response;
    }
}
