package com.eu.habbo.messages.incoming.wired;

import com.eu.habbo.habbohotel.rooms.Room;
import com.eu.habbo.messages.incoming.MessageHandler;

/** Restores the room-load baseline used by Wired's Room state rollback control. */
public class WiredRoomRollbackEvent extends MessageHandler {
    @Override
    public void handle() throws Exception {
        Room room = currentRoom();
        if (room == null || !room.isOwner(this.client.getHabbo())) {
            return;
        }

        room.rollbackWiredRoomState();
    }

    @Override
    public int getRatelimit() {
        return 1_000;
    }
}
