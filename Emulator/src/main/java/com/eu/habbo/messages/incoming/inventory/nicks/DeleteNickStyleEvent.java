package com.eu.habbo.messages.incoming.inventory.nicks;

import com.eu.habbo.habbohotel.users.UserNick;
import com.eu.habbo.messages.incoming.MessageHandler;
import com.eu.habbo.messages.outgoing.inventory.nicks.ActiveNickStyleUpdatedComposer;
import com.eu.habbo.messages.outgoing.inventory.nicks.UserNickStylesComposer;
import com.eu.habbo.messages.outgoing.rooms.users.RoomUserDataComposer;

public class DeleteNickStyleEvent extends MessageHandler {
    @Override
    public void handle() throws Exception {
        int nickId = this.packet.readInt();

        UserNick nick = this.client.getHabbo().getInventory().getUserNicksComponent().getNick(nickId);

        if (nick == null) return;

        boolean wasActive = nick.isActive();
        this.client.getHabbo().getInventory().getUserNicksComponent().removeNick(nick);
        nick.delete();

        this.client.sendResponse(new UserNickStylesComposer(this.client.getHabbo()));

        if (wasActive) {
            this.client.sendResponse(new ActiveNickStyleUpdatedComposer(0, "", "", ""));
            if (this.client.getHabbo().getHabboInfo().getCurrentRoom() != null) {
                this.client.getHabbo().getHabboInfo().getCurrentRoom().sendComposer(new RoomUserDataComposer(this.client.getHabbo()).compose());
            }
        }
    }
}
