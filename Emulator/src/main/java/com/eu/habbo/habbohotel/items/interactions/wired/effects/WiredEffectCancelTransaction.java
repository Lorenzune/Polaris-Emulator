package com.eu.habbo.habbohotel.items.interactions.wired.effects;

import com.eu.habbo.habbohotel.gameclients.GameClient;
import com.eu.habbo.habbohotel.items.Item;
import com.eu.habbo.habbohotel.items.interactions.InteractionWiredEffect;
import com.eu.habbo.habbohotel.items.interactions.wired.WiredSettings;
import com.eu.habbo.habbohotel.rooms.Room;
import com.eu.habbo.habbohotel.rooms.RoomUnit;
import com.eu.habbo.habbohotel.wired.WiredEffectType;
import com.eu.habbo.habbohotel.wired.core.WiredContext;
import com.eu.habbo.habbohotel.wired.core.WiredManager;
import com.eu.habbo.messages.ServerMessage;
import com.eu.habbo.messages.incoming.wired.WiredSaveException;
import java.sql.ResultSet;
import java.sql.SQLException;

/** Cancels a pending payment or trade for the specified contract, or any pending transaction, without transferring assets. */
public class WiredEffectCancelTransaction extends InteractionWiredEffect {
    public static final WiredEffectType type = WiredEffectType.CANCEL_TRANSACTION;

    public WiredEffectCancelTransaction(ResultSet set, Item baseItem) throws SQLException {
        super(set, baseItem);
    }

    public WiredEffectCancelTransaction(
            int id, int userId, Item item, String extradata, int limitedStack, int limitedSells) {
        super(id, userId, item, extradata, limitedStack, limitedSells);
    }

    private int cancelMode;
    private com.eu.habbo.habbohotel.items.interactions.wired.chest.WiredTransactionSources sources =
            new com.eu.habbo.habbohotel.items.interactions.wired.chest.WiredTransactionSources();
    private java.util.List<Integer> contractIds = new java.util.ArrayList<>();

    @Override
    public void execute(WiredContext ctx) {
        com.eu.habbo.habbohotel.items.interactions.wired.chest.WiredContractTransactions.cancel(
                ctx, cancelMode, contractIds, sources);
    }

    @Deprecated
    @Override
    public boolean execute(RoomUnit roomUnit, Room room, Object[] stuff) {
        return false;
    }

    @Override
    public WiredEffectType getType() {
        return type;
    }

    @Override
    public boolean saveData(WiredSettings settings, GameClient gameClient) throws WiredSaveException {
        sources = com.eu.habbo.habbohotel.items.interactions.wired.chest.WiredTransactionSources.read(
                settings.getStringParam());
        this.cancelMode = settings.getIntParams().length > 0 && settings.getIntParams()[0] == 1 ? 1 : 0;
        contractIds = settings.getFurniIds() == null
                ? new java.util.ArrayList<>()
                : java.util.Arrays.stream(settings.getFurniIds())
                        .distinct()
                        .boxed()
                        .toList();
        this.setDelay(settings.getDelay());
        return true;
    }

    @Override
    public String getWiredData() {
        return WiredManager.getGson().toJson(new JsonData(this.getDelay(), cancelMode, contractIds, sources));
    }

    @Override
    public void loadWiredData(ResultSet set, Room room) throws SQLException {
        this.setDelay(0);
        String wiredData = set.getString("wired_data");
        if (wiredData != null && wiredData.startsWith("{")) {
            JsonData data = WiredManager.getGson().fromJson(wiredData, JsonData.class);
            if (data != null) {
                this.setDelay(data.delay);
                cancelMode = data.mode;
                contractIds = data.ids == null ? new java.util.ArrayList<>() : data.ids;
                sources = data.sources == null
                        ? new com.eu.habbo.habbohotel.items.interactions.wired.chest.WiredTransactionSources()
                        : com.eu.habbo.habbohotel.items.interactions.wired.chest.WiredTransactionSources.read(
                                WiredManager.getGson().toJson(data.sources));
            }
        }
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
        message.appendInt(1);
        message.appendInt(cancelMode);
        message.appendInt(0);
        message.appendInt(this.getType().code);
        message.appendInt(this.getDelay());
        message.appendInt(0);
    }

    @Override
    public void onPickUp() {
        this.setDelay(0);
        sources = new com.eu.habbo.habbohotel.items.interactions.wired.chest.WiredTransactionSources();
        cancelMode = 0;
        contractIds = new java.util.ArrayList<>();
    }

    static class JsonData {
        int delay, mode;
        java.util.List<Integer> ids;
        com.eu.habbo.habbohotel.items.interactions.wired.chest.WiredTransactionSources sources;

        public JsonData(
                int delay,
                int mode,
                java.util.List<Integer> ids,
                com.eu.habbo.habbohotel.items.interactions.wired.chest.WiredTransactionSources sources) {
            this.sources = sources;
            this.delay = delay;
            this.mode = mode;
            this.ids = ids;
        }
    }
}
