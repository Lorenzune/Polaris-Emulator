package com.eu.habbo.messages.outgoing.rooms.items;

import com.eu.habbo.messages.ServerMessage;
import com.eu.habbo.messages.outgoing.MessageComposer;
import com.eu.habbo.messages.outgoing.Outgoing;

import java.util.List;

public class ChestRoomLogComposer extends MessageComposer {
    public static class RoomLogItem {
        public String type;
        public long timestamp;
        public String userName;
        public int withdrawn;
        public int deposited;
        public int chestId;

        public RoomLogItem(String type, long timestamp, String userName, int withdrawn, int deposited, int chestId) {
            this.type = type;
            this.timestamp = timestamp;
            this.userName = userName;
            this.withdrawn = withdrawn;
            this.deposited = deposited;
            this.chestId = chestId;
        }
    }

    private final int amount;
    private final int page;
    private final int totalCount;
    private final List<RoomLogItem> rows;

    public ChestRoomLogComposer(int amount, int page, int totalCount, List<RoomLogItem> rows) {
        this.amount = amount;
        this.page = page;
        this.totalCount = totalCount;
        this.rows = rows;
    }

    @Override
    protected ServerMessage composeInternal() {
        this.response.init(Outgoing.ChestRoomLogComposer);
        this.response.appendInt(this.amount);
        this.response.appendInt(this.page);
        this.response.appendInt(this.totalCount);
        this.response.appendInt(this.rows.size());

        for (RoomLogItem e : this.rows) {
            this.response.appendString(e.type == null ? "" : e.type);
            this.response.appendInt((int) (e.timestamp / 1000L));
            this.response.appendString(e.userName == null ? "" : e.userName);
            this.response.appendInt(e.withdrawn);
            this.response.appendInt(e.deposited);
            this.response.appendInt(e.chestId);
        }

        return this.response;
    }
}
