package com.eu.habbo.messages.incoming.rooms.users;

import com.eu.habbo.Emulator;
import com.eu.habbo.habbohotel.rooms.Room;
import com.eu.habbo.habbohotel.users.Habbo;
import com.eu.habbo.habbohotel.users.HabboInfo;
import com.eu.habbo.habbohotel.users.HabboManager;
import com.eu.habbo.messages.incoming.MessageHandler;
import com.eu.habbo.messages.outgoing.rooms.users.RoomUserIgnoredComposer;

public class UnIgnoreRoomUserEvent extends MessageHandler {
    @Override
    public void handle() throws Exception {
        String username = this.packet.readString();
        if (username == null || username.isEmpty()) return;

        Room room = this.client.getHabbo().getHabboInfo().getCurrentRoom();
        Habbo habbo = (room != null) ? room.getHabbo(username) : null;
        HabboInfo targetInfo = null;

        if (habbo != null) {
            targetInfo = habbo.getHabboInfo();
        } else {
            habbo = Emulator.getGameEnvironment().getHabboManager().getHabbo(username);
            if (habbo != null) {
                targetInfo = habbo.getHabboInfo();
            } else {
                targetInfo = HabboManager.getOfflineHabboInfo(username);
            }
        }

        if (targetInfo != null) {
            this.client.getHabbo().getHabboStats().unignoreUser(targetInfo.getId());
            this.client.sendResponse(new RoomUserIgnoredComposer(targetInfo.getUsername(), RoomUserIgnoredComposer.UNIGNORED));
        }
    }
}
