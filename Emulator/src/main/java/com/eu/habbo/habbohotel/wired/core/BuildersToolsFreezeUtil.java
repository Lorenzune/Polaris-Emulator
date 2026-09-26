package com.eu.habbo.habbohotel.wired.core;

import com.eu.habbo.habbohotel.rooms.Room;
import com.eu.habbo.habbohotel.rooms.RoomUnit;
import com.eu.habbo.habbohotel.users.Habbo;
import com.eu.habbo.messages.outgoing.rooms.users.RoomUserStatusComposer;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public final class BuildersToolsFreezeUtil {
    private static final String CACHE_ACTIVE = "builders-tools.freeze.active";
    private static final String CACHE_PREVIOUS_CAN_WALK = "builders-tools.freeze.previous-can-walk";
    private static final Map<Integer, Integer> ACTIVE_ROOM_FREEZES = new ConcurrentHashMap<>();

    private BuildersToolsFreezeUtil() {}

    public static void setRoomFrozen(Room room, int actorId, boolean frozen) {
        if (room == null) {
            return;
        }

        if (frozen) {
            ACTIVE_ROOM_FREEZES.put(room.getId(), actorId);
            for (Habbo habbo : room.getHabbos()) {
                freeze(room, habbo.getRoomUnit());
            }
            return;
        }

        ACTIVE_ROOM_FREEZES.remove(room.getId());
        for (Habbo habbo : room.getHabbos()) {
            unfreeze(room, habbo.getRoomUnit());
        }
    }

    public static void onHabboAdded(Room room, RoomUnit roomUnit) {
        if (room != null && ACTIVE_ROOM_FREEZES.containsKey(room.getId())) {
            freeze(room, roomUnit);
        }
    }

    public static void onHabboRemoved(Room room, Habbo habbo) {
        if (room == null || habbo == null) {
            return;
        }

        Integer actorId = ACTIVE_ROOM_FREEZES.get(room.getId());
        if (actorId != null && actorId == habbo.getHabboInfo().getId()) {
            setRoomFrozen(room, actorId, false);
        } else {
            unfreeze(room, habbo.getRoomUnit());
        }
    }

    public static void clearRoom(Room room) {
        if (room == null) {
            return;
        }

        ACTIVE_ROOM_FREEZES.remove(room.getId());
        for (Habbo habbo : room.getHabbos()) {
            unfreeze(room, habbo.getRoomUnit());
        }
    }

    public static boolean isFrozen(RoomUnit roomUnit) {
        return roomUnit != null && Boolean.TRUE.equals(roomUnit.getCacheable().get(CACHE_ACTIVE));
    }

    public static boolean getPreviousCanWalk(RoomUnit roomUnit) {
        if (roomUnit == null) {
            return false;
        }

        Object previousState = roomUnit.getCacheable().get(CACHE_PREVIOUS_CAN_WALK);
        return previousState instanceof Boolean ? (Boolean) previousState : roomUnit.canWalk();
    }

    private static void freeze(Room room, RoomUnit roomUnit) {
        if (room == null || roomUnit == null || isFrozen(roomUnit)) {
            return;
        }

        boolean previousCanWalk =
                WiredFreezeUtil.isFrozen(roomUnit) ? WiredFreezeUtil.getPreviousCanWalk(roomUnit) : roomUnit.canWalk();
        roomUnit.getCacheable().put(CACHE_PREVIOUS_CAN_WALK, previousCanWalk);
        roomUnit.getCacheable().put(CACHE_ACTIVE, true);
        roomUnit.stopWalking();
        roomUnit.setCanWalk(false);
        roomUnit.statusUpdate(true);
        room.sendComposer(new RoomUserStatusComposer(roomUnit).compose());
    }

    private static void unfreeze(Room room, RoomUnit roomUnit) {
        if (!isFrozen(roomUnit)) {
            return;
        }

        Object previousState = roomUnit.getCacheable().remove(CACHE_PREVIOUS_CAN_WALK);
        roomUnit.getCacheable().remove(CACHE_ACTIVE);
        roomUnit.stopWalking();
        roomUnit.setCanWalk(!WiredFreezeUtil.isFrozen(roomUnit) && Boolean.TRUE.equals(previousState));
        roomUnit.statusUpdate(true);

        if (room != null) {
            room.sendComposer(new RoomUserStatusComposer(roomUnit).compose());
        }
    }
}
