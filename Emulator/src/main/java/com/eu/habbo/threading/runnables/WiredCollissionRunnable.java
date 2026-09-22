package com.eu.habbo.threading.runnables;

import com.eu.habbo.habbohotel.rooms.Room;
import com.eu.habbo.habbohotel.rooms.RoomUnit;
import com.eu.habbo.habbohotel.users.HabboItem;
import com.eu.habbo.habbohotel.wired.core.WiredManager;

public class WiredCollissionRunnable implements Runnable {
    public final RoomUnit roomUnit;
    public final Room room;
    public final HabboItem item;

    public WiredCollissionRunnable(RoomUnit roomUnit, Room room) {
        this(roomUnit, room, null);
    }

    public WiredCollissionRunnable(RoomUnit roomUnit, Room room, HabboItem item) {
        this.roomUnit = roomUnit;
        this.room = room;
        this.item = item;
    }

    @Override
    public void run() {
        if (this.roomUnit == null || this.room == null || !this.room.isLoaded()) return;
        try {
            WiredManager.triggerBotCollision(room, roomUnit, item);
        } catch (Exception e) {
            // Prevent task from crashing the thread pool
        }
    }
}
