package com.eu.habbo.messages.incoming.rooms.users;

import com.eu.habbo.habbohotel.rooms.Room;
import com.eu.habbo.habbohotel.wired.core.BuildersToolsFreezeUtil;
import com.eu.habbo.messages.incoming.MessageHandler;

public class BuildersToolsFreezeEvent extends MessageHandler {
    @Override
    public void handle() throws Exception {
        Room room = this.client.getHabbo().getHabboInfo().getCurrentRoom();
        if (room == null) {
            return;
        }

        if (room.getOwnerId() != this.client.getHabbo().getHabboInfo().getId() && !room.hasRights(this.client.getHabbo())) {
            return;
        }

        BuildersToolsFreezeUtil.setRoomFrozen(
                room,
                this.client.getHabbo().getHabboInfo().getId(),
                this.packet.readBoolean());
    }
}
