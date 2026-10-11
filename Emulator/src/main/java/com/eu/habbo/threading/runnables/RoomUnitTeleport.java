package com.eu.habbo.threading.runnables;

import com.eu.habbo.habbohotel.rooms.Room;
import com.eu.habbo.habbohotel.rooms.RoomTile;
import com.eu.habbo.habbohotel.rooms.RoomUnit;
import com.eu.habbo.habbohotel.rooms.RoomUnitStatus;
import com.eu.habbo.habbohotel.users.HabboItem;
import com.eu.habbo.habbohotel.wired.core.WiredFreezeUtil;
import java.util.LinkedList;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class RoomUnitTeleport implements Runnable {
    private static final Logger LOGGER = LoggerFactory.getLogger(RoomUnitTeleport.class);

    private RoomUnit roomUnit;
    private Room room;
    private int x;
    private int y;
    private double z;
    private final long roomGeneration;

    public RoomUnitTeleport(RoomUnit roomUnit, Room room, int x, int y, double z, int newEffect) {
        this.roomUnit = roomUnit;
        this.room = room;
        this.x = x;
        this.y = y;
        this.z = z;
        this.roomGeneration = room == null ? 0L : room.getLifecycleGeneration();
        // newEffect parameter is unused
    }

    @Override
    public void run() {
        // A delayed in-room teleport must never mutate an avatar that has changed rooms.
        if (roomUnit == null
                || roomUnit.getRoom() != room
                || (room != null && room.getLifecycleGeneration() != roomGeneration)) return;

        if (room == null || !room.isLoaded() || room.getLayout() == null) {
            roomUnit.isWiredTeleporting = false;
            WiredFreezeUtil.restoreWalkState(roomUnit);
            return;
        }

        if (roomUnit.isLeavingTeleporter) {
            roomUnit.isWiredTeleporting = false;
            return;
        }

        RoomTile lastLocation = this.roomUnit.getCurrentLocation();
        RoomTile newLocation = this.room.getLayout().getTile((short) this.x, (short) this.y);
        if (lastLocation == null || newLocation == null) {
            roomUnit.isWiredTeleporting = false;
            WiredFreezeUtil.restoreWalkState(roomUnit);
            return;
        }

        WiredFreezeUtil.onTeleport(this.room, this.roomUnit);

        HabboItem topItem =
                this.room.getTopItemAt(this.roomUnit.getCurrentLocation().x, this.roomUnit.getCurrentLocation().y);
        if (topItem != null) {
            try {
                topItem.onWalkOff(this.roomUnit, this.room, new Object[] {this});
            } catch (Exception e) {
                LOGGER.error("Caught exception", e);
            }
        }
        if (roomUnit.getRoom() != room) return;
        this.roomUnit.setPath(new LinkedList<>());
        this.roomUnit.setCurrentLocation(newLocation);
        this.roomUnit.setPreviousLocation(newLocation);
        this.roomUnit.setZ(this.z);
        this.roomUnit.setPreviousLocationZ(this.z);
        this.roomUnit.removeStatus(RoomUnitStatus.MOVE);
        // ServerMessage teleportMessage = new RoomUnitOnRollerComposer(this.roomUnit, newLocation,
        // this.room).compose();
        this.roomUnit.setLocation(newLocation);
        this.roomUnit.setGoalLocation(newLocation);
        // this.room.sendComposer(teleportMessage);
        this.roomUnit.statusUpdate(true);
        roomUnit.isWiredTeleporting = false;
        WiredFreezeUtil.restoreWalkState(this.roomUnit);

        this.room.updateHabbosAt(newLocation.x, newLocation.y);
        this.room.updateBotsAt(newLocation.x, newLocation.y);

        topItem = room.getTopItemAt(x, y);
        if (topItem != null
                && roomUnit.getCurrentLocation().equals(room.getLayout().getTile((short) x, (short) y))) {
            try {
                topItem.onWalkOn(roomUnit, room, new Object[] {lastLocation, newLocation, this});
            } catch (Exception e) {
            }
        }
    }
}
