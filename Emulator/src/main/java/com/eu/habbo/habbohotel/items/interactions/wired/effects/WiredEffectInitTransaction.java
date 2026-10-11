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
import java.util.ArrayList;
import java.util.List;

/** Opens the selected contract with chest-backed terms. Payments commit only after acceptance and confirmation. */
public class WiredEffectInitTransaction extends InteractionWiredEffect {
    public static final WiredEffectType type = WiredEffectType.INIT_TRANSACTION;

    private final List<Integer> contractIds = new ArrayList<>();

    public WiredEffectInitTransaction(ResultSet set, Item baseItem) throws SQLException {
        super(set, baseItem);
    }

    public WiredEffectInitTransaction(
            int id, int userId, Item item, String extradata, int limitedStack, int limitedSells) {
        super(id, userId, item, extradata, limitedStack, limitedSells);
    }

    private int[] transactionParams = new int[] {0, 1, 0, -20, 0, 300};
    private String transactionConfig = "";

    @Override
    public void execute(WiredContext ctx) {
        com.eu.habbo.habbohotel.items.interactions.wired.chest.WiredContractTransactions.start(
                ctx, this, contractIds, transactionParams, transactionConfig);
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
        int[] p = settings.getIntParams();
        this.transactionParams = p.length >= 6 ? java.util.Arrays.copyOf(p, 6) : new int[] {0, 1, 0, -20, 0, 300};
        if (transactionParams[0] < 0 || transactionParams[0] > 2) return false;
        transactionParams[1] = Math.max(1, Math.min(500, transactionParams[1]));
        transactionParams[5] = Math.max(30, Math.min(3600, transactionParams[5]));
        this.transactionConfig = settings.getStringParam() == null ? "" : settings.getStringParam();
        this.contractIds.clear();
        if (settings.getFurniIds() != null) {
            for (int id : settings.getFurniIds()) this.contractIds.add(id);
        }
        this.setDelay(settings.getDelay());
        return true;
    }

    @Override
    public String getWiredData() {
        return WiredManager.getGson()
                .toJson(new JsonData(this.getDelay(), this.contractIds, transactionParams, transactionConfig));
    }

    @Override
    public void loadWiredData(ResultSet set, Room room) throws SQLException {
        this.onPickUp();

        String wiredData = set.getString("wired_data");
        if (wiredData == null || !wiredData.startsWith("{")) return;

        JsonData data = WiredManager.getGson().fromJson(wiredData, JsonData.class);
        if (data == null) return;

        this.setDelay(data.delay);
        if (data.params != null && data.params.length == 6) transactionParams = data.params;
        transactionConfig = data.config == null ? "" : data.config;
        if (data.contractIds != null) this.contractIds.addAll(data.contractIds);
    }

    @Override
    public void serializeWiredData(ServerMessage message, Room room) {
        message.appendBoolean(false);
        message.appendInt(WiredManager.MAXIMUM_FURNI_SELECTION);
        message.appendInt(this.contractIds.size());
        for (Integer id : this.contractIds) message.appendInt(id);
        message.appendInt(this.getBaseItem().getSpriteId());
        message.appendInt(this.getId());
        message.appendString(transactionConfig);
        message.appendInt(transactionParams.length);
        for (int value : transactionParams) message.appendInt(value);
        message.appendInt(0);
        message.appendInt(this.getType().code);
        message.appendInt(this.getDelay());
        message.appendInt(0);
    }

    @Override
    public void onPickUp() {
        this.contractIds.clear();
        transactionParams = new int[] {0, 1, 0, -20, 0, 300};
        transactionConfig = "";
        this.setDelay(0);
    }

    static class JsonData {
        int delay;
        List<Integer> contractIds;
        int[] params;
        String config;

        public JsonData(int delay, List<Integer> contractIds, int[] params, String config) {
            this.delay = delay;
            this.contractIds = contractIds;
            this.params = params;
            this.config = config;
        }
    }
}
