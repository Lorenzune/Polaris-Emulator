package com.eu.habbo.habbohotel.items.interactions.wired.triggers;

import com.eu.habbo.habbohotel.items.Item;
import com.eu.habbo.habbohotel.items.interactions.InteractionWiredTrigger;
import com.eu.habbo.habbohotel.items.interactions.wired.WiredSettings;
import com.eu.habbo.habbohotel.rooms.Room;
import com.eu.habbo.habbohotel.rooms.RoomUnit;
import com.eu.habbo.habbohotel.users.HabboItem;
import com.eu.habbo.habbohotel.wired.WiredTriggerType;
import com.eu.habbo.habbohotel.wired.core.WiredEvent;
import com.eu.habbo.habbohotel.wired.core.WiredManager;
import com.eu.habbo.messages.ServerMessage;
import java.sql.ResultSet;
import java.sql.SQLException;

/** Matches successful transactions for selected contracts. */
public class WiredTriggerTransactionComplete extends InteractionWiredTrigger {
    private com.eu.habbo.habbohotel.items.interactions.wired.chest.WiredTransactionSources sources =
            new com.eu.habbo.habbohotel.items.interactions.wired.chest.WiredTransactionSources();
    private java.util.List<Integer> contractIds = new java.util.ArrayList<>();
    public static final WiredTriggerType type = WiredTriggerType.TRANSACTION_COMPLETE;

    public WiredTriggerTransactionComplete(ResultSet set, Item baseItem) throws SQLException {
        super(set, baseItem);
    }

    public WiredTriggerTransactionComplete(
            int id, int userId, Item item, String extradata, int limitedStack, int limitedSells) {
        super(id, userId, item, extradata, limitedStack, limitedSells);
    }

    @Override
    public boolean matches(HabboItem triggerItem, WiredEvent event) {
        return sources.matches(triggerItem, event, contractIds);
    }

    @Deprecated
    @Override
    public boolean execute(RoomUnit roomUnit, Room room, Object[] stuff) {
        return false;
    }

    @Override
    public WiredTriggerType getType() {
        return type;
    }

    @Override
    public void serializeWiredData(ServerMessage message, Room room) {
        message.appendBoolean(false);
        message.appendInt(WiredManager.MAXIMUM_FURNI_SELECTION);
        message.appendInt(contractIds.size());
        for (int id : contractIds) message.appendInt(id);
        message.appendInt(this.getBaseItem().getSpriteId());
        message.appendInt(this.getId());
        message.appendString(sources.json());
        message.appendInt(0);
        message.appendInt(0);
        message.appendInt(type.code);
        message.appendInt(0);
        message.appendInt(0);
    }

    @Override
    public boolean saveData(WiredSettings settings) {
        sources = com.eu.habbo.habbohotel.items.interactions.wired.chest.WiredTransactionSources.read(
                settings.getStringParam());
        contractIds = settings.getFurniIds() == null
                ? new java.util.ArrayList<>()
                : java.util.Arrays.stream(settings.getFurniIds())
                        .distinct()
                        .boxed()
                        .toList();
        return true;
    }

    @Override
    public String getWiredData() {
        return WiredManager.getGson().toJson(new JsonData(contractIds, sources));
    }

    @Override
    public void loadWiredData(ResultSet set, Room room) throws SQLException {
        onPickUp();
        String value = set.getString("wired_data");
        if (value != null && value.startsWith("{")) {
            JsonData data = WiredManager.getGson().fromJson(value, JsonData.class);
            if (data != null) {
                contractIds = data.ids == null ? new java.util.ArrayList<>() : data.ids;
                sources = data.sources == null
                        ? new com.eu.habbo.habbohotel.items.interactions.wired.chest.WiredTransactionSources()
                        : com.eu.habbo.habbohotel.items.interactions.wired.chest.WiredTransactionSources.read(
                                WiredManager.getGson().toJson(data.sources));
            }
        } else if (value != null && value.startsWith("[")) {
            int[] ids = WiredManager.getGson().fromJson(value, int[].class);
            if (ids != null)
                contractIds = java.util.Arrays.stream(ids).distinct().boxed().toList();
        }
    }

    @Override
    public void onPickUp() {
        sources = new com.eu.habbo.habbohotel.items.interactions.wired.chest.WiredTransactionSources();
        contractIds = new java.util.ArrayList<>();
    }

    @Override
    public boolean isTriggeredByRoomUnit() {
        return false;
    }

    private record JsonData(
            java.util.List<Integer> ids,
            com.eu.habbo.habbohotel.items.interactions.wired.chest.WiredTransactionSources sources) {}
}
