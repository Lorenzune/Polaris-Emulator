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
 * Chest Contains Furni Type (furni classname {@code wf_cnd_chest_has_item_type}). Passes when the
 * selected {@link InteractionWiredChest}(s) hold at least {@code amount} furni of base type
 * {@code baseItemId}.
 */
public class WiredConditionChestHasItemType extends InteractionWiredCondition {
    public static final WiredConditionType type = WiredConditionType.CHEST_HAS_ITEM_TYPE;

    private WiredChestConfiguration configuration;
    private final List<Integer> chestIds = new ArrayList<>();
    private int baseItemId = 0;
    private int amount = 1;
    private int comparison = WiredComparison.GREATER_EQUAL;

    public WiredConditionChestHasItemType(ResultSet set, Item baseItem) throws SQLException {
        super(set, baseItem);
    }

    public WiredConditionChestHasItemType(int id, int userId, Item item, String extradata, int limitedStack, int limitedSells) {
        super(id, userId, item, extradata, limitedStack, limitedSells);
    }

    @Override
    public boolean evaluate(WiredContext ctx) {
        Room room = ctx.room();
        if (room == null) return false;
        WiredChestConfiguration config = this.configuration;
        java.util.Set<Integer> types = config == null ? java.util.Set.of(this.baseItemId) : config.itemTypes(ctx);
        if (types.isEmpty() || (config == null && this.baseItemId <= 0)) return false;
        Integer expected = config == null ? this.amount : config.amount(ctx, this.amount);
        if (expected == null) return false;
        if (config != null && config.conditionQuantifier != null) {
            var candidates = config.items(ctx, this.chestIds, config.chestSource);
            if (candidates.isEmpty()) return false;
            boolean any = config.conditionQuantifier == 1;
            for (HabboItem item : candidates) {
                boolean match = item instanceof InteractionWiredChest chest && WiredComparison.compare(
                    (int)chest.getContents().furniItems().stream().filter(row -> types.contains(row.baseItemId)).count(), expected, this.comparison);
                if (any && match) return true;
                if (!any && !match) return false;
            }
            return !any;
        }
        long total = 0;
        WiredChestConfiguration resolver = config == null ? new WiredChestConfiguration() : config;
        for (HabboItem item : resolver.items(ctx, this.chestIds, resolver.chestSource)) {
            if (item instanceof InteractionWiredChest chest) {
                for (var stored : chest.getContents().furniItems()) if (types.contains(stored.baseItemId)) total++;
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
        if (this.configuration != null && params.length >= 4) {
            this.baseItemId = 0;
            this.amount = Math.max(0, params[0]);
            this.comparison = WiredComparison.normalize(params[3]);
        } else {
            this.baseItemId = params.length > 0 ? params[0] : 0;
            this.amount = params.length > 1 ? Math.max(1, params[1]) : 1;
            this.comparison = params.length > 2 ? WiredComparison.normalize(params[2]) : WiredComparison.GREATER_EQUAL;
        }

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
        return WiredManager.getGson().toJson(new JsonData(this.baseItemId, this.amount, this.comparison, this.chestIds));
    }

    @Override
    public void loadWiredData(ResultSet set, Room room) throws SQLException {
        this.onPickUp();

        String wiredData = set.getString("wired_data");
        if (wiredData == null || !wiredData.startsWith("{")) return;

        JsonData data = WiredManager.getGson().fromJson(wiredData, JsonData.class);
        if (data == null) return;
        this.configuration = data.configuration;

        this.baseItemId = data.baseItemId;
        this.amount = Math.max(this.configuration == null ? 1 : 0, data.amount);
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
        message.appendInt(3);
        message.appendInt(this.baseItemId);
        message.appendInt(this.amount);
        message.appendInt(this.comparison);
        }
        message.appendInt(0);
        message.appendInt(this.getType().code);
        if (this.configuration == null) { message.appendInt(0); message.appendInt(0); }
        if (this.configuration != null) this.configuration.appendSettings(message, true, null);
    }

    @Override
    public void onPickUp() {
        this.configuration = null;
        this.chestIds.clear();
        this.baseItemId = 0;
        this.amount = 1;
        this.comparison = WiredComparison.GREATER_EQUAL;
    }

    class JsonData {
        WiredChestConfiguration configuration;
        int baseItemId;
        int amount;
        int comparison;
        List<Integer> chestIds;

        public JsonData(int baseItemId, int amount, int comparison, List<Integer> chestIds) {
            this.baseItemId = baseItemId;
            this.configuration = WiredConditionChestHasItemType.this.configuration;
            this.amount = amount;
            this.comparison = comparison;
            this.chestIds = chestIds;
        }
    }
}
