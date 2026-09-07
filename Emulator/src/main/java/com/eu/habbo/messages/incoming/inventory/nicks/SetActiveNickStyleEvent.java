package com.eu.habbo.messages.incoming.inventory.nicks;

import com.eu.habbo.habbohotel.users.UserNick;
import com.eu.habbo.messages.incoming.MessageHandler;
import com.eu.habbo.messages.outgoing.inventory.nicks.ActiveNickStyleUpdatedComposer;
import com.eu.habbo.messages.outgoing.inventory.nicks.UserNickStylesComposer;
import com.eu.habbo.messages.outgoing.rooms.users.RoomUserDataComposer;

public class SetActiveNickStyleEvent extends MessageHandler {
    @Override
    public int getRatelimit() {
        return 1000;
    }

    @Override
    public void handle() throws Exception {
        int nickId = this.packet.readInt();

        if (nickId <= 0) {
            this.client.getHabbo().getInventory().getUserNicksComponent().deactivateAll();
            this.client.sendResponse(new ActiveNickStyleUpdatedComposer(0, "", "", ""));
            this.client.sendResponse(new UserNickStylesComposer(this.client.getHabbo()));

            if (this.client.getHabbo().getHabboInfo().getCurrentRoom() != null) {
                this.client.getHabbo().getHabboInfo().getCurrentRoom().sendComposer(new RoomUserDataComposer(this.client.getHabbo()).compose());
            }
            return;
        }

        UserNick nick = this.client.getHabbo().getInventory().getUserNicksComponent().getNick(nickId);

        if (nick == null) return;

        this.client.getHabbo().getInventory().getUserNicksComponent().setActive(nickId);
        this.client.sendResponse(new ActiveNickStyleUpdatedComposer(nick.getId(), nick.getColor(), nick.getEffect(), nick.getFont()));
        this.client.sendResponse(new UserNickStylesComposer(this.client.getHabbo()));

        if (this.client.getHabbo().getHabboInfo().getCurrentRoom() != null) {
            this.client.getHabbo().getHabboInfo().getCurrentRoom().sendComposer(new RoomUserDataComposer(this.client.getHabbo()).compose());
        }
    }
}
