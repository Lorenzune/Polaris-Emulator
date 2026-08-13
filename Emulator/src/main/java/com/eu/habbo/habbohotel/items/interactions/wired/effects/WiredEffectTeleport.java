package com.eu.habbo.habbohotel.items.interactions.wired.effects;

import com.eu.habbo.Emulator;
import com.eu.habbo.habbohotel.gameclients.GameClient;
import com.eu.habbo.habbohotel.items.Item;
import com.eu.habbo.habbohotel.items.interactions.InteractionWiredEffect;
import com.eu.habbo.habbohotel.items.interactions.InteractionWiredTrigger;
import com.eu.habbo.habbohotel.items.interactions.wired.WiredLegacyDataGuard;
import com.eu.habbo.habbohotel.items.interactions.wired.WiredSettings;
import com.eu.habbo.habbohotel.pets.RideablePet;
import com.eu.habbo.habbohotel.rooms.*;
import com.eu.habbo.habbohotel.users.Habbo;
import com.eu.habbo.habbohotel.users.HabboItem;
import com.eu.habbo.habbohotel.wired.WiredEffectType;
import com.eu.habbo.habbohotel.wired.core.WiredContext;
import com.eu.habbo.habbohotel.wired.core.WiredEvent;
import com.eu.habbo.habbohotel.wired.core.WiredManager;
import com.eu.habbo.habbohotel.wired.core.WiredSourceUtil;
import com.eu.habbo.messages.ServerMessage;
import com.eu.habbo.messages.incoming.wired.WiredSaveException;
import com.eu.habbo.messages.outgoing.rooms.users.RoomUserEffectComposer;
import com.eu.habbo.messages.outgoing.rooms.users.RoomUserStatusComposer;
import com.eu.habbo.threading.runnables.RoomUnitTeleport;
import com.eu.habbo.threading.runnables.SendRoomUnitEffectComposer;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Collectors;

public class WiredEffectTeleport extends InteractionWiredEffect {
    public static final WiredEffectType type = WiredEffectType.TELEPORT;
    private static final long SIGNAL_TELEPORT_BATCH_DELAY_MS = 1L;
    private static final ConcurrentHashMap<String, PendingSignalTeleport> PENDING_SIGNAL_TELEPORTS = new ConcurrentHashMap<>();

    protected List<HabboItem> items;
    private boolean fastTeleport = false;
    private int furniSource = WiredSourceUtil.SOURCE_SELECTED;
    private int userSource = WiredSourceUtil.SOURCE_TRIGGER;

    public WiredEffectTeleport(ResultSet set, Item baseItem) throws SQLException {
        super(set, baseItem);
        this.items = new ArrayList<>();
    }

    public WiredEffectTeleport(int id, int userId, Item item, String extradata, int limitedStack, int limitedSells) {
        super(id, userId, item, extradata, limitedStack, limitedSells);
        this.items = new ArrayList<>();
    }

    public static void teleportUnitToTile(RoomUnit roomUnit, RoomTile tile) {
        teleportUnitToTile(roomUnit, tile, false);
    }

    public static void teleportUnitToTile(RoomUnit roomUnit, RoomTile tile, boolean fastTeleport) {
        if (roomUnit == null || tile == null || roomUnit.isWiredTeleporting)
            return;

        Room room = roomUnit.getRoom();

        if (room == null) {
            return;
        }

        // If this is a rider, sync the riding pet to the rider's current position immediately
        // Both will teleport together when the delay fires
        if (roomUnit.getRoomUnitType() == RoomUnitType.USER) {
            Habbo habbo = room.getHabbo(roomUnit);
            if (habbo != null && habbo.getHabboInfo() != null && habbo.getHabboInfo().getRiding() != null) {
                RideablePet ridingPet = habbo.getHabboInfo().getRiding();
                RoomUnit petUnit = ridingPet.getRoomUnit();
                if (petUnit != null) {
                    // Sync pet to rider's current position
                    RoomTile riderTile = roomUnit.getCurrentLocation();
                    petUnit.setLocation(riderTile);
                    petUnit.setZ(roomUnit.getZ() - 1.0);
                    petUnit.setPreviousLocation(riderTile);
                    petUnit.setGoalLocation(riderTile);
                    petUnit.removeStatus(RoomUnitStatus.MOVE);
                    petUnit.setCanWalk(false);
                    room.sendComposer(new RoomUserStatusComposer(petUnit).compose());
                }
            }
        }

        // makes a temporary effect

        int teleportDelay = getTeleportDelay(fastTeleport);

        roomUnit.getRoom().unIdle(roomUnit.getRoom().getHabbo(roomUnit));
        sendTeleportEffect(room, roomUnit, fastTeleport);

        if (tile == roomUnit.getCurrentLocation()) {
            return;
        }

        if (tile.state == RoomTileState.INVALID || tile.state == RoomTileState.BLOCKED) {
            RoomTile alternativeTile = null;
            List<RoomTile> optionalTiles = room.getLayout().getTilesAround(tile);

            Collections.reverse(optionalTiles);
            for (RoomTile optionalTile : optionalTiles) {
                if (optionalTile.state != RoomTileState.INVALID && optionalTile.state != RoomTileState.BLOCKED) {
                    alternativeTile = optionalTile;
                    break;
                }
            }

            if (alternativeTile != null) {
                tile = alternativeTile;
            }
        }

        Emulator.getThreading().run(() -> { roomUnit.isWiredTeleporting = true; }, Math.max(0, teleportDelay - 500));
        Emulator.getThreading().run(new RoomUnitTeleport(roomUnit, room, tile.x, tile.y, tile.getStackHeight() + (tile.state == RoomTileState.SIT ? -0.5 : 0), roomUnit.getEffectId()), teleportDelay);
    }

    @Override
    public void serializeWiredData(ServerMessage message, Room room) {
        List<HabboItem> itemsSnapshot = new ArrayList<>(this.items);
        Set<HabboItem> items = new HashSet<>();

        for (HabboItem item : itemsSnapshot) {
            if (item.getRoomId() != this.getRoomId() || Emulator.getGameEnvironment().getRoomManager().getRoom(this.getRoomId()).getHabboItem(item.getId()) == null)
                items.add(item);
        }

        for (HabboItem item : items) {
            this.items.remove(item);
        }
        itemsSnapshot = new ArrayList<>(this.items);
        message.appendBoolean(false);
        message.appendInt(WiredManager.MAXIMUM_FURNI_SELECTION);
        message.appendInt(itemsSnapshot.size());
        for (HabboItem item : itemsSnapshot)
            message.appendInt(item.getId());

        message.appendInt(this.getBaseItem().getSpriteId());
        message.appendInt(this.getId());
        message.appendString("");
        message.appendInt(3);
        message.appendInt(this.fastTeleport ? 1 : 0);
        message.appendInt(this.furniSource);
        message.appendInt(this.userSource);
        message.appendInt(0);
        message.appendInt(this.getType().code);
        message.appendInt(this.getDelay());
        if (this.requiresTriggeringUser()) {
            List<Integer> invalidTriggers = new ArrayList<>();
            for (InteractionWiredTrigger object : room.getRoomSpecialTypes().getTriggers(this.getX(), this.getY())) {
                if (!object.isTriggeredByRoomUnit()) {
                    invalidTriggers.add(object.getBaseItem().getSpriteId());
                }
            }
            message.appendInt(invalidTriggers.size());
            for (Integer i : invalidTriggers) {
                message.appendInt(i);
            }
        } else {
            message.appendInt(0);
        }
    }

    @Override
    public boolean saveData(WiredSettings settings, GameClient gameClient) throws WiredSaveException {
        int[] params = settings.getIntParams();
        if (params.length > 2) {
            this.fastTeleport = params[0] == 1;
            this.furniSource = params[1];
            this.userSource = params[2];
        } else {
            this.fastTeleport = false;
            this.furniSource = (params.length > 0) ? params[0] : WiredSourceUtil.SOURCE_TRIGGER;
            this.userSource = (params.length > 1) ? params[1] : WiredSourceUtil.SOURCE_TRIGGER;
        }

        int itemsCount = settings.getFurniIds().length;

        if(itemsCount > Emulator.getConfig().getInt("hotel.wired.furni.selection.count")) {
            throw new WiredSaveException("Too many furni selected");
        }

        if (itemsCount > 0 && this.furniSource == WiredSourceUtil.SOURCE_TRIGGER) {
            this.furniSource = WiredSourceUtil.SOURCE_SELECTED;
        }

        List<HabboItem> newItems = new ArrayList<>();

        if (this.furniSource == WiredSourceUtil.SOURCE_SELECTED) {
            for (int i = 0; i < itemsCount; i++) {
                int itemId = settings.getFurniIds()[i];
                HabboItem it = Emulator.getGameEnvironment().getRoomManager().getRoom(this.getRoomId()).getHabboItem(itemId);

                if(it == null)
                    throw new WiredSaveException(String.format("Item %s not found", itemId));

                newItems.add(it);
            }
        }

        int delay = settings.getDelay();

        if(delay > Emulator.getConfig().getInt("hotel.wired.max_delay", 20))
            throw new WiredSaveException("Delay too long");

        this.items.clear();
        if (this.furniSource == WiredSourceUtil.SOURCE_SELECTED) {
            this.items.addAll(newItems);
        }
        this.setDelay(delay);

        return true;
    }

    @Override
    public void execute(WiredContext ctx) {
        Room room = ctx.room();

        if (room == null || room.getLayout() == null) {
            return;
        }

        List<HabboItem> effectiveItems = WiredSourceUtil.resolveItems(ctx, this.furniSource, this.items);
        if (this.furniSource == WiredSourceUtil.SOURCE_SELECTED) {
            this.items.removeIf(item -> item == null || item.getRoomId() != this.getRoomId()
                    || Emulator.getGameEnvironment().getRoomManager().getRoom(this.getRoomId()).getHabboItem(item.getId()) == null);
        }

        if (effectiveItems.isEmpty()) return;

        for (RoomUnit roomUnit : WiredSourceUtil.resolveUsers(ctx, this.userSource)) {
            int i = Emulator.getRandom().nextInt(effectiveItems.size());
            HabboItem item = effectiveItems.get(i);

            if (item == null) continue;

            RoomTile tile = room.getLayout().getTile(item.getX(), item.getY());
            if (tile != null) {
                if (shouldBatchSignalTeleport(ctx)) {
                    queueSignalTeleport(ctx, roomUnit, tile);
                } else {
                    teleportUnitToTile(roomUnit, tile, this.fastTeleport);
                }
            }
        }
    }

    private boolean shouldBatchSignalTeleport(WiredContext ctx) {
        return ctx != null
                && this.furniSource == WiredSourceUtil.SOURCE_SIGNAL
                && ctx.eventType() == WiredEvent.Type.SIGNAL_RECEIVED
                && ctx.event().isSignalPerFurni()
                && ctx.event().getSignalFurniCount() > 1;
    }

    private void queueSignalTeleport(WiredContext ctx, RoomUnit roomUnit, RoomTile tile) {
        if (roomUnit == null || tile == null) {
            return;
        }

        String key = buildSignalTeleportBatchKey(ctx, roomUnit);
        PendingSignalTeleport pending = PENDING_SIGNAL_TELEPORTS.computeIfAbsent(key,
                ignored -> new PendingSignalTeleport(roomUnit, this.fastTeleport));

        pending.add(tile);

        if (pending.markScheduled()) {
            Emulator.getThreading().run(() -> flushSignalTeleport(key), SIGNAL_TELEPORT_BATCH_DELAY_MS);
        }
    }

    private String buildSignalTeleportBatchKey(WiredContext ctx, RoomUnit roomUnit) {
        return ctx.room().getId()
                + ":" + this.getId()
                + ":" + roomUnit.getId()
                + ":" + ctx.event().getCreatedAtMs()
                + ":" + ctx.event().getSignalChannel()
                + ":" + ctx.event().getCallStackDepth();
    }

    private static void flushSignalTeleport(String key) {
        PendingSignalTeleport pending = PENDING_SIGNAL_TELEPORTS.remove(key);
        if (pending == null) {
            return;
        }

        RoomTile tile = pending.pickTile();
        if (tile != null) {
            teleportUnitToTile(pending.roomUnit, tile, pending.fastTeleport);
        }
    }

    @Deprecated
    @Override
    public boolean execute(RoomUnit roomUnit, Room room, Object[] stuff) {
        return false;
    }

    @Override
    public String getWiredData() {
        List<HabboItem> itemsSnapshot = new ArrayList<>(this.items);
        return WiredManager.getGson().toJson(new JsonData(
            this.getDelay(),
            itemsSnapshot.stream().map(HabboItem::getId).collect(Collectors.toList()),
            this.fastTeleport,
            this.furniSource,
            this.userSource
        ));
    }

    @Override
    public void loadWiredData(ResultSet set, Room room) throws SQLException {
        this.items = new ArrayList<>();
        String wiredData = set.getString("wired_data");

        JsonData jsonData = WiredMovementPayloadGuard.fromJson(wiredData, JsonData.class);
        if (jsonData != null) {
            this.setDelay(WiredMovementPayloadGuard.delay(jsonData.delay));
            this.fastTeleport = jsonData.fastTeleport;
            this.furniSource = WiredMovementPayloadGuard.furniSource(jsonData.furniSource);
            this.userSource = WiredMovementPayloadGuard.userSource(jsonData.userSource);
            if (jsonData.itemIds != null) {
                for (Integer id: jsonData.itemIds) {
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
        } else {
            String[] wiredDataOld = wiredData != null ? wiredData.split("\t") : new String[0];

            if (wiredDataOld.length >= 1) {
                this.setDelay(WiredLegacyDataGuard.parseDelay(wiredDataOld[0]));
            }
            if (wiredDataOld.length == 2) {
                if (wiredDataOld[1].contains(";")) {
                    this.items.addAll(WiredLegacyDataGuard.parseRoomItems(wiredDataOld[1], room));
                }
            }
            this.fastTeleport = false;
            this.furniSource = this.items.isEmpty() ? WiredSourceUtil.SOURCE_TRIGGER : WiredSourceUtil.SOURCE_SELECTED;
            this.userSource = WiredSourceUtil.SOURCE_TRIGGER;
        }
    }

    @Override
    public void onPickUp() {
        this.items.clear();
        this.fastTeleport = false;
        this.furniSource = WiredSourceUtil.SOURCE_SELECTED;
        this.userSource = WiredSourceUtil.SOURCE_TRIGGER;
        this.setDelay(0);
    }

    @Override
    public WiredEffectType getType() {
        return type;
    }

    @Override
    public boolean requiresTriggeringUser() {
        return this.userSource == WiredSourceUtil.SOURCE_TRIGGER;
    }

    @Override
    protected long requiredCooldown() {
        return COOLDOWN_DEFAULT;
    }

    private static int getTeleportDelay(boolean fastTeleport) {
        // The SWF's fast teleport option is the short variant of the normal
        // teleport, not an almost-instant 1/5 delay. Keep both timings tied to
        // the configured normal duration so the checkbox remains predictable.
        return fastTeleport
                ? Math.max(1, WiredManager.TELEPORT_DELAY / 2)
                : WiredManager.TELEPORT_DELAY;
    }

    private static int getTeleportEffectDuration(boolean fastTeleport) {
        int normalDuration = WiredManager.TELEPORT_DELAY + 1000;
        return fastTeleport ? Math.max(300, normalDuration / 2) : normalDuration;
    }

    private static void sendTeleportEffect(Room room, RoomUnit roomUnit, boolean fastTeleport) {
        room.sendComposer(new RoomUserEffectComposer(roomUnit, 4).compose());
        Emulator.getThreading().run(new SendRoomUnitEffectComposer(room, roomUnit), getTeleportEffectDuration(fastTeleport));
    }

    static class JsonData {
        int delay;
        List<Integer> itemIds;
        boolean fastTeleport;
        int furniSource;
        int userSource;

        public JsonData(int delay, List<Integer> itemIds, boolean fastTeleport, int furniSource, int userSource) {
            this.delay = delay;
            this.itemIds = itemIds;
            this.fastTeleport = fastTeleport;
            this.furniSource = furniSource;
            this.userSource = userSource;
        }
    }

    private static final class PendingSignalTeleport {
        private final RoomUnit roomUnit;
        private final boolean fastTeleport;
        private final AtomicBoolean scheduled = new AtomicBoolean(false);
        private final List<RoomTile> candidateTiles = new ArrayList<>();

        private PendingSignalTeleport(RoomUnit roomUnit, boolean fastTeleport) {
            this.roomUnit = roomUnit;
            this.fastTeleport = fastTeleport;
        }

        private synchronized void add(RoomTile tile) {
            this.candidateTiles.add(tile);
        }

        private boolean markScheduled() {
            return this.scheduled.compareAndSet(false, true);
        }

        private synchronized RoomTile pickTile() {
            if (this.candidateTiles.isEmpty()) {
                return null;
            }

            return this.candidateTiles.get(Emulator.getRandom().nextInt(this.candidateTiles.size()));
        }
    }
}
