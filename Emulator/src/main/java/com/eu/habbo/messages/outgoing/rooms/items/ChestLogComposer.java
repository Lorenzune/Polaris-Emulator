package com.eu.habbo.messages.outgoing.rooms.items;

import com.eu.habbo.habbohotel.items.interactions.wired.chest.ChestStorage;
import com.eu.habbo.habbohotel.items.interactions.wired.chest.InteractionWiredChest;
import com.eu.habbo.messages.ServerMessage;
import com.eu.habbo.messages.outgoing.MessageComposer;
import com.eu.habbo.messages.outgoing.Outgoing;

import java.util.List;
import java.util.Map;

/**
 * Wired chest (Scrigno) transaction log, newest first. Wire layout:
 * {@code int itemId, int rowCount, [string type, int epochSeconds, string userName, int withdrawn, int deposited]*}.
 */
public class ChestLogComposer extends MessageComposer {
    private final InteractionWiredChest chest;

    public ChestLogComposer(InteractionWiredChest chest) {
        this.chest = chest;
    }

    public static void appendDetails(ServerMessage response, Integer currency, Map<Integer, Integer> quantities) {
        response.appendBoolean(currency != null);
        if (currency != null) response.appendInt(currency);
        response.appendInt(quantities == null ? 0 : quantities.size());
        if (quantities != null) for (var entry : quantities.entrySet()) { response.appendInt(entry.getKey()); response.appendInt(entry.getValue()); }
    }
    @Override
    protected ServerMessage composeInternal() {
        this.response.init(Outgoing.ChestLogComposer);
        this.response.appendInt(this.chest.getId());

        List<ChestStorage.LogEntry> log = this.chest.getContents().getLog();
        this.response.appendInt(log.size());

        for (ChestStorage.LogEntry e : log) {
            this.response.appendString(e.type == null ? "" : e.type);
            this.response.appendInt((int) (e.timestamp / 1000L));
            this.response.appendString(e.userName == null ? "" : e.userName);
            this.response.appendInt(e.withdrawn);
            this.response.appendInt(e.deposited);
        }

        // Optional tail keeps the established row prefix compatible with older clients.
        this.response.appendInt(log.size());
        for (ChestStorage.LogEntry e : log) {
            int[] ids = e.furniTypeIds;
            this.response.appendInt(ids == null ? 0 : ids.length);
            if (ids != null) for (int id : ids) this.response.appendInt(id);
        }
        this.response.appendInt(log.size());
        for (ChestStorage.LogEntry e : log) appendDetails(this.response, e.currencyType, e.furniQuantities);
        return this.response;
    }
}
