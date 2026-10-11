package com.eu.habbo.messages.incoming.rooms.items;

import com.eu.habbo.habbohotel.items.interactions.wired.chest.ChestStorage;
import com.eu.habbo.habbohotel.items.interactions.wired.chest.InteractionWiredChest;
import com.eu.habbo.habbohotel.rooms.Room;
import com.eu.habbo.habbohotel.users.Habbo;
import com.eu.habbo.habbohotel.users.HabboItem;
import com.eu.habbo.messages.incoming.MessageHandler;
import com.eu.habbo.messages.outgoing.rooms.items.ChestRoomLogComposer;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Handles room-level chest transactions log request (for :wired tab chests).
 * Wire format: {@code int amount, int page}.
 */
public class ChestRequestRoomLogsEvent extends MessageHandler {
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

        if (!room.canInspectWired(habbo)) return;

        int amount = Math.max(1, Math.min(50, this.packet.readInt()));
        int page = Math.max(1, this.packet.readInt());

        List<ChestRoomLogComposer.RoomLogItem> allLogs = new ArrayList<>();

        for (HabboItem item : room.getFloorItems()) {
            if (item instanceof InteractionWiredChest chest) {
                for (ChestStorage.LogEntry entry : chest.getContents().getLog()) {
                    ChestRoomLogComposer.RoomLogItem row = new ChestRoomLogComposer.RoomLogItem(
                            entry.type,
                            entry.timestamp,
                            entry.userName,
                            entry.withdrawn,
                            entry.deposited,
                            chest.getId());
                    row.furniTypeIds = entry.furniTypeIds;
                    row.currencyType = entry.currencyType;
                    row.furniQuantities = entry.furniQuantities;
                    allLogs.add(row);
                }
            }
        }

        allLogs.sort(Comparator.comparingLong((ChestRoomLogComposer.RoomLogItem i) -> i.timestamp)
                .reversed());

        int totalCount = allLogs.size();
        page = Math.min(page, Math.max(1, (int) Math.ceil(totalCount / (double) amount)));
        int fromIndex = (page - 1) * amount;
        List<ChestRoomLogComposer.RoomLogItem> pageRows = new ArrayList<>();

        if (fromIndex < totalCount) {
            int toIndex = Math.min(fromIndex + amount, totalCount);
            pageRows = allLogs.subList(fromIndex, toIndex);
        }

        this.client.sendResponse(new ChestRoomLogComposer(amount, page, totalCount, pageRows));
    }
}
