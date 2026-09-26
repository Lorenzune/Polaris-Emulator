package com.eu.habbo.habbohotel.wired.core;

import com.eu.habbo.habbohotel.rooms.Room;
import com.eu.habbo.habbohotel.rooms.RoomUnit;
import com.eu.habbo.messages.outgoing.rooms.users.RoomUserStatusComposer;

public final class WiredFreezeUtil {
    private static final String CACHE_ACTIVE = "wired.freeze.active";
    private static final String CACHE_EFFECT_ID = "wired.freeze.effect_id";
    private static final String CACHE_CANCEL_ON_TELEPORT = "wired.freeze.cancel_on_teleport";
    private static final String CACHE_PREVIOUS_CAN_WALK = "wired.freeze.previous_can_walk";

    private WiredFreezeUtil() {
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

    public static void freeze(Room room, RoomUnit roomUnit, int effectId, boolean cancelOnTeleport) {
        if (room == null || roomUnit == null || effectId <= 0) {
            return;
        }

        if (!isFrozen(roomUnit)) {
            boolean previousCanWalk = BuildersToolsFreezeUtil.isFrozen(roomUnit)
                    ? BuildersToolsFreezeUtil.getPreviousCanWalk(roomUnit)
                    : roomUnit.canWalk();
            roomUnit.getCacheable().put(CACHE_PREVIOUS_CAN_WALK, previousCanWalk);
        }
        roomUnit.getCacheable().put(CACHE_ACTIVE, true);
        roomUnit.getCacheable().put(CACHE_EFFECT_ID, effectId);
        roomUnit.getCacheable().put(CACHE_CANCEL_ON_TELEPORT, cancelOnTeleport);

        roomUnit.stopWalking();
        roomUnit.setCanWalk(false);
        roomUnit.statusUpdate(true);

        room.giveEffect(roomUnit, effectId, Integer.MAX_VALUE);
        room.sendComposer(new RoomUserStatusComposer(roomUnit).compose());
    }

    public static void unfreeze(Room room, RoomUnit roomUnit) {
        if (roomUnit == null) {
            return;
        }

        Object previousState = roomUnit.getCacheable().remove(CACHE_PREVIOUS_CAN_WALK);
        roomUnit.getCacheable().remove(CACHE_ACTIVE);
        roomUnit.getCacheable().remove(CACHE_EFFECT_ID);
        roomUnit.getCacheable().remove(CACHE_CANCEL_ON_TELEPORT);

        roomUnit.stopWalking();
        roomUnit.setCanWalk(!BuildersToolsFreezeUtil.isFrozen(roomUnit) && Boolean.TRUE.equals(previousState));
        roomUnit.statusUpdate(true);

        if (room != null) {
            room.giveEffect(roomUnit, 0, -1);
            room.sendComposer(new RoomUserStatusComposer(roomUnit).compose());
        } else {
            roomUnit.setEffectId(0, 0);
        }
    }

    public static void onTeleport(Room room, RoomUnit roomUnit) {
        if (!isFrozen(roomUnit)) {
            return;
        }

        if (Boolean.TRUE.equals(roomUnit.getCacheable().get(CACHE_CANCEL_ON_TELEPORT))) {
            unfreeze(room, roomUnit);
        }
    }

    public static void restoreWalkState(RoomUnit roomUnit) {
        if (roomUnit == null) {
            return;
        }

        roomUnit.setCanWalk(!isFrozen(roomUnit) && !BuildersToolsFreezeUtil.isFrozen(roomUnit));
    }
}
