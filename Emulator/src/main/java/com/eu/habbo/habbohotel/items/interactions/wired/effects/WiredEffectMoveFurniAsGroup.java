package com.eu.habbo.habbohotel.items.interactions.wired.effects;

import com.eu.habbo.Emulator;
import com.eu.habbo.habbohotel.gameclients.GameClient;
import com.eu.habbo.habbohotel.items.Item;
import com.eu.habbo.habbohotel.items.interactions.InteractionWiredEffect;
import com.eu.habbo.habbohotel.items.interactions.wired.WiredNativeMovementSettings;
import com.eu.habbo.habbohotel.items.interactions.wired.WiredSettings;
import com.eu.habbo.habbohotel.rooms.Room;
import com.eu.habbo.habbohotel.rooms.RoomTile;
import com.eu.habbo.habbohotel.rooms.RoomUnit;
import com.eu.habbo.habbohotel.users.HabboItem;
import com.eu.habbo.habbohotel.wired.WiredEffectType;
import com.eu.habbo.habbohotel.wired.core.WiredContext;
import com.eu.habbo.habbohotel.wired.core.WiredManager;
import com.eu.habbo.habbohotel.wired.core.WiredMoveCarryHelper;
import com.eu.habbo.habbohotel.wired.core.WiredSourceUtil;
import com.eu.habbo.messages.ServerMessage;
import com.eu.habbo.messages.incoming.wired.WiredSaveException;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/** Moves a group toward a user/furni destination with a common offset vector.
 * Existing direction-only JSON remains in legacy mode until resaved.
 * The lowest item id is the stable anchor; leading members move first to vacate packed groups.
 * Collision behavior remains best-effort and uses the common movement physics/carry helpers.
 */
public class WiredEffectMoveFurniAsGroup extends InteractionWiredEffect {
    public static final WiredEffectType type = WiredEffectType.MOVE_FURNI_AS_GROUP;

    private final List<HabboItem> items = new ArrayList<>();
    private int direction;
    private boolean destinationMode = true;
    private boolean targetUser = true;
    private int offsetX, offsetY, targetSource, userSource;
    private final List<HabboItem> targetItems = new ArrayList<>();
    private int furniSource = WiredSourceUtil.SOURCE_SELECTED;

    public WiredEffectMoveFurniAsGroup(ResultSet set, Item baseItem) throws SQLException {
        super(set, baseItem);
    }

    public WiredEffectMoveFurniAsGroup(
            int id, int userId, Item item, String extradata, int limitedStack, int limitedSells) {
        super(id, userId, item, extradata, limitedStack, limitedSells);
    }

    @Override
    public boolean saveData(WiredSettings settings, GameClient gameClient) throws WiredSaveException {
        settings = WiredNativeMovementSettings.normalize(this, settings);
        Room room = this.getRoom();
        if (room == null) return false;

        int[] p = settings.getIntParams();
        if (p.length >= 6) {
            if ((p[0] != 0 && p[0] != 1)
                    || p[1] < -64
                    || p[1] > 64
                    || p[2] < -64
                    || p[2] > 64
                    || settings.getDelay() < 0
                    || settings.getDelay() > Emulator.getConfig().getInt("hotel.wired.max_delay", 20)
                    || settings.getFurniIds().length
                            > Emulator.getConfig().getInt("hotel.wired.furni.selection.count", 50)
                    || settings.getFurniIds2().length
                            > Emulator.getConfig().getInt("hotel.wired.furni.selection.count", 50)) return false;
            List<HabboItem> selected = new ArrayList<>(), targets = new ArrayList<>();
            for (int id : settings.getFurniIds()) {
                var item = room.getHabboItem(id);
                if (item == null) return false;
                selected.add(item);
            }
            for (int id : settings.getFurniIds2()) {
                var item = room.getHabboItem(id);
                if (item == null) return false;
                targets.add(item);
            }
            this.items.clear();
            this.items.addAll(selected);
            this.targetItems.clear();
            this.targetItems.addAll(targets);
            this.destinationMode = true;
            this.targetUser = p[0] == 1;
            this.offsetX = p[1];
            this.offsetY = p[2];
            this.furniSource = p[3];
            this.targetSource = p[4];
            this.userSource = p[5];
            this.setDelay(settings.getDelay());
            return true;
        }

        if (settings.getIntParams().length < 2) throw new WiredSaveException("invalid data");

        this.items.clear();
        this.targetItems.clear();
        this.destinationMode = false;
        this.direction = ((settings.getIntParams()[0] % 8) + 8) % 8;
        this.furniSource = settings.getIntParams()[1];

        int count = settings.getFurniIds().length;
        if (count > 0 && this.furniSource == WiredSourceUtil.SOURCE_TRIGGER) {
            this.furniSource = WiredSourceUtil.SOURCE_SELECTED;
        }

        if (this.furniSource == WiredSourceUtil.SOURCE_SELECTED) {
            for (int i = 0; i < count; i++) {
                HabboItem item = room.getHabboItem(settings.getFurniIds()[i]);
                if (item != null) {
                    this.items.add(item);
                }
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
    public void execute(WiredContext ctx) {
        if (ctx == null) return;

        Room room = ctx.room();
        if (room == null || room.getLayout() == null) return;

        List<HabboItem> effectiveItems = new ArrayList<>(WiredMovementTargets.items(ctx, this.furniSource, this.items));
        effectiveItems.removeIf(item -> item == null || room.getHabboItem(item.getId()) != item);

        if (effectiveItems.isEmpty()) return;

        // Move the leading edge first so members don't collide with un-moved members.
        int dx = directionDeltaX(this.direction);
        int dy = directionDeltaY(this.direction);
        if (this.destinationMode) {
            RoomTile destination = null;
            if (this.targetUser) {
                for (RoomUnit unit : WiredSourceUtil.resolveUsers(ctx, this.userSource)) {
                    if (unit != null && unit.getRoom() == room && unit.getCurrentLocation() != null) {
                        destination = unit.getCurrentLocation();
                        break;
                    }
                }
            } else {
                for (HabboItem target : WiredMovementTargets.items(ctx, this.targetSource, this.targetItems)) {
                    if (target != null && room.getHabboItem(target.getId()) == target) {
                        destination = room.getLayout().getTile(target.getX(), target.getY());
                        break;
                    }
                }
            }
            if (destination == null) return;
            HabboItem anchor = effectiveItems.stream()
                    .min(Comparator.comparingInt(HabboItem::getId))
                    .orElseThrow();
            dx = destination.x + this.offsetX - anchor.getX();
            dy = destination.y + this.offsetY - anchor.getY();
        }
        final int moveX = dx, moveY = dy;
        effectiveItems.sort(Comparator.comparingInt((HabboItem i) -> i.getX() * moveX + i.getY() * moveY)
                .reversed());

        for (HabboItem item : effectiveItems) {
            RoomTile current = room.getLayout().getTile(item.getX(), item.getY());
            if (current == null) continue;

            RoomTile target = this.destinationMode
                    ? room.getLayout().getTile((short) (current.x + moveX), (short) (current.y + moveY))
                    : room.getLayout().getTileInFront(current, this.direction);
            if (target == null || !target.getAllowStack()) continue;

            WiredMoveCarryHelper.moveFurni(room, this, item, target, item.getRotation(), null, false, ctx);
        }
    }

    @Override
    public boolean simulate(WiredContext ctx, com.eu.habbo.habbohotel.wired.core.WiredSimulation simulation) {
        if (ctx == null || ctx.room() == null || ctx.room().getLayout() == null) return false;
        Room room = ctx.room();
        List<HabboItem> members = new ArrayList<>(WiredMovementTargets.items(ctx, this.furniSource, this.items));
        if (members.isEmpty()) return true;
        int dx = directionDeltaX(this.direction), dy = directionDeltaY(this.direction);
        if (this.destinationMode) {
            RoomTile destination = null;
            if (this.targetUser) {
                for (RoomUnit unit : WiredSourceUtil.resolveUsers(ctx, this.userSource)) {
                    if (unit != null && unit.getRoom() == room && unit.getCurrentLocation() != null) {
                        destination = unit.getCurrentLocation();
                        break;
                    }
                }
            } else {
                for (HabboItem item : WiredMovementTargets.items(ctx, this.targetSource, this.targetItems)) {
                    var p = simulation.getItemPosition(item);
                    destination = room.getLayout().getTile(p.x, p.y);
                    break;
                }
            }
            if (destination == null) return false;
            var anchor = simulation.getItemPosition(members.stream()
                    .min(Comparator.comparingInt(HabboItem::getId))
                    .orElseThrow());
            dx = destination.x + this.offsetX - anchor.x;
            dy = destination.y + this.offsetY - anchor.y;
        }
        final int moveX = dx, moveY = dy;
        members.sort(Comparator.comparingInt((HabboItem i) -> {
                    var p = simulation.getItemPosition(i);
                    return p.x * moveX + p.y * moveY;
                })
                .reversed());
        for (HabboItem item : members) {
            var p = simulation.getItemPosition(item);
            int x = p.x + moveX, y = p.y + moveY;
            if (x < Short.MIN_VALUE || x > Short.MAX_VALUE || y < Short.MIN_VALUE || y > Short.MAX_VALUE) return false;
            RoomTile tile = room.getLayout().getTile((short) x, (short) y);
            if (tile == null
                    || !tile.getAllowStack()
                    || !simulation.moveItem(ctx, this, item, (short) x, (short) y, p.z, p.rotation, false))
                return false;
        }
        return true;
    }

    @Deprecated
    @Override
    public boolean execute(RoomUnit roomUnit, Room room, Object[] stuff) {
        return false;
    }

    @Override
    public String getWiredData() {
        List<Integer> validIds = this.items.stream()
                .filter(item -> item != null && item.getRoomId() == this.getRoomId())
                .map(HabboItem::getId)
                .toList();

        JsonData data = new JsonData(this.direction, this.getDelay(), validIds, this.furniSource);
        data.destinationMode = this.destinationMode;
        data.targetUser = this.targetUser;
        data.offsetX = this.offsetX;
        data.offsetY = this.offsetY;
        data.targetSource = this.targetSource;
        data.userSource = this.userSource;
        data.targetItemIds = this.targetItems.stream().map(HabboItem::getId).toList();
        return WiredManager.getGson().toJson(data);
    }

    @Override
    public void serializeWiredData(ServerMessage message, Room room) {
        List<HabboItem> snapshot = new ArrayList<>(this.items);
        snapshot.removeIf(item ->
                item == null || item.getRoomId() != this.getRoomId() || room.getHabboItem(item.getId()) != item);

        message.appendBoolean(false);
        message.appendInt(WiredManager.MAXIMUM_FURNI_SELECTION);
        message.appendInt(snapshot.size());
        for (HabboItem item : snapshot) message.appendInt(item.getId());
        message.appendInt(this.getBaseItem().getSpriteId());
        message.appendInt(this.getId());
        message.appendString(
                this.destinationMode
                        ? this.targetItems.stream()
                                .filter(i -> room.getHabboItem(i.getId()) == i)
                                .map(i -> String.valueOf(i.getId()))
                                .collect(java.util.stream.Collectors.joining(";"))
                        : "");
        message.appendInt(this.destinationMode ? 6 : 2);
        if (this.destinationMode) {
            message.appendInt(this.targetUser ? 1 : 0);
            message.appendInt(this.offsetX);
            message.appendInt(this.offsetY);
            message.appendInt(this.furniSource);
            message.appendInt(this.targetSource);
            message.appendInt(this.userSource);
        } else {
            message.appendInt(this.direction);
            message.appendInt(this.furniSource);
        }
        message.appendInt(0);
        message.appendInt(this.getType().code);
        message.appendInt(this.getDelay());
        message.appendInt(0);
    }

    @Override
    public void loadWiredData(ResultSet set, Room room) throws SQLException {
        this.onPickUp();
        String wiredData = set.getString("wired_data");

        if (wiredData == null || !wiredData.startsWith("{")) return;

        JsonData data = WiredMovementPayloadGuard.fromJson(wiredData, JsonData.class);
        if (data == null) return;

        this.direction = ((data.direction % 8) + 8) % 8;
        this.setDelay(WiredMovementPayloadGuard.delay(data.delay));
        this.furniSource = WiredMovementPayloadGuard.furniSource(data.furniSource);
        this.destinationMode = data.destinationMode;
        this.targetUser = data.targetUser;
        this.offsetX = Math.max(-64, Math.min(64, data.offsetX));
        this.offsetY = Math.max(-64, Math.min(64, data.offsetY));
        this.targetSource = WiredMovementPayloadGuard.furniSource(data.targetSource);
        this.userSource = WiredMovementPayloadGuard.userSource(data.userSource);
        if (data.targetItemIds != null)
            for (Integer id : data.targetItemIds) {
                if (id == null) continue;
                var target = room.getHabboItem(id);
                if (target != null) this.targetItems.add(target);
            }

        if (data.itemIds != null) {
            for (Integer id : data.itemIds) {
                if (id == null) continue;
                HabboItem item = room.getHabboItem(id);
                if (item != null) {
                    this.items.add(item);
                }
            }
        }

        if (this.furniSource == WiredSourceUtil.SOURCE_TRIGGER && !this.items.isEmpty()) {
            this.furniSource = WiredSourceUtil.SOURCE_SELECTED;
        }
    }

    @Override
    public void onPickUp() {
        this.setDelay(0);
        this.items.clear();
        this.direction = 0;
        this.destinationMode = true;
        this.targetUser = true;
        this.offsetX = 0;
        this.offsetY = 0;
        this.targetSource = 0;
        this.userSource = 0;
        this.targetItems.clear();
        this.furniSource = WiredSourceUtil.SOURCE_SELECTED;
    }

    @Override
    protected long requiredCooldown() {
        return COOLDOWN_MOVEMENT;
    }

    private static int directionDeltaX(int direction) {
        return switch (direction) {
            case 1, 2, 3 -> 1;
            case 5, 6, 7 -> -1;
            default -> 0;
        };
    }

    private static int directionDeltaY(int direction) {
        return switch (direction) {
            case 3, 4, 5 -> 1;
            case 0, 1, 7 -> -1;
            default -> 0;
        };
    }

    static class JsonData {
        boolean destinationMode, targetUser;
        int offsetX, offsetY, targetSource, userSource;
        List<Integer> targetItemIds;
        int direction;
        int delay;
        List<Integer> itemIds;
        int furniSource;

        public JsonData(int direction, int delay, List<Integer> itemIds, int furniSource) {
            this.direction = direction;
            this.delay = delay;
            this.itemIds = itemIds;
            this.furniSource = furniSource;
        }
    }
}
