package com.eu.habbo.messages.incoming.rooms.items;

import com.eu.habbo.habbohotel.dailytasks.DailyTaskActionMatcher;
import com.eu.habbo.habbohotel.rooms.FurnitureMovementError;
import com.eu.habbo.habbohotel.rooms.Room;
import com.eu.habbo.habbohotel.rooms.RoomTile;
import com.eu.habbo.habbohotel.users.HabboItem;
import com.eu.habbo.messages.incoming.MessageHandler;
import com.eu.habbo.messages.ServerMessage;
import com.eu.habbo.messages.outgoing.generic.alerts.BubbleAlertComposer;
import com.eu.habbo.messages.outgoing.generic.alerts.BubbleAlertKeys;
import com.eu.habbo.messages.outgoing.rooms.items.FloorItemUpdateComposer;
import com.eu.habbo.util.PacketGuard;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class RotateMoveItemEvent extends MessageHandler {
    private static final int MOVE_ENTRY_BYTES = Integer.BYTES * 4;

    @Override
    public void handle() throws Exception {
        Room room = currentRoom();
        if (room == null) return;

        int furniId = this.packet.readInt();
        int x = this.packet.readInt();
        int y = this.packet.readInt();
        int rotation = this.packet.readInt();

        if (!RoomItemInputGuard.isPositiveId(furniId) || !isValidMove(x, y, rotation)) return;

        boolean moveThroughFurniture = false;
        boolean moveThroughUsers = false;
        boolean maintainHeight = false;
        int optionBytes = this.packet.bytesAvailable();
        if (optionBytes != 0) {
            if (optionBytes < 3) return;

            moveThroughFurniture = this.packet.readBoolean();
            moveThroughUsers = this.packet.readBoolean();
            maintainHeight = this.packet.readBoolean();
        }

        List<MoveRequest> moves = new ArrayList<>();
        moves.add(new MoveRequest(furniId, x, y, rotation));

        int additionalMoveCount = 0;
        if (this.packet.bytesAvailable() > 0) {
            if (optionBytes < 7 || this.packet.bytesAvailable() < Integer.BYTES) return;

            additionalMoveCount = this.packet.readInt();
            if (additionalMoveCount < 1
                    || additionalMoveCount >= Room.MAXIMUM_FURNI
                    || !PacketGuard.hasFixedWidthEntries(this.packet, additionalMoveCount, MOVE_ENTRY_BYTES)
                    || this.packet.bytesAvailable() != (long) additionalMoveCount * MOVE_ENTRY_BYTES) {
                return;
            }

            Set<Integer> itemIds = new HashSet<>();
            itemIds.add(furniId);

            for (int i = 0; i < additionalMoveCount; i++) {
                int itemId = this.packet.readInt();
                int targetX = this.packet.readInt();
                int targetY = this.packet.readInt();
                int targetRotation = this.packet.readInt();

                if (!RoomItemInputGuard.isPositiveId(itemId)
                        || !itemIds.add(itemId)
                        || !isValidMove(targetX, targetY, targetRotation)) {
                    return;
                }

                moves.add(new MoveRequest(itemId, targetX, targetY, targetRotation));
            }
        }

        boolean groupedMove = additionalMoveCount > 0;
        FurnitureMovementError firstBatchError = null;
        List<ServerMessage> batchUpdates = groupedMove ? new ArrayList<>(moves.size()) : null;

        for (MoveRequest move : moves) {
            FurnitureMovementError error = moveItem(
                    room,
                    move,
                    moveThroughFurniture,
                    moveThroughUsers,
                    maintainHeight,
                    !groupedMove,
                    batchUpdates);

            if (groupedMove && error != null && error != FurnitureMovementError.NONE && firstBatchError == null) {
                firstBatchError = error;
            }
        }

        if (groupedMove && !batchUpdates.isEmpty()) {
            room.sendComposers(batchUpdates);
        }

        if (firstBatchError != null) {
            this.client.sendResponse(new BubbleAlertComposer(
                    BubbleAlertKeys.FURNITURE_PLACEMENT_ERROR.key, firstBatchError.errorCode));
        }
    }

    private FurnitureMovementError moveItem(
            Room room,
            MoveRequest move,
            boolean moveThroughFurniture,
            boolean moveThroughUsers,
            boolean maintainHeight,
            boolean sendIndividualError,
            List<ServerMessage> batchUpdates) {
        HabboItem item = room.getHabboItem(move.itemId);
        if (item == null) return null;

        RoomTile tile = room.getLayout().getTile((short) move.x, (short) move.y);
        if (tile == null) return FurnitureMovementError.INVALID_MOVE;

        RoomTile oldTile = room.getLayout().getTile(item.getX(), item.getY());
        int oldRotation = item.getRotation();

        FurnitureMovementError error = room.canPlaceFurnitureAt(item, this.client.getHabbo(), tile, move.rotation);
        if (error == FurnitureMovementError.NONE) {
            error = room.moveFurniTo(
                    item,
                    tile,
                    move.rotation,
                    this.client.getHabbo(),
                    batchUpdates == null,
                    !moveThroughUsers,
                    moveThroughFurniture,
                    maintainHeight);
        }

        if (error != FurnitureMovementError.NONE) {
            if (sendIndividualError) {
                this.client.sendResponse(new BubbleAlertComposer(
                        BubbleAlertKeys.FURNITURE_PLACEMENT_ERROR.key, error.errorCode));
                this.client.sendResponse(new FloorItemUpdateComposer(item));
            }
            return error;
        }

        if (oldTile != null && (oldTile.x != tile.x || oldTile.y != tile.y)) {
            DailyTaskActionMatcher.addProgress(this.client.getHabbo(), DailyTaskActionMatcher.MOVE_ITEM, 1);
        }
        if (oldRotation != item.getRotation()) {
            DailyTaskActionMatcher.addProgress(this.client.getHabbo(), DailyTaskActionMatcher.ROTATE_ITEM, 1);
        }

        if (batchUpdates != null) {
            batchUpdates.add(new FloorItemUpdateComposer(item).compose());
        }

        return FurnitureMovementError.NONE;
    }

    private static boolean isValidMove(int x, int y, int rotation) {
        return x >= Short.MIN_VALUE
                && x <= Short.MAX_VALUE
                && y >= Short.MIN_VALUE
                && y <= Short.MAX_VALUE
                && rotation >= 0
                && rotation < 8;
    }

    private static final class MoveRequest {
        private final int itemId;
        private final int x;
        private final int y;
        private final int rotation;

        private MoveRequest(int itemId, int x, int y, int rotation) {
            this.itemId = itemId;
            this.x = x;
            this.y = y;
            this.rotation = rotation;
        }
    }
}
