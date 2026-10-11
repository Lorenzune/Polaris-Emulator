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
import com.eu.habbo.habbohotel.wired.core.WiredManager;
import com.eu.habbo.habbohotel.wired.core.WiredSourceUtil;
import com.eu.habbo.messages.ServerMessage;
import com.eu.habbo.messages.incoming.wired.WiredSaveException;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

/** Applies a bounded room-scoped highlight to one target kind. */
public class WiredEffectSetHighlight extends InteractionWiredEffect {
    private final List<HabboItem> items = new ArrayList<>();
    private int style = 1;
    private int color = 0xffd700;
    private int target = 1;
    private int furniSource = WiredSourceUtil.SOURCE_SELECTED;
    private int userSource = WiredSourceUtil.SOURCE_TRIGGER;

    public WiredEffectSetHighlight(ResultSet set, Item item) throws SQLException {
        super(set, item);
    }

    public WiredEffectSetHighlight(int id, int userId, Item item, String data, int stack, int sells) {
        super(id, userId, item, data, stack, sells);
    }

    @Override
    public void execute(WiredContext context) {
        if (context == null || context.room() == null) return;
        Room room = context.room();
        List<com.eu.habbo.habbohotel.rooms.WiredHighlightState> changes = new ArrayList<>();
        if (target == 1) {
            for (HabboItem item : WiredSourceUtil.resolveItems(context, furniSource, items)) {
                if (item != null && room.getHabboItem(item.getId()) == item)
                    changes.add(new com.eu.habbo.habbohotel.rooms.WiredHighlightState(
                            item.getBaseItem().getType() == com.eu.habbo.habbohotel.items.FurnitureType.WALL ? 20 : 10,
                            item.getId(),
                            style,
                            color));
            }
        } else {
            for (RoomUnit unit : WiredSourceUtil.resolveUsers(context, userSource)) {
                if (unit != null && !unit.isInvisible() && room.getRoomUnits().contains(unit))
                    changes.add(new com.eu.habbo.habbohotel.rooms.WiredHighlightState(100, unit.getId(), style, color));
            }
        }
        room.getWiredRuntime().applyHighlights(changes);
    }

    @Override
    public boolean saveData(WiredSettings settings, GameClient client) throws WiredSaveException {
        Room room = getRoom();
        int[] params = settings == null ? null : settings.getIntParams();
        if (room == null || params == null || params.length != 5 || params[0] < 0 || params[0] > 24)
            throw new WiredSaveException("Invalid highlight data");
        int nextTarget = params[2];
        if (params[1] < 0 || params[1] > 0xffffff) throw new WiredSaveException("Invalid highlight color");
        if (nextTarget != 0 && nextTarget != 1) throw new WiredSaveException("Invalid highlight target");
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
        int nextFurniSource = WiredMovementPayloadGuard.furniSource(params[3]);
        int nextUserSource = WiredMovementPayloadGuard.userSource(params[4]);
        items.clear();
        items.addAll(selected);
        style = params[0];
        color = params[1];
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
                        style,
                        color,
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
        style = Math.max(0, Math.min(24, data.style));
        color = Math.max(0, Math.min(0xffffff, data.color));
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
        message.appendInt(5);
        message.appendInt(style);
        message.appendInt(color);
        message.appendInt(target);
        message.appendInt(furniSource);
        message.appendInt(userSource);
        message.appendInt(0);
        message.appendInt(getType().code);
        message.appendInt(getDelay());
        message.appendInt(0);
    }

    @Override
    public WiredEffectType getType() {
        return WiredEffectType.SET_HIGHLIGHT;
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
        style = 1;
        color = 0xffd700;
        target = 1;
        furniSource = WiredSourceUtil.SOURCE_SELECTED;
        userSource = WiredSourceUtil.SOURCE_TRIGGER;
        setDelay(0);
    }

    private record JsonData(
            int style, int color, Integer target, int furniSource, int userSource, int delay, List<Integer> itemIds) {}
}
