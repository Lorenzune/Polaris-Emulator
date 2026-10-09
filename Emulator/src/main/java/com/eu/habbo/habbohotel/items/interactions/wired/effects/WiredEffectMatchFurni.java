package com.eu.habbo.habbohotel.items.interactions.wired.effects;

import com.eu.habbo.Emulator;
import com.eu.habbo.WiredPlatform;
import com.eu.habbo.habbohotel.gameclients.GameClient;
import com.eu.habbo.habbohotel.items.Item;
import com.eu.habbo.habbohotel.items.interactions.InteractionWiredEffect;
import com.eu.habbo.habbohotel.items.interactions.wired.WiredNativeSnapshotSettings;
import com.eu.habbo.habbohotel.items.interactions.wired.WiredSettings;
import com.eu.habbo.habbohotel.items.interactions.wired.interfaces.InteractionWiredMatchFurniSettings;
import com.eu.habbo.habbohotel.rooms.Room;
import com.eu.habbo.habbohotel.rooms.RoomTile;
import com.eu.habbo.habbohotel.rooms.RoomTileState;
import com.eu.habbo.habbohotel.rooms.RoomUnit;
import com.eu.habbo.habbohotel.users.HabboItem;
import com.eu.habbo.habbohotel.wired.WiredEffectType;
import com.eu.habbo.habbohotel.wired.WiredMatchFurniSetting;
import com.eu.habbo.habbohotel.wired.core.WiredContext;
import com.eu.habbo.habbohotel.wired.core.WiredManager;
import com.eu.habbo.habbohotel.wired.core.WiredMoveCarryHelper;
import com.eu.habbo.habbohotel.wired.core.WiredSourceUtil;
import com.eu.habbo.messages.ServerMessage;
import com.eu.habbo.messages.incoming.wired.WiredSaveException;
import java.math.BigDecimal;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class WiredEffectMatchFurni extends InteractionWiredEffect implements InteractionWiredMatchFurniSettings {
    private static final Logger LOGGER = LoggerFactory.getLogger(WiredEffectMatchFurni.class);

    private static final WiredEffectType type = WiredEffectType.MATCH_SSHOT;
    public boolean checkForWiredResetPermission = true;
    private Set<WiredMatchFurniSetting> settings;
    private boolean state = false;
    private boolean direction = false;
    private boolean position = false;
    private boolean altitude = false;
    private int furniSource = WiredSourceUtil.SOURCE_SELECTED;

    public WiredEffectMatchFurni(ResultSet set, Item baseItem) throws SQLException {
        super(set, baseItem);
        this.settings = new HashSet<>(0);
    }

    public WiredEffectMatchFurni(int id, int userId, Item item, String extradata, int limitedStack, int limitedSells) {
        super(id, userId, item, extradata, limitedStack, limitedSells);
        this.settings = new HashSet<>(0);
    }

    @Override
    public void execute(WiredContext ctx) {
        Room room = ctx.room();
        this.refresh();

        if (this.settings.isEmpty()) return;

        if (room == null || room.getLayout() == null) return;

        if (this.furniSource == WiredSourceUtil.SOURCE_SELECTED) {
            for (WiredMatchFurniSetting setting : this.settings) {
                HabboItem item = room.getHabboItem(setting.item_id);
                if (item != null) {
                    this.applySetting(room, item, setting, ctx);
                }
            }

            return;
        }

        List<HabboItem> targets = WiredSourceUtil.resolveItems(ctx, this.furniSource, null);
        if (targets.isEmpty()) {
            return;
        }

        for (HabboItem item : targets) {
            if (item == null) continue;

            WiredMatchFurniSetting setting = this.resolveSettingForTarget(room, item);
            if (setting == null) continue;

            this.applySetting(room, item, setting, ctx);
        }
    }

    private WiredMatchFurniSetting resolveSettingForTarget(Room room, HabboItem target) {
        WiredMatchFurniSetting fallback = null;

        for (WiredMatchFurniSetting setting : this.settings) {
            HabboItem sourceItem = room.getHabboItem(setting.item_id);
            if (sourceItem == null) continue;
            if (sourceItem.getBaseItem().getId() != target.getBaseItem().getId()) continue;

            if (setting.state.equals(target.getExtradata())) {
                return setting;
            }

            if (fallback == null) {
                fallback = setting;
            }
        }

        return fallback;
    }

    private void applySetting(Room room, HabboItem item, WiredMatchFurniSetting setting, WiredContext ctx) {
        if (this.state && this.checkForWiredResetPermission
                && (item.allowWiredResetState()
                        || item instanceof com.eu.habbo.habbohotel.items.interactions.games.football.scoreboards.InteractionFootballScoreboard)) {
            if (item instanceof com.eu.habbo.habbohotel.items.interactions.games.football.scoreboards.InteractionFootballScoreboard counter) {
                // SWF snapshots use an empty state for a counter's initial value.
                // Updating only extradata leaves the counter's numeric score stale.
                try {
                    int score = setting.state.isBlank() ? 0 : Integer.parseInt(setting.state);
                    if (score != counter.getScore()) {
                        counter.setScore(score);
                        room.updateItemState(counter);
                    }
                } catch (NumberFormatException ignored) {
                    com.eu.habbo.WiredCompatibilityDiagnostics.record(
                            com.eu.habbo.WiredCompatibilityDiagnostics.FailurePoint.EFFECT_MATCH_COUNTER_STATE, ignored);
                }
            } else if (!setting.state.equals(" ") && !setting.state.equals(item.getExtradata())) {
                item.setExtradata(setting.state);
                item.needsUpdate(true);
                room.updateItemState(item);
            }
        }

        RoomTile oldLocation = room.getLayout().getTile(item.getX(), item.getY());
        if (oldLocation == null) return;

        RoomTile destination =
                this.position ? room.getLayout().getTile((short) setting.x, (short) setting.y) : oldLocation;
        int rotation = this.direction ? setting.rotation : item.getRotation();
        Double height = null;
        if (this.altitude) height = setting.z;
        else if (this.position) height = item.getZ();
        boolean heightChanged =
                height != null && BigDecimal.valueOf(item.getZ()).compareTo(BigDecimal.valueOf(height)) != 0;
        if (destination == null
                || destination.state == RoomTileState.INVALID
                || (destination == oldLocation && rotation == item.getRotation() && !heightChanged)) return;
        WiredMoveCarryHelper.moveFurni(
                room, this, item, destination, rotation, height, null, rotation != item.getRotation(), ctx);
    }

    @Deprecated
    @Override
    public boolean execute(RoomUnit roomUnit, Room room, Object[] stuff) {
        return false;
    }

    @Override
    public boolean simulate(WiredContext ctx, com.eu.habbo.habbohotel.wired.core.WiredSimulation simulation) {
        if (ctx == null || ctx.room() == null || ctx.room().getLayout() == null) return false;
        Room room = ctx.room();
        List<HabboItem> targets = this.furniSource == WiredSourceUtil.SOURCE_SELECTED
                ? this.settings.stream()
                        .map(s -> room.getHabboItem(s.item_id))
                        .filter(java.util.Objects::nonNull)
                        .toList()
                : WiredMovementTargets.items(ctx, this.furniSource, null);
        for (HabboItem item : targets) {
            WiredMatchFurniSetting captured = this.furniSource == WiredSourceUtil.SOURCE_SELECTED
                    ? this.settings.stream()
                            .filter(s -> s.item_id == item.getId())
                            .findFirst()
                            .orElse(null)
                    : this.resolveSettingForTarget(room, item);
            if (captured == null) continue;
            var p = simulation.getItemPosition(item);
            if (this.position
                    && (captured.x < Short.MIN_VALUE
                            || captured.x > Short.MAX_VALUE
                            || captured.y < Short.MIN_VALUE
                            || captured.y > Short.MAX_VALUE)) return false;
            short x = this.position ? (short) captured.x : p.x, y = this.position ? (short) captured.y : p.y;
            int rotation = this.direction ? captured.rotation : p.rotation;
            double z = this.altitude ? captured.z : p.z;
            if ((x != p.x || y != p.y || rotation != p.rotation || z != p.z)
                    && !simulation.moveItem(ctx, this, item, x, y, z, rotation, false)) return false;
        }
        return true;
    }

    @Override
    public String getWiredData() {
        this.refresh();
        return WiredManager.getGson()
                .toJson(new JsonData(
                        this.state,
                        this.direction,
                        this.position,
                        this.altitude,
                        new ArrayList<WiredMatchFurniSetting>(this.settings),
                        this.getDelay(),
                        this.furniSource));
    }

    @Override
    public void loadWiredData(ResultSet set, Room room) throws SQLException {
        this.onPickUp();
        String wiredData = set.getString("wired_data");
        if (wiredData == null || wiredData.isBlank()) return;
        try {
            if (wiredData.startsWith("{")) {
                JsonData data = WiredManager.getGson().fromJson(wiredData, JsonData.class);
                if (data == null) return;
                this.setDelay(data.delay);
                this.state = data.state;
                this.direction = data.direction;
                this.position = data.position;
                this.altitude = data.altitude;
                this.settings.clear();
                this.settings.addAll(
                        com.eu.habbo.habbohotel.items.interactions.wired.conditions.WiredMatchPositionInputGuard
                                .sanitizeSettings(data.items, room));
                this.furniSource = data.furniSource;
            } else {
                String[] data = set.getString("wired_data").split(":");

                Integer.parseInt(data[0]); // itemCount - consumed but unused, data[1] contains actual items

                String[] items = data[1].split(Pattern.quote(";"));

                for (int i = 0; i < items.length; i++) {
                    try {

                        String[] stuff = items[i].split(Pattern.quote("-"));

                        if (stuff.length >= 6) {
                            this.settings.add(new WiredMatchFurniSetting(
                                    Integer.parseInt(stuff[0]),
                                    stuff[1],
                                    Integer.parseInt(stuff[2]),
                                    Integer.parseInt(stuff[3]),
                                    Integer.parseInt(stuff[4]),
                                    Double.parseDouble(stuff[5])));
                        } else if (stuff.length >= 5) {
                            this.settings.add(new WiredMatchFurniSetting(
                                    Integer.parseInt(stuff[0]),
                                    stuff[1],
                                    Integer.parseInt(stuff[2]),
                                    Integer.parseInt(stuff[3]),
                                    Integer.parseInt(stuff[4])));
                        }

                    } catch (Exception e) {
                        LOGGER.error("Caught exception", e);
                    }
                }

                this.state = data[2].equals("1");
                this.direction = data[3].equals("1");
                this.position = data[4].equals("1");
                this.altitude = false;
                this.setDelay(Integer.parseInt(data[5]));
                this.furniSource =
                        this.settings.isEmpty() ? WiredSourceUtil.SOURCE_TRIGGER : WiredSourceUtil.SOURCE_SELECTED;
                this.needsUpdate(true);
            }
        } catch (RuntimeException exception) {
            this.onPickUp();
        }
    }

    @Override
    public void onPickUp() {
        this.settings.clear();
        this.state = false;
        this.direction = false;
        this.position = false;
        this.altitude = false;
        this.furniSource = WiredSourceUtil.SOURCE_SELECTED;
        this.setDelay(0);
    }

    @Override
    public WiredEffectType getType() {
        return type;
    }

    @Override
    public void serializeWiredData(ServerMessage message, Room room) {
        this.refresh();

        message.appendBoolean(false);
        message.appendInt(WiredManager.MAXIMUM_FURNI_SELECTION);
        message.appendInt(this.settings.size());

        for (WiredMatchFurniSetting item : this.settings) message.appendInt(item.item_id);

        message.appendInt(this.getBaseItem().getSpriteId());
        message.appendInt(this.getId());
        message.appendString("");
        message.appendInt(5);
        message.appendInt(this.state ? 1 : 0);
        message.appendInt(this.direction ? 1 : 0);
        message.appendInt(this.position ? 1 : 0);
        message.appendInt(this.altitude ? 1 : 0);
        message.appendInt(this.furniSource);
        message.appendInt(0);
        message.appendInt(this.getType().code);
        message.appendInt(this.getDelay());
        message.appendInt(0);
    }

    @Override
    public boolean saveData(WiredSettings settings, GameClient gameClient) throws WiredSaveException {
        settings = WiredNativeSnapshotSettings.normalize(settings, false);
        if (settings.getIntParams().length < 4) throw new WiredSaveException("Invalid data");
        boolean setState = settings.getIntParams()[0] == 1;
        boolean setDirection = settings.getIntParams()[1] == 1;
        boolean setPosition = settings.getIntParams()[2] == 1;
        boolean setAltitude = (settings.getIntParams().length > 4) ? (settings.getIntParams()[3] == 1) : false;
        int newSource = (settings.getIntParams().length > 4) ? settings.getIntParams()[4] : settings.getIntParams()[3];

        Room room = Emulator.getGameEnvironment().getRoomManager().getRoom(this.getRoomId());

        if (room == null) throw new WiredSaveException("Trying to save wired in unloaded room");

        int itemsCount = settings.getFurniIds().length;

        if (itemsCount > Emulator.getConfig().getInt("hotel.wired.furni.selection.count")) {
            throw new WiredSaveException("Too many furni selected");
        }

        List<WiredMatchFurniSetting> newSettings = new ArrayList<>();

        for (int i = 0; i < itemsCount; i++) {
            int itemId = settings.getFurniIds()[i];
            HabboItem it = room.getHabboItem(itemId);

            if (it == null) throw new WiredSaveException(String.format("Item %s not found", itemId));

            newSettings.add(new WiredMatchFurniSetting(
                    it.getId(),
                    this.checkForWiredResetPermission && it.allowWiredResetState() ? it.getExtradata() : " ",
                    it.getRotation(),
                    it.getX(),
                    it.getY(),
                    it.getZ()));
        }

        int delay = settings.getDelay();

        if (delay < 0 || delay > Emulator.getConfig().getInt("hotel.wired.max_delay", 20))
            throw new WiredSaveException("Delay too long");

        this.state = setState;
        this.direction = setDirection;
        this.position = setPosition;
        this.altitude = setAltitude;
        this.furniSource = newSource;
        this.settings.clear();
        this.settings.addAll(newSettings);
        this.setDelay(delay);

        return true;
    }

    private void refresh() {
        if (WiredPlatform.gameEnvironment() == null
                || WiredPlatform.gameEnvironment().getRoomManager() == null) {
            return;
        }

        Room room = WiredPlatform.gameEnvironment().getRoomManager().getRoom(this.getRoomId());

        if (room != null && room.isLoaded()) {
            // Use removeIf for O(n) instead of O(n²) with separate remove set
            this.settings.removeIf(setting -> room.getHabboItem(setting.item_id) == null);
        }
    }

    @Override
    public Set<WiredMatchFurniSetting> getMatchFurniSettings() {
        return this.settings;
    }

    @Override
    public boolean shouldMatchState() {
        return this.state;
    }

    @Override
    public boolean shouldMatchRotation() {
        return this.direction;
    }

    @Override
    public boolean shouldMatchPosition() {
        return this.position;
    }

    @Override
    public boolean shouldMatchAltitude() {
        return this.altitude;
    }

    static class JsonData {
        boolean state;
        boolean direction;
        boolean position;
        boolean altitude;
        List<WiredMatchFurniSetting> items;
        int delay;
        int furniSource;

        public JsonData(
                boolean state,
                boolean direction,
                boolean position,
                boolean altitude,
                List<WiredMatchFurniSetting> items,
                int delay,
                int furniSource) {
            this.state = state;
            this.direction = direction;
            this.position = position;
            this.altitude = altitude;
            this.items = items;
            this.delay = delay;
            this.furniSource = furniSource;
        }
    }
}
