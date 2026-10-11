package com.eu.habbo.habbohotel.items.interactions.wired.extra;

import com.eu.habbo.habbohotel.gameclients.GameClient;
import com.eu.habbo.habbohotel.items.Item;
import com.eu.habbo.habbohotel.items.interactions.InteractionWiredExtra;
import com.eu.habbo.habbohotel.items.interactions.wired.WiredSettings;
import com.eu.habbo.habbohotel.items.interactions.wired.chest.ChestFurniStoredItem;
import com.eu.habbo.habbohotel.items.interactions.wired.chest.InteractionWiredChestFurni;
import com.eu.habbo.habbohotel.items.interactions.wired.chest.WiredChestConfiguration;
import com.eu.habbo.habbohotel.rooms.Room;
import com.eu.habbo.habbohotel.rooms.RoomUnit;
import com.eu.habbo.habbohotel.wired.core.WiredContext;
import com.eu.habbo.habbohotel.wired.core.WiredContextVariableSupport;
import com.eu.habbo.habbohotel.wired.core.WiredManager;
import com.eu.habbo.messages.ServerMessage;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Set;

/** Native chest scanner add-on. Runs before conditions and never changes selector targets. */
public class WiredExtraScanChestFurniByType extends InteractionWiredExtra {
    public static final int CODE = 103;
    private List<Integer> chestIds = new ArrayList<>();
    private WiredChestConfiguration configuration = new WiredChestConfiguration();

    public WiredExtraScanChestFurniByType(ResultSet set, Item item) throws SQLException {
        super(set, item);
    }

    public WiredExtraScanChestFurniByType(int id, int userId, Item item, String data, int stack, int sells) {
        super(id, userId, item, data, stack, sells);
    }

    public void scan(WiredContext ctx) {
        if (configuration.variableId <= 0
                || !WiredContextVariableSupport.hasDefinition(ctx.room(), configuration.variableId)) return;
        Set<Integer> types = configuration.itemTypes(ctx);
        long count = 0;
        for (var item : configuration.items(ctx, chestIds, configuration.chestSource)) {
            if (!(item instanceof InteractionWiredChestFurni chest)) continue;
            List<ChestFurniStoredItem> stored = chest.getContents().furniItems();
            Set<String> preview = configuration.scanningMode == 1 ? chest.previewKeys() : Set.of();
            for (var row : stored)
                if (types.contains(row.baseItemId)
                        && (configuration.scanningMode == 0
                                || preview.contains(InteractionWiredChestFurni.previewKey(row)))) count++;
        }
        WiredContextVariableSupport.assignVariable(
                ctx, ctx.room(), configuration.variableId, (int) Math.min(Integer.MAX_VALUE, count), true);
    }

    @Override
    public boolean execute(RoomUnit unit, Room room, Object[] stuff) {
        return false;
    }

    @Override
    public boolean saveData(WiredSettings settings, GameClient client) {
        var config = WiredChestConfiguration.read(settings);
        if (config == null || config.variableId <= 0) return false;
        configuration = config;
        chestIds = settings.getFurniIds() == null
                ? new ArrayList<>()
                : Arrays.stream(settings.getFurniIds())
                        .distinct()
                        .limit(WiredManager.MAXIMUM_FURNI_SELECTION)
                        .boxed()
                        .toList();
        return true;
    }

    @Override
    public String getWiredData() {
        return WiredManager.getGson().toJson(new Data(chestIds, configuration));
    }

    @Override
    public void loadWiredData(ResultSet set, Room room) throws SQLException {
        onPickUp();
        String json = set.getString("wired_data");
        if (json == null || !json.startsWith("{")) return;
        Data data = WiredExtraPayloadGuard.fromJson(json, Data.class);
        if (data == null) return;
        if (data.chestIds != null) chestIds = data.chestIds;
        if (data.configuration != null) configuration = data.configuration;
    }

    @Override
    public void serializeWiredData(ServerMessage message, Room room) {
        message.appendBoolean(false);
        message.appendInt(WiredManager.MAXIMUM_FURNI_SELECTION);
        message.appendInt(chestIds.size());
        for (int id : chestIds) message.appendInt(id);
        message.appendInt(getBaseItem().getSpriteId());
        message.appendInt(getId());
        message.appendString(WiredManager.getGson().toJson(configuration));
        message.appendInt(1);
        message.appendInt(configuration.scanningMode);
        message.appendInt(0);
        message.appendInt(CODE);
        message.appendInt(0);
        message.appendInt(0);
        configuration.appendSettings(message, true, null);
    }

    @Override
    public void onPickUp() {
        chestIds = new ArrayList<>();
        configuration = new WiredChestConfiguration();
    }

    private record Data(List<Integer> chestIds, WiredChestConfiguration configuration) {}
}
