package com.eu.habbo.habbohotel.items.interactions.wired.conditions;

import com.eu.habbo.habbohotel.items.Item;
import com.eu.habbo.habbohotel.items.interactions.InteractionWiredCondition;
import com.eu.habbo.habbohotel.items.interactions.wired.WiredComparison;
import com.eu.habbo.habbohotel.items.interactions.wired.WiredSettings;
import com.eu.habbo.habbohotel.items.interactions.wired.chest.ChestStorage;
import com.eu.habbo.habbohotel.items.interactions.wired.chest.WiredChestConfiguration;
import com.eu.habbo.habbohotel.items.interactions.wired.chest.InteractionWiredChest;
import com.eu.habbo.habbohotel.rooms.Room;
import com.eu.habbo.habbohotel.rooms.RoomUnit;
import com.eu.habbo.habbohotel.users.HabboItem;
import com.eu.habbo.habbohotel.wired.WiredConditionType;
import com.eu.habbo.habbohotel.wired.core.WiredContext;
import com.eu.habbo.habbohotel.wired.core.WiredManager;
import com.eu.habbo.messages.ServerMessage;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

/**
 * Chest Has X Items (furni classname {@code wf_cnd_chest_has_items}). Passes when the total contents
 * (currency + furni) of the selected {@link InteractionWiredChest}(s) is at least {@code amount}.
 */
public class WiredConditionChestHasItems extends InteractionWiredCondition {
    public static final WiredConditionType type = WiredConditionType.CHEST_HAS_ITEMS;

    private WiredChestConfiguration configuration;
    private final List<Integer> chestIds = new ArrayList<>();
    private int amount = 1;
    private int comparison = WiredComparison.GREATER_EQUAL;

    public WiredConditionChestHasItems(ResultSet set, Item baseItem) throws SQLException {
        super(set, baseItem);
    }

    public WiredConditionChestHasItems(int id, int userId, Item item, String extradata, int limitedStack, int limitedSells) {
        super(id, userId, item, extradata, limitedStack, limitedSells);
    }

    @Override
    public boolean evaluate(WiredContext ctx) {
        Room room = ctx.room();
        if (room == null) return false;

        WiredChestConfiguration config = this.configuration == null ? new WiredChestConfiguration() : this.configuration;
        Integer expected = config.amount(ctx, this.amount);
        if (expected == null) return false;
        if (this.configuration != null && config.conditionQuantifier != null) {
            var candidates = config.items(ctx, this.chestIds, config.chestSource);
            if (candidates.isEmpty()) return false;
            boolean any = config.conditionQuantifier == 1;
            for (HabboItem item : candidates) {
                long value = item instanceof InteractionWiredChest chest
                    ? chest.getContents().furniItemCount() + chest.getContents().entries().stream().filter(entry -> entry.kind == ChestStorage.KIND_CURRENCY).mapToLong(entry -> Math.max(0, entry.quantity)).sum() : -1;
                boolean match = value >= 0 && WiredComparison.compare((int)Math.min(Integer.MAX_VALUE, value), expected, this.comparison);
                if (any && match) return true;
                if (!any && !match) return false;
            }
            return !any;
        }
        long total = 0;
        for (HabboItem item : config.items(ctx, this.chestIds, config.chestSource)) {
            if (item instanceof InteractionWiredChest chest) {
                for (var entry : chest.getContents().entries()) if (entry.kind == ChestStorage.KIND_CURRENCY) total += Math.max(0, entry.quantity);
                total += chest.getContents().furniItemCount();
            }
        }
        return WiredComparison.compare((int)Math.min(Integer.MAX_VALUE, total), expected, this.comparison);
    }

    @Deprecated
    @Override
    public boolean execute(RoomUnit roomUnit, Room room, Object[] stuff) {
        return false;
    }

    @Override
    public boolean saveData(WiredSettings settings) {
        int[] params = settings.getIntParams();
        this.configuration = WiredChestConfiguration.read(settings);
        this.amount = (params.length > 0) ? Math.max(0, params[0]) : 1;
        this.comparison = (params.length > 1) ? WiredComparison.normalize(params[this.configuration != null && params.length >= 4 ? 3 : 1]) : WiredComparison.GREATER_EQUAL;

        this.chestIds.clear();
        if (settings.getFurniIds() != null) {
            for (int id : settings.getFurniIds()) {
                this.chestIds.add(id);
            }
        }
        return true;
    }

    @Override
    public WiredConditionType getType() {
        return type;
    }

    @Override
    public String getWiredData() {
        return WiredManager.getGson().toJson(new JsonData(this.amount, this.comparison, this.chestIds));
    }

    @Override
    public void loadWiredData(ResultSet set, Room room) throws SQLException {
        this.onPickUp();

        String wiredData = set.getString("wired_data");
        if (wiredData == null || !wiredData.startsWith("{")) return;

        JsonData data = WiredManager.getGson().fromJson(wiredData, JsonData.class);
        if (data == null) return;
        this.configuration = data.configuration;

        this.amount = Math.max(0, data.amount);
        this.comparison = WiredComparison.normalize(data.comparison);
        if (data.chestIds != null) {
            this.chestIds.addAll(data.chestIds);
        }
    }

    @Override
    public void serializeWiredData(ServerMessage message, Room room) {
        message.appendBoolean(false);
        message.appendInt(WiredManager.MAXIMUM_FURNI_SELECTION);
        message.appendInt(this.chestIds.size());
        for (Integer id : this.chestIds) {
            message.appendInt(id);
        }
        message.appendInt(this.getBaseItem().getSpriteId());
        message.appendInt(this.getId());
        message.appendString(this.configuration == null ? "" : WiredManager.getGson().toJson(this.configuration));
        if (this.configuration != null) {
            message.appendInt(4);
            message.appendInt(this.amount);
            message.appendInt(this.configuration.amountOption);
            message.appendInt(this.configuration.amountTarget);
            message.appendInt(this.comparison);
        } else {
        message.appendInt(2);
        message.appendInt(this.amount);
        message.appendInt(this.comparison);
        }
        message.appendInt(0);
        message.appendInt(this.getType().code);
        if (this.configuration == null) { message.appendInt(0); message.appendInt(0); }
        if (this.configuration != null) this.configuration.appendSettings(message, false, null);
    }

    @Override
    public void onPickUp() {
        this.configuration = null;
        this.chestIds.clear();
        this.amount = 1;
        this.comparison = WiredComparison.GREATER_EQUAL;
    }

    class JsonData {
        WiredChestConfiguration configuration;
        int amount;
        int comparison;
        List<Integer> chestIds;

        public JsonData(int amount, int comparison, List<Integer> chestIds) {
            this.configuration = WiredConditionChestHasItems.this.configuration;
            this.amount = amount;
            this.comparison = comparison;
            this.chestIds = chestIds;
        }
    }
}
