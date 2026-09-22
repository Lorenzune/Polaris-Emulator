package com.eu.habbo.habbohotel.items.interactions.wired.triggers;

import com.eu.habbo.Emulator;
import com.eu.habbo.WiredCompatibilityDiagnostics;
import com.eu.habbo.habbohotel.gameclients.GameClient;
import com.eu.habbo.habbohotel.items.Item;
import com.eu.habbo.habbohotel.items.interactions.InteractionWiredTrigger;
import com.eu.habbo.habbohotel.items.interactions.wired.WiredSettings;
import com.eu.habbo.habbohotel.rooms.Room;
import com.eu.habbo.habbohotel.rooms.RoomUnit;
import com.eu.habbo.habbohotel.users.HabboItem;
import com.eu.habbo.habbohotel.wired.WiredTriggerType;
import com.eu.habbo.habbohotel.wired.core.WiredEvent;
import com.eu.habbo.habbohotel.wired.core.WiredManager;
import com.eu.habbo.habbohotel.wired.core.WiredSourceUtil;
import com.eu.habbo.habbohotel.wired.core.WiredTriggerSourceUtil;
import com.eu.habbo.messages.ServerMessage;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

public class WiredTriggerCollision extends InteractionWiredTrigger {
    private static final WiredTriggerType type = WiredTriggerType.COLLISION;

    private final Set<HabboItem> items;
    private int furniSource = WiredSourceUtil.SOURCE_SELECTED;

    public WiredTriggerCollision(ResultSet set, Item baseItem) throws SQLException {
        super(set, baseItem);
        this.items = new LinkedHashSet<>();
    }

    public WiredTriggerCollision(int id, int userId, Item item, String extradata, int limitedStack, int limitedSells) {
        super(id, userId, item, extradata, limitedStack, limitedSells);
        this.items = new LinkedHashSet<>();
    }

    @Override
    public boolean matches(HabboItem triggerItem, WiredEvent event) {
        // Collision trigger fires when a furniture item moves and collides with a room unit
        // The actor is the room unit that was collided with
        if (!event.getActor().isPresent()) {
            return false;
        }

        HabboItem sourceItem = event.getSourceItem().orElse(null);
        Room room = event.getRoom();
        List<HabboItem> candidateItems = WiredTriggerSourceUtil.resolveItems(this, event, this.furniSource, this.items);
        if (candidateItems.isEmpty()) {
            return true;
        }

        if (sourceItem != null && WiredTriggerSourceUtil.containsItemOrTile(room, candidateItems, sourceItem)) {
            return true;
        }

        RoomUnit actorUnit = event.getActor().get();
        if (room != null && actorUnit.getCurrentLocation() != null) {
            HabboItem actorTopItem = room.getTopItemAt(actorUnit.getX(), actorUnit.getY());
            if (actorTopItem != null && WiredTriggerSourceUtil.containsItemOrTile(room, candidateItems, actorTopItem)) {
                return true;
            }

            if (room.getLayout() != null) {
                HabboItem tallestChair =
                        room.getTallestChair(room.getLayout().getTile(actorUnit.getX(), actorUnit.getY()));
                if (tallestChair != null
                        && WiredTriggerSourceUtil.containsItemOrTile(room, candidateItems, tallestChair)) {
                    return true;
                }
            }

            Set<HabboItem> itemsAtActor = room.getItemsAt(actorUnit.getX(), actorUnit.getY());
            if (itemsAtActor != null) {
                for (HabboItem itemOnTile : itemsAtActor) {
                    if (itemOnTile != null
                            && WiredTriggerSourceUtil.containsItemOrTile(room, candidateItems, itemOnTile)) {
                        return true;
                    }
                }
            }
        }

        return false;
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
        Set<HabboItem> items = new HashSet<>();

        if (Emulator.getGameEnvironment().getRoomManager().getRoom(this.getRoomId()) == null) {
            items.addAll(this.items);
        } else {
            for (HabboItem item : this.items) {
                if (Emulator.getGameEnvironment()
                                .getRoomManager()
                                .getRoom(this.getRoomId())
                                .getHabboItem(item.getId())
                        == null) items.add(item);
            }
        }

        for (HabboItem item : items) {
            this.items.remove(item);
        }

        message.appendBoolean(false);
        message.appendInt(WiredManager.MAXIMUM_FURNI_SELECTION);
        message.appendInt(this.items.size());
        for (HabboItem item : this.items) {
            message.appendInt(item.getId());
        }
        message.appendInt(this.getBaseItem().getSpriteId());
        message.appendInt(this.getId());
        message.appendString("");
        message.appendInt(1);
        message.appendInt(this.furniSource);
        message.appendInt(0);
        message.appendInt(type.code);
        message.appendInt(0);
        message.appendInt(0);
    }

    @Override
    public boolean saveData(WiredSettings settings) {
        return this.saveData(settings, null);
    }

    @Override
    public boolean saveData(WiredSettings settings, GameClient gameClient) {
        this.items.clear();
        this.furniSource = (settings.getIntParams().length > 0)
                ? this.normalizeFurniSource(settings.getIntParams()[0])
                : WiredSourceUtil.SOURCE_SELECTED;

        if (this.furniSource != WiredSourceUtil.SOURCE_SELECTED) {
            return true;
        }

        int count = settings.getFurniIds().length;
        Room room = Emulator.getGameEnvironment().getRoomManager().getRoom(this.getRoomId());
        if (room == null) {
            return false;
        }

        for (int i = 0; i < count; i++) {
            HabboItem item = room.getHabboItem(settings.getFurniIds()[i]);
            if (item != null) {
                this.items.add(item);
            }
        }

        return true;
    }

    @Override
    public String getWiredData() {
        return WiredManager.getGson()
                .toJson(new JsonData(
                        this.furniSource,
                        this.items.stream().map(HabboItem::getId).collect(Collectors.toList())));
    }

    @Override
    public void loadWiredData(ResultSet set, Room room) throws SQLException {
        this.items.clear();
        this.furniSource = WiredSourceUtil.SOURCE_SELECTED;
        String wiredData = set.getString("wired_data");

        if (wiredData == null || wiredData.isEmpty()) {
            return;
        }

        if (wiredData.startsWith("{")) {
            JsonData data = WiredManager.getGson().fromJson(wiredData, JsonData.class);
            this.furniSource = this.normalizeFurniSource(data.furniSource);
            if (data.itemIds != null) {
                for (Integer id : data.itemIds) {
                    HabboItem item = room.getHabboItem(id);
                    if (item != null) {
                        this.items.add(item);
                    }
                }
            }
        } else {
            if (wiredData.split(":").length >= 3) {
                super.setDelay(Integer.parseInt(wiredData.split(":")[0]));

                if (!wiredData.split(":")[2].equals("\t")) {
                    for (String s : wiredData.split(":")[2].split(";")) {
                        if (s.isEmpty()) continue;

                        try {
                            HabboItem item = room.getHabboItem(Integer.parseInt(s));

                            if (item != null) this.items.add(item);
                        } catch (Exception e) {
                            WiredCompatibilityDiagnostics.record(
                                    WiredCompatibilityDiagnostics.FailurePoint.TRIGGER_WALK_ON_ITEM,
                                    room.getId(),
                                    this.getId(),
                                    e);
                        }
                    }
                }
            }

            this.furniSource = WiredSourceUtil.SOURCE_SELECTED;
        }
    }

    @Override
    public void onPickUp() {
        this.items.clear();
        this.furniSource = WiredSourceUtil.SOURCE_SELECTED;
    }

    @Override
    public boolean isTriggeredByRoomUnit() {
        return true;
    }

    static class JsonData {
        int furniSource;
        List<Integer> itemIds;

        public JsonData(int furniSource, List<Integer> itemIds) {
            this.furniSource = furniSource;
            this.itemIds = itemIds;
        }
    }

    private int normalizeFurniSource(int value) {
        if (value == WiredSourceUtil.SOURCE_SELECTOR) {
            return WiredSourceUtil.SOURCE_SELECTOR;
        }

        return WiredSourceUtil.SOURCE_SELECTED;
    }
}
