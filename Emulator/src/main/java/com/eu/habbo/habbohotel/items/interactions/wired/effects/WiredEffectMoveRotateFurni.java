package com.eu.habbo.habbohotel.items.interactions.wired.effects;

import com.eu.habbo.Emulator;
import com.eu.habbo.WiredCompatibilityDiagnostics;
import com.eu.habbo.habbohotel.gameclients.GameClient;
import com.eu.habbo.habbohotel.items.ICycleable;
import com.eu.habbo.habbohotel.items.Item;
import com.eu.habbo.habbohotel.items.interactions.InteractionWiredEffect;
import com.eu.habbo.habbohotel.items.interactions.wired.WiredNativeMovementSettings;
import com.eu.habbo.habbohotel.items.interactions.wired.WiredSettings;
import com.eu.habbo.habbohotel.rooms.FurnitureMovementError;
import com.eu.habbo.habbohotel.rooms.Room;
import com.eu.habbo.habbohotel.rooms.RoomTile;
import com.eu.habbo.habbohotel.rooms.RoomTileState;
import com.eu.habbo.habbohotel.rooms.RoomUnit;
import com.eu.habbo.habbohotel.rooms.RoomUserRotation;
import com.eu.habbo.habbohotel.users.HabboItem;
import com.eu.habbo.habbohotel.wired.WiredEffectType;
import com.eu.habbo.habbohotel.wired.core.WiredContext;
import com.eu.habbo.habbohotel.wired.core.WiredEvent;
import com.eu.habbo.habbohotel.wired.core.WiredManager;
import com.eu.habbo.habbohotel.wired.core.WiredMoveCarryHelper;
import com.eu.habbo.habbohotel.wired.core.WiredSimulation;
import com.eu.habbo.habbohotel.wired.core.WiredSourceUtil;
import com.eu.habbo.messages.ServerMessage;
import com.eu.habbo.messages.incoming.wired.WiredSaveException;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

public class WiredEffectMoveRotateFurni extends InteractionWiredEffect implements ICycleable {

    public static final WiredEffectType type = WiredEffectType.MOVE_ROTATE;
    // Use LinkedHashSet to preserve insertion order for consistent movement
    private final Set<HabboItem> items = new LinkedHashSet<>(WiredManager.MAXIMUM_FURNI_SELECTION / 2);
    private int direction;
    private int rotation;
    private int furniSource = WiredSourceUtil.SOURCE_SELECTED;
    // Use thread-safe set for cooldowns since execute() can be called from async threads.
    // For signal-per-furni chains the same effect can validly move the same selected item once per
    // forwarded source item before the next cycle. Keep those signal sources distinct instead of
    // collapsing the whole burst to one movement.
    private final Set<String> itemCooldowns = ConcurrentHashMap.newKeySet();
    // Pre-selected directions from simulation (itemId -> direction)
    private record MovementPlan(Map<Integer, RoomUserRotation> directions, Map<Integer, Integer> rotations) {}

    private final Map<WiredContext, MovementPlan> simulatedPlans =
            java.util.Collections.synchronizedMap(new java.util.WeakHashMap<>());

    public WiredEffectMoveRotateFurni(ResultSet set, Item baseItem) throws SQLException {
        super(set, baseItem);
    }

    public WiredEffectMoveRotateFurni(
            int id, int userId, Item item, String extradata, int limitedStack, int limitedSells) {
        super(id, userId, item, extradata, limitedStack, limitedSells);
    }

    @Override
    public void execute(WiredContext ctx) {
        Room room = ctx.room();
        boolean shortPeriod = ctx.eventType() == WiredEvent.Type.TIMER_REPEAT_SHORT;

        List<HabboItem> effectiveItems = WiredMovementTargets.items(ctx, this.furniSource, this.items);
        if (this.furniSource == WiredSourceUtil.SOURCE_SELECTED) {
            this.items.removeIf(item -> Emulator.getGameEnvironment()
                            .getRoomManager()
                            .getRoom(this.getRoomId())
                            .getHabboItem(item.getId())
                    == null);
        }

        MovementPlan planned = this.simulatedPlans.remove(ctx);
        for (HabboItem item : effectiveItems) {
            String cooldownKey = movementCooldownKey(ctx, item);
            if (!shortPeriod && this.itemCooldowns.contains(cooldownKey)) continue;

            Integer plannedRotation =
                    planned == null ? null : planned.rotations().get(item.getId());
            int newRotation = plannedRotation != null
                    ? plannedRotation
                    : this.rotation > 0 ? this.getNewRotation(item) : item.getRotation();
            RoomTile newLocation = room.getLayout().getTile(item.getX(), item.getY());
            RoomTile oldLocation = room.getLayout().getTile(item.getX(), item.getY());

            if (this.direction > 0) {
                // Use pre-selected direction if available, otherwise pick random
                RoomUserRotation moveDirection =
                        planned == null ? null : planned.directions().get(item.getId());
                if (moveDirection == null) {
                    moveDirection = this.getMovementDirection();
                }
                newLocation = room.getLayout().getTileInFront(oldLocation, moveDirection.getValue());
            }

            boolean slideAnimation = item.getRotation() == newRotation;

            FurnitureMovementError furniMoveTest =
                    WiredMoveCarryHelper.getMovementError(room, this, item, newLocation, newRotation, ctx);

            if (newLocation != null
                    && newLocation.state != RoomTileState.INVALID
                    && (newLocation != oldLocation || newRotation != item.getRotation())
                    && furniMoveTest == FurnitureMovementError.NONE) {
                if (WiredMoveCarryHelper.moveFurni(
                                room, this, item, newLocation, newRotation, null, !slideAnimation, ctx)
                        == FurnitureMovementError.NONE) {
                    if (!shortPeriod) this.itemCooldowns.add(cooldownKey);
                }
            }
        }
    }

    private static String movementCooldownKey(WiredContext ctx, HabboItem item) {
        int itemId = item != null ? item.getId() : 0;
        if (ctx != null && ctx.eventType() == WiredEvent.Type.SIGNAL_RECEIVED) {
            int signalSourceItemId = ctx.sourceItem().map(HabboItem::getId).orElse(0);
            if (signalSourceItemId > 0) {
                return itemId + ":signal:" + signalSourceItemId;
            }
        }
        return String.valueOf(itemId);
    }

    @Override
    public boolean simulate(WiredContext ctx, WiredSimulation simulation) {
        if (ctx == null || ctx.room() == null || ctx.room().getLayout() == null) return false;
        Room room = ctx.room();
        this.simulatedPlans.remove(ctx);
        Map<Integer, RoomUserRotation> directions = new java.util.HashMap<>();
        Map<Integer, Integer> rotations = new java.util.HashMap<>();
        for (HabboItem item : WiredMovementTargets.items(ctx, this.furniSource, this.items)) {
            var p = simulation.getItemPosition(item);
            short x = p.x, y = p.y;
            int rotation = this.rotation > 0 ? this.getNewRotation(item, p.rotation) : p.rotation;
            if (this.direction > 0) {
                RoomUserRotation direction = this.getMovementDirection();
                RoomTile from = room.getLayout().getTile(p.x, p.y);
                RoomTile target = from == null ? null : room.getLayout().getTileInFront(from, direction.getValue());
                if (target == null) return false;
                x = target.x;
                y = target.y;
                directions.put(item.getId(), direction);
            }
            if ((x != p.x || y != p.y || rotation != p.rotation)
                    && !simulation.moveItem(ctx, this, item, x, y, p.z, rotation, false)) return false;
            rotations.put(item.getId(), rotation);
        }
        this.simulatedPlans.put(ctx, new MovementPlan(directions, rotations));
        return true;
    }

    @Override
    public String getWiredData() {
        List<HabboItem> itemsToRemove = new ArrayList<>();

        Room room = Emulator.getGameEnvironment().getRoomManager().getRoom(this.getRoomId());

        for (HabboItem item : this.items) {
            if (item.getRoomId() != this.getRoomId() || (room != null && room.getHabboItem(item.getId()) == null))
                itemsToRemove.add(item);
        }

        for (HabboItem item : itemsToRemove) {
            this.items.remove(item);
        }

        return WiredManager.getGson()
                .toJson(new JsonData(
                        this.direction,
                        this.rotation,
                        this.getDelay(),
                        this.items.stream().map(HabboItem::getId).collect(Collectors.toList()),
                        this.furniSource));
    }

    @Override
    public void loadWiredData(ResultSet set, Room room) throws SQLException {
        this.onPickUp();
        try {
            this.items.clear();
            String wiredData = set.getString("wired_data");
            if (wiredData == null) return;

            if (wiredData.startsWith("{")) {
                JsonData data = WiredManager.getGson().fromJson(wiredData, JsonData.class);
                this.setDelay(data.delay);
                this.direction = data.direction;
                this.rotation = data.rotation;
                this.furniSource = data.furniSource;
                for (Integer id : data.itemIds != null ? data.itemIds : java.util.List.<Integer>of()) {
                    if (id == null) continue;
                    HabboItem item = room.getHabboItem(id);
                    if (item != null) {
                        this.items.add(item);
                    }
                }
                if (this.furniSource == WiredSourceUtil.SOURCE_TRIGGER && !this.items.isEmpty()) {
                    this.furniSource = WiredSourceUtil.SOURCE_SELECTED;
                }
            } else {
                String[] data = wiredData.split("\t");

                if (data.length == 4) {
                    try {
                        this.direction = Integer.parseInt(data[0]);
                        this.rotation = Integer.parseInt(data[1]);
                        this.setDelay(Integer.parseInt(data[2]));
                    } catch (Exception e) {
                        WiredCompatibilityDiagnostics.record(
                                WiredCompatibilityDiagnostics.FailurePoint.EFFECT_MOVE_ROTATE_LEGACY,
                                room.getId(),
                                this.getId(),
                                e);
                    }

                    for (String s : data[3].split("\r")) {
                        if (s.trim().isEmpty()) continue;
                        try {
                            HabboItem item = room.getHabboItem(Integer.parseInt(s.trim()));

                            if (item != null) this.items.add(item);
                        } catch (NumberFormatException ignored) {
                            // skip malformed furni id token
                            WiredCompatibilityDiagnostics.record(
                                    WiredCompatibilityDiagnostics.FailurePoint.EFFECT_MOVE_ROTATE_ITEM,
                                    room.getId(),
                                    this.getId(),
                                    ignored);
                        }
                    }
                }
                this.furniSource =
                        this.items.isEmpty() ? WiredSourceUtil.SOURCE_TRIGGER : WiredSourceUtil.SOURCE_SELECTED;
            }
        } catch (RuntimeException malformed) {
            this.onPickUp();
        }
    }

    @Override
    public void onPickUp() {
        this.simulatedPlans.clear();
        this.direction = 0;
        this.rotation = 0;
        this.items.clear();
        this.furniSource = WiredSourceUtil.SOURCE_SELECTED;
        this.setDelay(0);
    }

    @Override
    public WiredEffectType getType() {
        return type;
    }

    @Override
    public void serializeWiredData(ServerMessage message, Room room) {
        List<HabboItem> itemsToRemove = new ArrayList<>();

        for (HabboItem item : this.items) {
            if (item.getRoomId() != this.getRoomId()
                    || Emulator.getGameEnvironment()
                                    .getRoomManager()
                                    .getRoom(this.getRoomId())
                                    .getHabboItem(item.getId())
                            == null) itemsToRemove.add(item);
        }

        for (HabboItem item : itemsToRemove) {
            this.items.remove(item);
        }

        message.appendBoolean(false);
        message.appendInt(WiredManager.MAXIMUM_FURNI_SELECTION);
        message.appendInt(this.items.size());
        for (HabboItem item : this.items) message.appendInt(item.getId());
        message.appendInt(this.getBaseItem().getSpriteId());
        message.appendInt(this.getId());
        message.appendString("");
        message.appendInt(3);
        message.appendInt(this.direction);
        message.appendInt(this.rotation);
        message.appendInt(this.furniSource);
        message.appendInt(0);
        message.appendInt(this.getType().code);
        message.appendInt(this.getDelay());
        message.appendInt(0);
    }

    @Override
    public boolean saveData(WiredSettings settings, GameClient gameClient) throws WiredSaveException {
        settings = WiredNativeMovementSettings.normalize(this, settings);
        Room room = Emulator.getGameEnvironment().getRoomManager().getRoom(this.getRoomId());

        if (room == null) return false;

        if (settings.getIntParams().length < 3) throw new WiredSaveException("invalid data");

        this.direction = settings.getIntParams()[0];
        this.rotation = settings.getIntParams()[1];
        this.furniSource = settings.getIntParams()[2];

        int count = settings.getFurniIds().length;
        if (count > Emulator.getConfig().getInt("hotel.wired.furni.selection.count", 5)) return false;

        if (count > 0 && this.furniSource == WiredSourceUtil.SOURCE_TRIGGER) {
            this.furniSource = WiredSourceUtil.SOURCE_SELECTED;
        }

        this.items.clear();
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

    /**
     * Returns a new rotation for an item based on the wired options
     *
     * @param item HabboItem
     * @return new rotation
     */
    private int getNewRotation(HabboItem item) {
        return this.getNewRotation(item, item.getRotation());
    }

    private int getNewRotation(HabboItem item, int currentRotation) {
        if (item.getMaximumRotations() == 2) {
            return currentRotation == 0 ? 4 : 0;
        } else if (item.getMaximumRotations() == 1) {
            return currentRotation;
        } else if (item.getMaximumRotations() > 4) {
            if (this.rotation == 1) {
                return currentRotation == item.getMaximumRotations() - 1 ? 0 : currentRotation + 1;
            } else if (this.rotation == 2) {
                return currentRotation > 0 ? currentRotation - 1 : item.getMaximumRotations() - 1;
            } else if (this.rotation == 3) { // Random rotation
                Set<Integer> possibleRotations = new HashSet<>();
                for (int i = 0; i < item.getMaximumRotations(); i++) {
                    possibleRotations.add(i);
                }

                possibleRotations.remove(currentRotation);

                if (possibleRotations.size() > 0) {
                    int index = Emulator.getRandom().nextInt(possibleRotations.size());
                    Iterator<Integer> iter = possibleRotations.iterator();
                    for (int i = 0; i < index; i++) {
                        iter.next();
                    }
                    return iter.next();
                }
            }
        } else {
            if (this.rotation == 1) {
                return (currentRotation + 2) % 8;
            } else if (this.rotation == 2) {
                int rot = (currentRotation - 2) % 8;
                if (rot < 0) {
                    rot += 8;
                }
                return rot;
            } else if (this.rotation == 3) { // Random rotation
                Set<Integer> possibleRotations = new HashSet<>();
                for (int i = 0; i < item.getMaximumRotations(); i++) {
                    possibleRotations.add(i * 2);
                }

                possibleRotations.remove(currentRotation);

                if (possibleRotations.size() > 0) {
                    int index = Emulator.getRandom().nextInt(possibleRotations.size());
                    Iterator<Integer> iter = possibleRotations.iterator();
                    for (int i = 0; i < index; i++) {
                        iter.next();
                    }
                    return iter.next();
                }
            }
        }

        return currentRotation;
    }

    /**
     * Returns the direction of movement based on the wired settings
     *
     * @return direction
     */
    private RoomUserRotation getMovementDirection() {
        switch (this.direction) {
            case 1:
                return RoomUserRotation.values()[
                        Emulator.getRandom().nextInt(RoomUserRotation.values().length / 2) * 2];
            case 2:
                return Emulator.getRandom().nextInt(2) == 1 ? RoomUserRotation.EAST : RoomUserRotation.WEST;
            case 3:
                return Emulator.getRandom().nextInt(2) == 1 ? RoomUserRotation.NORTH : RoomUserRotation.SOUTH;
            case 4:
                return RoomUserRotation.NORTH;
            case 5:
                return RoomUserRotation.EAST;
            case 6:
                return RoomUserRotation.SOUTH;
            case 7:
                return RoomUserRotation.WEST;
            case 8:
                return RoomUserRotation.NORTH_EAST;
            case 9:
                return RoomUserRotation.SOUTH_EAST;
            case 10:
                return RoomUserRotation.SOUTH_WEST;
            case 11:
                return RoomUserRotation.NORTH_WEST;
            default:
                return RoomUserRotation.NORTH;
        }
    }

    @Override
    public void cycle(Room room) {
        this.itemCooldowns.clear();
        this.simulatedPlans.clear();
    }

    @Override
    public boolean execute(RoomUnit roomUnit, Room room, Object[] stuff) {
        return false;
    }

    static class JsonData {
        int direction;
        int rotation;
        int delay;
        List<Integer> itemIds;
        int furniSource;

        public JsonData(int direction, int rotation, int delay, List<Integer> itemIds, int furniSource) {
            this.direction = direction;
            this.rotation = rotation;
            this.delay = delay;
            this.itemIds = itemIds;
            this.furniSource = furniSource;
        }
    }
}
