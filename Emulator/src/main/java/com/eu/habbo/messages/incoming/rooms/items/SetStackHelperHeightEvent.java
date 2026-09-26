package com.eu.habbo.messages.incoming.rooms.items;

import com.eu.habbo.Emulator;
import com.eu.habbo.habbohotel.items.FurnitureType;
import com.eu.habbo.habbohotel.items.interactions.InteractionStackHelper;
import com.eu.habbo.habbohotel.items.interactions.InteractionStackWalkHelper;
import com.eu.habbo.habbohotel.items.interactions.InteractionTileWalkMagic;
import com.eu.habbo.habbohotel.rooms.Room;
import com.eu.habbo.habbohotel.rooms.RoomTile;
import com.eu.habbo.habbohotel.users.HabboItem;
import com.eu.habbo.messages.incoming.MessageHandler;
import com.eu.habbo.messages.outgoing.rooms.UpdateStackHeightComposer;
import com.eu.habbo.messages.outgoing.rooms.items.UpdateStackHeightTileHeightComposer;
import com.eu.habbo.util.PacketGuard;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class SetStackHelperHeightEvent extends MessageHandler {
    private static final int HEIGHT_ENTRY_BYTES = Integer.BYTES * 2;

    @Override
    public void handle() throws Exception {
        int itemId = this.packet.readInt();
        int requestedHeight = this.packet.readInt();
        Room room = this.client.getHabbo().getHabboInfo().getCurrentRoom();

        if (room == null || !RoomItemInputGuard.isPositiveId(itemId)) return;

        if (room.getOwnerId() != this.client.getHabbo().getHabboInfo().getId()
                && !room.hasRights(this.client.getHabbo())) return;

        HabboItem firstItem = room.getHabboItem(itemId);
        if (firstItem == null) return;

        List<HeightRequest> requests = new ArrayList<>();
        requests.add(new HeightRequest(itemId, requestedHeight));
        Boolean multiWalkEnabled = null;
        int remainingBytes = this.packet.bytesAvailable();

        if (remainingBytes == 1 && firstItem instanceof InteractionStackWalkHelper) {
            multiWalkEnabled = this.packet.readBoolean();
        } else if (remainingBytes > 0) {
            if (remainingBytes < Integer.BYTES) return;

            int additionalCount = this.packet.readInt();
            if (additionalCount < 1
                    || additionalCount >= Room.MAXIMUM_FURNI
                    || !PacketGuard.hasFixedWidthEntries(this.packet, additionalCount, HEIGHT_ENTRY_BYTES)
                    || this.packet.bytesAvailable() != (long) additionalCount * HEIGHT_ENTRY_BYTES) return;

            Set<Integer> itemIds = new HashSet<>();
            itemIds.add(itemId);

            for (int i = 0; i < additionalCount; i++) {
                int additionalItemId = this.packet.readInt();
                int additionalHeight = this.packet.readInt();
                if (!RoomItemInputGuard.isPositiveId(additionalItemId) || !itemIds.add(additionalItemId)) return;
                requests.add(new HeightRequest(additionalItemId, additionalHeight));
            }
        }

        List<HabboItem> changedItems = new ArrayList<>(requests.size());
        Set<RoomTile> affectedTiles = new HashSet<>();

        for (HeightRequest request : requests) {
            HabboItem item = room.getHabboItem(request.itemId);
            if (item == null || item.getBaseItem() == null || item.getBaseItem().getType() != FurnitureType.FLOOR)
                continue;

            boolean isStackHelper = item instanceof InteractionStackHelper
                    || item instanceof InteractionTileWalkMagic
                    || item instanceof InteractionStackWalkHelper;
            if (request.height < 0 && !(request.height == -100 && isStackHelper)) continue;

            RoomTile itemTile = room.getLayout().getTile(item.getX(), item.getY());
            if (itemTile == null) continue;

            Set<RoomTile> tiles = room.getLayout()
                    .getTilesAt(
                            itemTile,
                            item.getBaseItem().getWidth(),
                            item.getBaseItem().getLength(),
                            item.getRotation());
            if (tiles == null || tiles.isEmpty()) continue;

            int targetHeight = request.height;
            if (targetHeight == -100) {
                for (RoomTile tile : tiles) {
                    targetHeight = Math.max(
                            targetHeight, (int) Math.round(room.getStackHeight(tile.x, tile.y, false, item) * 100));
                }
            } else {
                int minimumHeight = isStackHelper
                        ? itemTile.z * 100
                        : (int) tiles.stream().mapToDouble(tile -> tile.z).max().orElse(itemTile.z) * 100;
                targetHeight = Math.min(Math.max(targetHeight, minimumHeight), (int) (Room.MAXIMUM_FURNI_HEIGHT * 100));
            }

            double height = Math.max(0, targetHeight) / 100.0D;
            item.setZ(height);
            if (item instanceof InteractionStackWalkHelper walkHelper) {
                if (requests.size() == 1 && multiWalkEnabled != null) walkHelper.setMultiWalkEnabled(multiWalkEnabled);
            } else if (isStackHelper) {
                item.setExtradata((int) (height * 100) + "");
            }

            item.needsUpdate(true);
            Emulator.getThreading().runHabboItemPersistence(item);
            changedItems.add(item);
            affectedTiles.addAll(tiles);
        }

        if (changedItems.isEmpty()) return;

        for (HabboItem item : changedItems) {
            room.updateItem(item);
            room.sendComposer(new UpdateStackHeightTileHeightComposer(item, (int) (item.getZ() * 100)).compose());
        }

        for (RoomTile tile : affectedTiles) {
            room.updateHabbosAt(tile.x, tile.y);
            room.updateBotsAt(tile.x, tile.y);
            room.updatePetsAt(tile.x, tile.y);
        }

        room.sendComposer(new UpdateStackHeightComposer(room, affectedTiles).compose());
    }

    private static final class HeightRequest {
        private final int itemId;
        private final int height;

        private HeightRequest(int itemId, int height) {
            this.itemId = itemId;
            this.height = height;
        }
    }
}
