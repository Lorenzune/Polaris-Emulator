package com.eu.habbo.habbohotel.items.interactions.wired.effects;

import com.eu.habbo.WiredPlatform;
import com.eu.habbo.habbohotel.gameclients.GameClient;
import com.eu.habbo.habbohotel.items.Item;
import com.eu.habbo.habbohotel.items.interactions.InteractionWiredEffect;
import com.eu.habbo.habbohotel.items.interactions.wired.WiredSettings;
import com.eu.habbo.habbohotel.rooms.Room;
import com.eu.habbo.habbohotel.rooms.RoomUnit;
import com.eu.habbo.habbohotel.users.HabboItem;
import com.eu.habbo.habbohotel.wired.WiredEffectType;
import com.eu.habbo.habbohotel.wired.core.WiredContext;
import com.eu.habbo.habbohotel.wired.core.WiredInternalVariableSupport;
import com.eu.habbo.habbohotel.wired.core.WiredManager;
import com.eu.habbo.habbohotel.wired.core.WiredSourceUtil;
import com.eu.habbo.messages.ServerMessage;
import com.eu.habbo.messages.incoming.wired.WiredSaveException;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

/** Sets @opacity on exactly one target kind; unrelated opacity effects are unchanged. */
public class WiredEffectSetVisibility extends InteractionWiredEffect {
    private final List<HabboItem> items = new ArrayList<>();
    private int opacity = 100;
    private int target = 1;
    private int furniSource = WiredSourceUtil.SOURCE_SELECTED;
    private int userSource = WiredSourceUtil.SOURCE_TRIGGER;

    public WiredEffectSetVisibility(ResultSet set, Item item) throws SQLException {
        super(set, item);
    }

    public WiredEffectSetVisibility(int id, int userId, Item item, String data, int stack, int sells) {
        super(id, userId, item, data, stack, sells);
    }

    @Override
    public void execute(WiredContext context) {
        if (context == null || context.room() == null) return;
        Room room = context.room();
        if (target == 1) {
            List<HabboItem> targets = WiredSourceUtil.resolveItems(context, furniSource, items).stream()
                    .filter(item -> item != null && room.getHabboItem(item.getId()) == item)
                    .distinct()
                    .toList();
            java.util.Map<Integer, Integer> previous = new java.util.HashMap<>();
            for (HabboItem item : targets)
                previous.put(item.getId(), room.getWiredRuntime().globalOpacity(item));
            for (var applied : room.getWiredRuntime().setGlobalOpacityForItems(room, targets, opacity)) {
                HabboItem item = room.getHabboItem(applied.itemId());
                if (item != null)
                    room.getWiredRuntime()
                            .getVariableMonitor()
                            .changed(room, 1, null, item, "@opacity", previous.get(item.getId()), applied.opacity());
            }
        } else {
            for (RoomUnit unit : WiredSourceUtil.resolveUsers(context, userSource))
                WiredInternalVariableSupport.writeUserValue(room, unit, "@opacity", opacity);
        }
    }

    @Override
    public boolean saveData(WiredSettings settings, GameClient client) throws WiredSaveException {
        Room room = getRoom();
        int[] params = settings == null ? null : settings.getIntParams();
        if (room == null
                || params == null
                || (params.length != 3 && params.length != 4)
                || params[0] < 0
                || params[0] > 100) throw new WiredSaveException("Invalid visibility data");
        int nextTarget = params.length == 4 ? params[3] : 1;
        if (nextTarget != 0 && nextTarget != 1) throw new WiredSaveException("Invalid visibility target");
        int delay = settings.getDelay();
        if (delay < 0 || delay > WiredPlatform.configuration().getInt("hotel.wired.max_delay", 20))
            throw new WiredSaveException("Invalid delay");
        int[] ids = settings.getFurniIds() == null ? new int[0] : settings.getFurniIds();
        if (ids.length > WiredManager.MAXIMUM_FURNI_SELECTION) throw new WiredSaveException("Too many furni");
        List<HabboItem> selected = new ArrayList<>();
        for (int id : ids) {
            HabboItem item = room.getHabboItem(id);
            if (item == null) throw new WiredSaveException("Selected furni not found");
            if (!selected.contains(item)) selected.add(item);
        }
        int nextFurniSource = WiredMovementPayloadGuard.furniSource(params[1]);
        int nextUserSource = WiredMovementPayloadGuard.userSource(params[2]);
        items.clear();
        items.addAll(selected);
        opacity = params[0];
        target = nextTarget;
        furniSource = nextFurniSource;
        userSource = nextUserSource;
        setDelay(delay);
        return true;
    }

    @Override
    public String getWiredData() {
        validateItems(items);
        return WiredManager.getGson()
                .toJson(new JsonData(
                        opacity,
                        target,
                        furniSource,
                        userSource,
                        getDelay(),
                        items.stream().map(HabboItem::getId).toList()));
    }

    @Override
    public void loadWiredData(ResultSet set, Room room) throws SQLException {
        onPickUp();
        JsonData data = WiredMovementPayloadGuard.fromJson(set.getString("wired_data"), JsonData.class);
        if (data == null) return;
        opacity = Math.max(0, Math.min(100, data.opacity));
        target = data.target == null ? 1 : data.target == 0 ? 0 : 1;
        furniSource = WiredMovementPayloadGuard.furniSource(data.furniSource);
        userSource = WiredMovementPayloadGuard.userSource(data.userSource);
        setDelay(WiredMovementPayloadGuard.delay(data.delay));
        if (data.itemIds != null)
            for (Integer id : data.itemIds) {
                HabboItem item = id == null ? null : room.getHabboItem(id);
                if (item != null && !items.contains(item) && items.size() < WiredManager.MAXIMUM_FURNI_SELECTION)
                    items.add(item);
            }
    }

    @Override
    public void serializeWiredData(ServerMessage message, Room room) {
        validateItems(items);
        message.appendBoolean(false);
        message.appendInt(WiredManager.MAXIMUM_FURNI_SELECTION);
        message.appendInt(items.size());
        for (HabboItem item : items) message.appendInt(item.getId());
        message.appendInt(getBaseItem().getSpriteId());
        message.appendInt(getId());
        message.appendString("");
        message.appendInt(4);
        message.appendInt(opacity);
        message.appendInt(furniSource);
        message.appendInt(userSource);
        message.appendInt(target);
        message.appendInt(0);
        message.appendInt(getType().code);
        message.appendInt(getDelay());
        message.appendInt(0);
    }

    @Override
    public WiredEffectType getType() {
        return WiredEffectType.SET_VISIBILITY;
    }

    @Override
    public boolean requiresTriggeringUser() {
        return target == 0 && userSource == WiredSourceUtil.SOURCE_TRIGGER;
    }

    @Deprecated
    @Override
    public boolean execute(RoomUnit unit, Room room, Object[] stuff) {
        return false;
    }

    @Override
    public void onPickUp() {
        items.clear();
        opacity = 100;
        target = 1;
        furniSource = WiredSourceUtil.SOURCE_SELECTED;
        userSource = WiredSourceUtil.SOURCE_TRIGGER;
        setDelay(0);
    }

    private record JsonData(
            int opacity, Integer target, int furniSource, int userSource, int delay, List<Integer> itemIds) {}
}
