package com.eu.habbo.messages.outgoing.inventory.nicks;

import com.eu.habbo.habbohotel.users.Habbo;
import com.eu.habbo.habbohotel.users.UserNick;
import com.eu.habbo.messages.ServerMessage;
import com.eu.habbo.messages.outgoing.MessageComposer;
import com.eu.habbo.messages.outgoing.Outgoing;

import java.util.List;

public class UserNickStylesComposer extends MessageComposer {
    private final Habbo habbo;

    public UserNickStylesComposer(Habbo habbo) {
        this.habbo = habbo;
    }

    @Override
    protected ServerMessage composeInternal() {
        if (this.habbo == null) return null;

        List<UserNick> nicks = this.habbo.getInventory().getUserNicksComponent().getNicks();

        this.response.init(Outgoing.UserNickStylesComposer);
        this.response.appendInt(nicks.size());

        for (UserNick nick : nicks) {
            this.response.appendInt(nick.getId());
            this.response.appendString(nick.getColor());
            this.response.appendString(nick.getEffect());
            this.response.appendString(nick.getFont());
            this.response.appendInt(nick.getCatalogNickId());
            this.response.appendInt(nick.isActive() ? 1 : 0);
        }

        return this.response;
    }
}
