package com.eu.habbo.habbohotel.items.interactions.wired.selector;

import com.eu.habbo.habbohotel.gameclients.GameClient;
import com.eu.habbo.habbohotel.items.Item;
import com.eu.habbo.habbohotel.items.interactions.InteractionWired;
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
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/** Combines selected stacks' selector results. Stored legacy configurations keep their previous signal/picks behavior until resaved. */
public class WiredEffectRemoteSelector extends InteractionWiredEffect {
    public static final WiredEffectType type = WiredEffectType.REMOTE_SELECTOR;
    private static final int MAX_PICKED_FURNI = 20;

    private boolean filterExisting = false;
    private boolean invert = false;
    private boolean nativeSelection;
    private int selectionType;
    private int stackLimit;
    private List<Integer> pickedFurniIds = new ArrayList<>();

    public WiredEffectRemoteSelector(ResultSet set, Item baseItem) throws SQLException {
        super(set, baseItem);
    }

    public WiredEffectRemoteSelector(
            int id, int userId, Item item, String extradata, int limitedStack, int limitedSells) {
        super(id, userId, item, extradata, limitedStack, limitedSells);
    }

    @Override
    public void execute(WiredContext ctx) {
        if (ctx == null) {
            return;
        }

        Room room = ctx.room();
        if (room == null) {
            return;
        }
        if (nativeSelection) {
            executeRemote(ctx);
            return;
        }

        boolean includeWiredItems = this.includeWiredTargets(ctx);

        Set<HabboItem> matched = new LinkedHashSet<>();

        for (HabboItem item : WiredSourceUtil.resolveItemsRaw(ctx, WiredSourceUtil.SOURCE_SIGNAL, null)) {
            if (item != null && (includeWiredItems || !(item instanceof InteractionWired))) {
                matched.add(item);
            }
        }

        if (matched.isEmpty()) {
            for (Integer id : this.pickedFurniIds) {
                HabboItem item = room.getHabboItem(id);
                if (item != null && (includeWiredItems || !(item instanceof InteractionWired))) {
                    matched.add(item);
                }
            }
        }

        Set<HabboItem> result = this.applySelectorModifiers(
                matched,
                this.getSelectableFloorItems(room, ctx),
                ctx.targets().items(),
                this.filterExisting,
                this.invert);
        ctx.targets().setItems(result);
    }

    @Override
    public boolean saveData(WiredSettings settings, GameClient gameClient) {
        settings = WiredNativeSelectorSettings.normalize(this, settings);
        int[] params = settings.getIntParams();

        this.filterExisting = params.length > 0 && params[0] == 1;
        this.invert = params.length > 1 && params[1] == 1;
        if (params.length >= 4 && (params[2] < 0 || params[2] > 1 || params[3] < 0))
            throw new IllegalArgumentException("Invalid remote selection");
        this.nativeSelection = params.length >= 4;
        this.selectionType = nativeSelection ? params[2] : 0;
        this.stackLimit = nativeSelection ? params[3] : 0;

        this.pickedFurniIds = new ArrayList<>();
        if (settings.getFurniIds() != null) {
            for (int id : settings.getFurniIds()) {
                if (this.pickedFurniIds.size() >= MAX_PICKED_FURNI) break;
                this.pickedFurniIds.add(id);
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
    public boolean isSelector() {
        return true;
    }

    @Override
    public boolean usesExistingSelectorTargets() {
        return this.filterExisting;
    }

    @Override
    public String getWiredData() {
        return WiredManager.getGson()
                .toJson(new JsonData(
                        this.filterExisting,
                        this.invert,
                        this.pickedFurniIds,
                        this.getDelay(),
                        this.nativeSelection,
                        this.selectionType,
                        this.stackLimit));
    }

    @Override
    public void loadWiredData(ResultSet set, Room room) throws SQLException {
        this.onPickUp();

        String wiredData = set.getString("wired_data");
        if (wiredData == null || !wiredData.startsWith("{")) {
            return;
        }

        JsonData data = WiredSelectorPayloadGuard.fromJson(wiredData, JsonData.class);
        if (data == null) {
            return;
        }

        this.filterExisting = data.filterExisting;
        this.invert = data.invert;
        this.nativeSelection = data.nativeSelection;
        this.selectionType = data.selectionType == 1 ? 1 : 0;
        this.stackLimit = Math.max(0, data.stackLimit);
        this.pickedFurniIds = (data.pickedFurniIds != null) ? data.pickedFurniIds : new ArrayList<>();
        this.setDelay(data.delay);
    }

    @Override
    public void onPickUp() {
        this.filterExisting = false;
        this.invert = false;
        this.nativeSelection = false;
        this.selectionType = 0;
        this.stackLimit = 0;
        this.pickedFurniIds = new ArrayList<>();
        this.setDelay(0);
    }

    @Override
    public void serializeWiredData(ServerMessage message, Room room) {
        message.appendBoolean(true);
        message.appendInt(MAX_PICKED_FURNI);

        if (!this.pickedFurniIds.isEmpty()) {
            message.appendInt(this.pickedFurniIds.size());
            this.pickedFurniIds.forEach(message::appendInt);
        } else {
            message.appendInt(0);
        }

        message.appendInt(this.getBaseItem().getSpriteId());
        message.appendInt(this.getId());
        message.appendString("");
        message.appendInt(4);
        message.appendInt(this.filterExisting ? 1 : 0);
        message.appendInt(this.invert ? 1 : 0);
        message.appendInt(this.selectionType);
        message.appendInt(this.stackLimit);
        message.appendInt(0);
        message.appendInt(this.getType().code);
        message.appendInt(this.getDelay());
        message.appendInt(0);
    }

    @Override
    public boolean execute(RoomUnit roomUnit, Room room, Object[] stuff) {
        return false;
    }

    static class JsonData {
        boolean filterExisting;
        boolean invert;
        List<Integer> pickedFurniIds;
        int delay;
        boolean nativeSelection;
        int selectionType;
        int stackLimit;

        JsonData(
                boolean filterExisting,
                boolean invert,
                List<Integer> pickedFurniIds,
                int delay,
                boolean nativeSelection,
                int selectionType,
                int stackLimit) {
            this.filterExisting = filterExisting;
            this.invert = invert;
            this.pickedFurniIds = pickedFurniIds;
            this.delay = delay;
            this.nativeSelection = nativeSelection;
            this.selectionType = selectionType;
            this.stackLimit = stackLimit;
        }
    }

    private void executeRemote(WiredContext ctx) {
        var stacks = new java.util.LinkedHashMap<String, HabboItem>();
        for (int id : pickedFurniIds) {
            HabboItem item = ctx.room().getHabboItem(id);
            if (item instanceof InteractionWired) stacks.putIfAbsent(item.getX() + ":" + item.getY(), item);
        }
        var selected = new ArrayList<>(stacks.values());
        if (stackLimit > 0 && selected.size() > stackLimit) {
            java.util.Collections.shuffle(selected, com.eu.habbo.Emulator.getRandom());
            selected = new ArrayList<>(selected.subList(0, stackLimit));
        }
        Set<HabboItem> items = new LinkedHashSet<>();
        Set<RoomUnit> users = new LinkedHashSet<>();
        boolean first = true;
        for (HabboItem item : selected) {
            var targets = WiredSourceUtil.resolveRemoteSelectors(ctx, item);
            Set<HabboItem> nextItems =
                    targets.isItemsModifiedBySelector() ? targets.items() : java.util.Collections.emptySet();
            Set<RoomUnit> nextUsers =
                    targets.isUsersModifiedBySelector() ? targets.users() : java.util.Collections.emptySet();
            if (first || selectionType == 0) {
                items.addAll(nextItems);
                users.addAll(nextUsers);
            } else {
                items.retainAll(nextItems);
                users.retainAll(nextUsers);
            }
            first = false;
        }
        ctx.targets()
                .setItems(applySelectorModifiers(
                        items,
                        getSelectableFloorItems(ctx.room(), ctx),
                        ctx.targets().items(),
                        filterExisting,
                        invert));
        ctx.targets()
                .setUsers(applySelectorModifiers(
                        users, ctx.room().getRoomUnits(), ctx.targets().users(), filterExisting, invert));
    }
}
