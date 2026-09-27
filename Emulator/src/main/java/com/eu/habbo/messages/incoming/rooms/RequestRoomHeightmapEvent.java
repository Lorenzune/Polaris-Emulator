package com.eu.habbo.messages.incoming.rooms;

import com.eu.habbo.Emulator;
import com.eu.habbo.habbohotel.rooms.Room;
import com.eu.habbo.habbohotel.rooms.RoomUnit;
import com.eu.habbo.messages.incoming.MessageHandler;
import com.eu.habbo.messages.outgoing.rooms.RoomHeightMapComposer;
import com.eu.habbo.messages.outgoing.rooms.RoomRelativeMapComposer;
import com.eu.habbo.messages.outgoing.rooms.users.RoomUserStatusComposer;

public class RequestRoomHeightmapEvent extends MessageHandler {
    @Override
    public void handle() throws Exception {
        if (this.client.getHabbo().getHabboInfo().getLoadingRoom() > 0) {
            Room room = Emulator.getGameEnvironment().getRoomManager().loadRoom(this.client.getHabbo().getHabboInfo().getLoadingRoom());

            if (room != null) {
                // If room is loading in background, wait for it to complete
                if (room.isLoadingInProgress()) {
                    room.waitForLoad();
                }
                
                if (room.getLayout() != null) {
                    this.client.sendResponse(new RoomRelativeMapComposer(room));

                    this.client.sendResponse(new RoomHeightMapComposer(room));

                    Emulator.getGameEnvironment().getRoomManager().enterRoom(this.client.getHabbo(), room);
                }
            }
        } else {
            // The client also requests room entry data after becoming visible
            // again. Once in a room, answer with the server's current position
            // so delayed browser rendering cannot leave the avatar stale.
            Room room = this.client.getHabbo().getHabboInfo().getCurrentRoom();
            RoomUnit roomUnit = this.client.getHabbo().getRoomUnit();

            if (room != null && room.isLoaded() && roomUnit != null && roomUnit.isInRoom()
                    && room.getHabbo(roomUnit) == this.client.getHabbo()) {
                synchronized (room.roomUnitLock) {
                    this.client.sendResponse(RoomUserStatusComposer.currentStateSnapshot(roomUnit).compose());
                }
            }
        }
    }
}
