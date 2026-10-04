package com.eu.habbo.habbohotel.items.interactions.wired.effects;

import com.eu.habbo.habbohotel.gameclients.GameClient;
import com.eu.habbo.habbohotel.items.Item;
import com.eu.habbo.habbohotel.items.interactions.InteractionWiredEffect;
import com.eu.habbo.habbohotel.items.interactions.wired.WiredSettings;
import com.eu.habbo.habbohotel.items.interactions.wired.chest.ChestStorage;
import com.eu.habbo.habbohotel.items.interactions.wired.chest.WiredChestConfiguration;
import com.eu.habbo.habbohotel.rooms.Room;
import com.eu.habbo.habbohotel.rooms.RoomUnit;
import com.eu.habbo.habbohotel.users.Habbo;
import com.eu.habbo.habbohotel.users.HabboItem;
import com.eu.habbo.habbohotel.wired.WiredEffectType;
import com.eu.habbo.habbohotel.wired.core.WiredContext;
import com.eu.habbo.habbohotel.wired.core.WiredManager;
import com.eu.habbo.habbohotel.wired.core.WiredSourceUtil;
import com.eu.habbo.messages.ServerMessage;
import com.eu.habbo.messages.incoming.wired.WiredSaveException;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

/**
 * Give Currency From Chest (furni classname {@code wf_act_give_currency}). Dispenses up to
 * {@code amount} currency from a selected {@link InteractionWiredChest} to the resolved user(s),
 * decrementing and persisting the chest pool. If the chest is empty nothing is given. Safe: it only
 * grants what the chest actually holds (no minting beyond the configured pool).
 */
public class WiredEffectGiveCurrencyFromChest extends InteractionWiredEffect {
    public static final WiredEffectType type = WiredEffectType.GIVE_CURRENCY_FROM_CHEST;

    private WiredChestConfiguration configuration;
    private final List<Integer> chestIds = new ArrayList<>();
    private int amount = 0;
    private int userSource = WiredSourceUtil.SOURCE_TRIGGER;

    public WiredEffectGiveCurrencyFromChest(ResultSet set, Item baseItem) throws SQLException {
        super(set, baseItem);
    }

    public WiredEffectGiveCurrencyFromChest(int id, int userId, Item item, String extradata, int limitedStack, int limitedSells) {
        super(id, userId, item, extradata, limitedStack, limitedSells);
    }

    @Override
    public void execute(WiredContext ctx) {
        Room room = ctx.room();
        if (room == null) return;
        WiredChestConfiguration config = this.configuration == null ? new WiredChestConfiguration() : this.configuration;
        Integer requested = config.amount(ctx, this.amount);
        if (config.rewardingMode != 1 && (requested == null || requested <= 0)) return;
        for (RoomUnit unit : WiredSourceUtil.resolveUsers(ctx, this.userSource)) {
            Habbo habbo = room.getHabbo(unit);
            if (habbo == null || habbo.getClient() == null) continue;
            int remaining = config.rewardingMode == 1 ? Integer.MAX_VALUE : requested;
            int totalGiven = 0;
            for (HabboItem item : config.items(ctx, this.chestIds, config.chestSource)) {
                if (!(item instanceof com.eu.habbo.habbohotel.items.interactions.wired.chest.InteractionWiredChestCurrency chest) || chest.getContents().isLocked()) continue;
                ChestStorage contents = chest.getContents();
                int currency = config.currencyType;
                if (this.configuration == null) {
                    var first = contents.entries().stream().filter(e -> e.kind == ChestStorage.KIND_CURRENCY && e.quantity > 0).findFirst();
                    if (first.isEmpty()) continue;
                    currency = first.get().type;
                }
                int given = contents.take(ChestStorage.KIND_CURRENCY, currency, remaining);
                if (given > 0) {
                    grant(habbo, currency, given);
                    contents.addLog(new ChestStorage.LogEntry("wired_withdraw", System.currentTimeMillis(), habbo.getHabboInfo().getUsername(), given, 0).withCurrency(currency));
                    chest.persistContents(room);
                }
                remaining -= given;
                totalGiven = (int)Math.min(Integer.MAX_VALUE, (long)totalGiven + given);
                if (remaining <= 0) break;
            }
            config.notifyReward(habbo, totalGiven);
        }
    }

    private static void grant(Habbo habbo, int currencyType, int amount) {
        if (currencyType < 0) {
            habbo.giveCredits(amount, "wired.reward");
        } else {
            habbo.givePoints(currencyType, amount, "wired.reward");
        }
    }

    @Deprecated
    @Override
    public boolean execute(RoomUnit roomUnit, Room room, Object[] stuff) {
        return false;
    }

    @Override
    public boolean saveData(WiredSettings settings, GameClient gameClient) throws WiredSaveException {
        int[] params = settings.getIntParams();
        this.configuration = WiredChestConfiguration.read(settings);
        if (params.length < 2) throw new WiredSaveException("invalid data");

        this.amount = Math.max(0, params[this.configuration != null && params.length >= 6 ? 1 : 0]);
        this.userSource = this.configuration != null ? (settings.getUserSourceTypes() != null && settings.getUserSourceTypes().length > 0 ? settings.getUserSourceTypes()[0] : params.length >= 7 ? params[6] : 0) : params[1];
        if (this.configuration != null && params.length >= 6) this.configuration.earningsCategory = params[5] == 13 ? 13 : 11;

        this.chestIds.clear();
        if (settings.getFurniIds() != null) {
            for (int id : settings.getFurniIds()) {
                this.chestIds.add(id);
            }
        }

        this.setDelay(settings.getDelay());
        return true;
    }

    @Override
    public WiredEffectType getType() {
        return type;
    }

    @Override
    public String getWiredData() {
        return WiredManager.getGson().toJson(new JsonData(this.amount, this.userSource, this.getDelay(), this.chestIds));
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
        this.userSource = data.userSource;
        this.setDelay(data.delay);
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
            message.appendInt(7);
            message.appendInt(this.configuration.rewardingMode);
            message.appendInt(this.amount);
            message.appendInt(this.configuration.amountOption);
            message.appendInt(this.configuration.amountTarget);
            message.appendInt(this.configuration.showByDefault ? 1 : 0);
            message.appendInt(this.configuration.earningsCategory);
            message.appendInt(this.userSource);
        } else {
        message.appendInt(2);
        message.appendInt(this.amount);
        message.appendInt(this.userSource);
        }
        message.appendInt(0);
        message.appendInt(this.getType().code);
        message.appendInt(this.getDelay());
        message.appendInt(0);
        if (this.configuration != null) this.configuration.appendSettings(message, false, this.userSource);
    }

    @Override
    public void onPickUp() {
        this.configuration = null;
        this.chestIds.clear();
        this.amount = 0;
        this.userSource = WiredSourceUtil.SOURCE_TRIGGER;
        this.setDelay(0);
    }

    @Override
    public boolean requiresTriggeringUser() {
        return this.userSource == WiredSourceUtil.SOURCE_TRIGGER;
    }

    class JsonData {
        WiredChestConfiguration configuration;
        int amount;
        int userSource;
        int delay;
        List<Integer> chestIds;

        public JsonData(int amount, int userSource, int delay, List<Integer> chestIds) {
            this.configuration = WiredEffectGiveCurrencyFromChest.this.configuration;
            this.amount = amount;
            this.userSource = userSource;
            this.delay = delay;
            this.chestIds = chestIds;
        }
    }
}
