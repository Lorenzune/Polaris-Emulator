package com.eu.habbo.messages.incoming.wired;

import com.eu.habbo.habbohotel.items.FurnitureType;
import com.eu.habbo.habbohotel.rooms.Room;
import com.eu.habbo.habbohotel.users.HabboItem;
import com.eu.habbo.messages.incoming.MessageHandler;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** Applies one room-wide opacity or scale value to a selection of floor furniture. */
public class WiredFurniOpacityBatchEvent extends MessageHandler {
    private static final int MAXIMUM_ITEMS = 1_000;
    private static final int FIXED_PACKET_BYTES = 8;

    @Override
    public void handle() throws Exception {
        Room room = currentRoom();
        if (room == null
                || !room.canModifyWired(this.client.getHabbo())
                || this.packet.bytesAvailable() < FIXED_PACKET_BYTES) {
            return;
        }

        int itemCount = this.packet.readInt();
        if (itemCount <= 0 || itemCount > MAXIMUM_ITEMS || this.packet.bytesAvailable() < itemCount * 4 + 4) {
            return;
        }

        Set<Integer> itemIds = new HashSet<>();
        for (int index = 0; index < itemCount; index++) {
            int itemId = this.packet.readInt();
            if (itemId > 0) {
                itemIds.add(itemId);
            }
        }

        int value = this.packet.readInt();
        boolean scalingUpdate = value < 0;
        int normalizedValue = scalingUpdate ? -value : value;
        if (itemIds.isEmpty()
                || (scalingUpdate && (normalizedValue < 25 || normalizedValue > 300))
                || (!scalingUpdate && normalizedValue > 100)) {
            return;
        }

        List<HabboItem> items = new ArrayList<>(itemIds.size());
        for (int itemId : itemIds) {
            HabboItem item = room.getHabboItem(itemId);
            if (item != null && item.getBaseItem() != null && item.getBaseItem().getType() == FurnitureType.FLOOR) {
                items.add(item);
            }
        }

        if (scalingUpdate) {
            room.getWiredRuntime().setGlobalScalingForItems(room, items, normalizedValue);
        } else {
            room.getWiredRuntime().setGlobalOpacityForItems(room, items, normalizedValue);
        }
    }

    @Override
    public int getRatelimit() {
        return 100;
    }
}
