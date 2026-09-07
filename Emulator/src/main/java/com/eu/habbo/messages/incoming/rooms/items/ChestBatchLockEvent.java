package com.eu.habbo.messages.incoming.rooms.items;

import com.eu.habbo.habbohotel.items.interactions.wired.chest.ChestStorage;
import com.eu.habbo.habbohotel.items.interactions.wired.chest.InteractionWiredChest;
import com.eu.habbo.habbohotel.rooms.Room;
import com.eu.habbo.habbohotel.users.Habbo;
import com.eu.habbo.habbohotel.users.HabboItem;
import com.eu.habbo.messages.incoming.MessageHandler;

/**
 * Batch locks or unlocks chests in a room (from :wired tab chests).
 * Wire format: {@code boolean lock, boolean allChests}.
 */
public class ChestBatchLockEvent extends MessageHandler {
    @Override
    public int getRatelimit() {
        return 500;
    }

    @Override
    public void handle() throws Exception {
        Habbo habbo = this.client.getHabbo();
        if (habbo == null) return;

        Room room = habbo.getHabboInfo().getCurrentRoom();
        if (room == null) return;

        boolean lock = this.packet.readBoolean();
        boolean allChests = this.packet.readBoolean();

        if (!room.hasRights(habbo)) return;

        boolean isRoomOwnerOrStaff = habbo.hasPermission("acc_anyroomowner") || room.getOwnerId() == habbo.getHabboInfo().getId();
        if (allChests && !isRoomOwnerOrStaff) {
            allChests = false;
        }

        int userId = habbo.getHabboInfo().getId();

        for (HabboItem item : room.getFloorItems()) {
            if (item instanceof InteractionWiredChest chest) {
                if (allChests || chest.getUserId() == userId) {
                    ChestStorage contents = chest.getContents();
                    contents.setLocked(lock);
                    chest.persistContents();
                }
            }
        }
    }
}
