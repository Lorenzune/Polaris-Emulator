package com.eu.habbo.messages.incoming.wired;

import com.eu.habbo.habbohotel.items.interactions.wired.effects.WiredEffectGiveVariable;
import com.eu.habbo.habbohotel.rooms.Room;
import com.eu.habbo.habbohotel.rooms.RoomUnit;
import com.eu.habbo.habbohotel.users.HabboItem;
import com.eu.habbo.habbohotel.wired.core.WiredInternalVariableSupport;
import com.eu.habbo.messages.incoming.MessageHandler;

public class WiredUserVariableUpdateEvent extends MessageHandler {
    private static final int TARGET_USER = 0;
    private static final int TARGET_ROOM = 3;
    private static final int INTERNAL_USER_HAND_ITEM = -1003;
    private static final int INTERNAL_USER_POSITION_X = -1004;
    private static final int INTERNAL_USER_POSITION_Y = -1005;
    private static final int INTERNAL_USER_DIRECTION = -1006;
    private static final int INTERNAL_FURNI_STATE = -1011;
    private static final int INTERNAL_FURNI_POSITION_X = -1012;
    private static final int INTERNAL_FURNI_POSITION_Y = -1013;
    private static final int INTERNAL_FURNI_ROTATION = -1014;
    private static final int INTERNAL_FURNI_ALTITUDE = -1015;
    private static final int INTERNAL_FURNI_OPACITY = -1001;
    private static final int INTERNAL_FURNI_GRAVITY = -1002;

    @Override
    public void handle() throws Exception {
        Room room = currentRoom();

        if (room == null) {
            return;
        }

        if (!room.canModifyWired(this.client.getHabbo())) {
            room.getUserVariableManager().sendSnapshot(this.client.getHabbo());
            return;
        }

        if (this.packet.bytesAvailable() < 16) {
            room.getUserVariableManager().sendSnapshot(this.client.getHabbo());
            return;
        }

        int targetType = this.packet.readInt();
        int targetId = this.packet.readInt();
        int definitionItemId = this.packet.readInt();
        int value = this.packet.readInt();

        String internalKey = resolveInternalKey(targetType, definitionItemId);

        if (internalKey != null) {
            if (targetType == TARGET_USER) {
                RoomUnit roomUnit = resolveRoomUnit(room, targetId);

                if (WiredUserInspectMovePolicy.canMove(room, this.client.getHabbo(), roomUnit)) {
                    WiredInternalVariableSupport.writeUserValue(room, roomUnit, internalKey, value);
                }
            } else {
                HabboItem item = room.getHabboItem(targetId);

                if (item != null) {
                    WiredInternalVariableSupport.writeFurniValue(room, item, internalKey, value);
                }
            }

            return;
        }

        if (definitionItemId < 0) {
            return;
        }

        if (targetType == WiredEffectGiveVariable.TARGET_FURNI) {
            room.getFurniVariableManager().updateVariableValue(targetId, definitionItemId, value);
            room.getFurniVariableManager().sendSnapshot(this.client.getHabbo());
            return;
        }

        if (targetType == TARGET_ROOM) {
            room.getRoomVariableManager().updateVariableValue(definitionItemId, value);
            room.getRoomVariableManager().sendSnapshot(this.client.getHabbo());
            return;
        }

        room.getUserVariableManager().updateVariableValue(targetId, definitionItemId, value);
        room.getUserVariableManager().sendSnapshot(this.client.getHabbo());
    }

    @Override
    public int getRatelimit() {
        return 150;
    }

    private RoomUnit resolveRoomUnit(Room room, int roomUnitId) {
        if (room == null || roomUnitId <= 0) {
            return null;
        }

        for (RoomUnit roomUnit : room.getRoomUnits()) {
            if (roomUnit != null && roomUnit.getId() == roomUnitId) {
                return roomUnit;
            }
        }

        return null;
    }

    private String resolveInternalKey(int targetType, int definitionItemId) {
        if (targetType == TARGET_USER) {
            return switch (definitionItemId) {
                case INTERNAL_USER_HAND_ITEM -> "@handitem";
                case INTERNAL_USER_POSITION_X -> "@position_x";
                case INTERNAL_USER_POSITION_Y -> "@position_y";
                case INTERNAL_USER_DIRECTION -> "@direction";
                default -> null;
            };
        }

        if (targetType == WiredEffectGiveVariable.TARGET_FURNI) {
            return switch (definitionItemId) {
                case INTERNAL_FURNI_STATE -> "@state";
                case INTERNAL_FURNI_POSITION_X -> "@position_x";
                case INTERNAL_FURNI_POSITION_Y -> "@position_y";
                case INTERNAL_FURNI_ROTATION -> "@rotation";
                case INTERNAL_FURNI_ALTITUDE -> "@altitude";
                case INTERNAL_FURNI_OPACITY -> "@opacity";
                case INTERNAL_FURNI_GRAVITY -> "@gravity";
                default -> null;
            };
        }

        return null;
    }
}
