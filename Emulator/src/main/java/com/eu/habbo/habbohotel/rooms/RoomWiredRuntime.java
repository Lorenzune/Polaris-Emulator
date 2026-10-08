package com.eu.habbo.habbohotel.rooms;

import com.eu.habbo.habbohotel.items.interactions.InteractionWired;
import com.eu.habbo.habbohotel.users.HabboItem;
import java.sql.SQLException;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicLong;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Room-owned WIRED runtime state. */
public final class RoomWiredRuntime {
    private static final Logger LOGGER = LoggerFactory.getLogger(RoomWiredRuntime.class);

    private final Room room;
    private final com.eu.habbo.habbohotel.wired.core.WiredInternalVariableMonitor variableMonitor =
            new com.eu.habbo.habbohotel.wired.core.WiredInternalVariableMonitor();

    public com.eu.habbo.habbohotel.wired.core.WiredInternalVariableMonitor getVariableMonitor() {
        return variableMonitor;
    }

    private final AtomicLong cacheGeneration = new AtomicLong();
    private final WiredGravityService gravity;
    private final WiredOpacityService opacity;
    private final RoomOpacityPersistence visualStatePersistence;

    RoomWiredRuntime(Room room, RoomDependencies.ConnectionProvider database, Executor persistenceExecutor) {
        this.room = room;
        this.gravity = new WiredGravityService(room);
        this.opacity = new WiredOpacityService(room);
        this.visualStatePersistence = new RoomOpacityPersistence(room.getId(), database, persistenceExecutor);
    }

    long cacheGeneration() {
        return this.cacheGeneration.get();
    }

    long advanceCacheGeneration() {
        return this.cacheGeneration.incrementAndGet();
    }

    public boolean setGravityEnabled(HabboItem item, boolean enabled) {
        return this.gravity.setEnabled(item, enabled);
    }

    RollbackSnapshot createRollbackSnapshot(Set<Integer> itemIds) {
        return new RollbackSnapshot(this.gravity.createSnapshot(itemIds), this.opacity.createSnapshot(itemIds));
    }

    void restoreRollbackSnapshot(RollbackSnapshot snapshot, Set<Integer> itemIds) {
        if (snapshot == null) {
            return;
        }

        this.gravity.restoreSnapshot(snapshot.gravity(), itemIds);
        Set<Integer> changedOpacityItems = this.opacity.restoreSnapshot(snapshot.opacity(), itemIds);
        this.visualStatePersistence.save(this.opacity.globalStates(changedOpacityItems));
        WiredOpacityBroadcaster.broadcastEffective(this.room, changedOpacityItems, 0, 0);
    }

    public boolean isGravityEnabled(HabboItem item) {
        return this.gravity.isEnabled(item);
    }

    /** Schedule a projectile sample through the room-owned runtime. */
    public void scheduleProjectileUpdate(Runnable sample, int delayMs) {
        this.room.threading().run(sample, Math.max(1, delayMs));
    }

    public void markFurnitureMoving(HabboItem item, int durationMs) {
        this.gravity.markMoving(item, durationMs);
    }

    void onFurnitureTopologyChanged(HabboItem item, boolean moved) {
        if (moved) {
            com.eu.habbo.habbohotel.wired.core.WiredProjectileSupport.cancelItem(this.room, item);
            if (item instanceof com.eu.habbo.habbohotel.items.interactions.wired.extra.WiredExtraProjectile projectile)
                com.eu.habbo.habbohotel.wired.core.WiredProjectileSupport.cancelExtra(projectile);
        }
        if (moved && item instanceof InteractionWired) {
            advanceCacheGeneration();
        }
        this.gravity.onTopologyChanged();
    }

    void forgetGravity(HabboItem item) {
        this.gravity.forget(item);
    }

    public List<WiredOpacityState> applyGlobalOpacity(Collection<HabboItem> items, int opacity, boolean clickThrough) {
        Map<Integer, WiredOpacityState> previous = this.opacitySnapshot(items);
        List<WiredOpacityState> applied = this.opacity.applyGlobal(items, opacity, clickThrough);
        this.persistOpacityChanges(previous, applied);
        return applied;
    }

    public List<WiredOpacityState> applyUserOpacity(
            int userId, Collection<HabboItem> items, int opacity, boolean clickThrough) {
        return this.opacity.applyUser(userId, items, opacity, clickThrough);
    }

    public List<WiredOpacityState> effectiveOpacity(int userId, Collection<Integer> itemIds) {
        return this.opacity.effective(userId, itemIds);
    }

    public List<WiredOpacityState> opacitySnapshot(int userId) {
        return this.opacity.snapshot(userId);
    }

    /** Room-wide opacity for one item, ignoring per-user overlays. Used by the {@code @opacity} variable. */
    public int globalOpacity(HabboItem item) {
        return item == null ? 100 : this.opacity.globalOpacity(item.getId());
    }

    /** Room-wide visual scale percentage, where 100 keeps the authored size. */
    public int globalScaling(HabboItem item) {
        return item == null ? 100 : this.opacity.globalScaling(item.getId());
    }

    /**
     * Sets the room-wide opacity for one item and pushes it to every client that announced opacity
     * support. Click-through is preserved, so a variable write only changes how transparent the item
     * is.
     */
    public boolean setGlobalOpacity(Room room, HabboItem item, int opacity) {
        return room != null
                && item != null
                && !this.setGlobalOpacityForItems(room, List.of(item), opacity).isEmpty();
    }

    /** Sets and persists a room-wide furniture visual scale percentage. */
    public boolean setGlobalScaling(Room room, HabboItem item, int scaling) {
        if (room != this.room || item == null || scaling < 25 || scaling > 300) {
            return false;
        }

        scaling = WiredFurnitureScaling.normalize(scaling);
        Map<Integer, WiredOpacityState> previous = this.opacitySnapshot(List.of(item));
        List<WiredOpacityState> applied = this.opacity.applyGlobalScaling(List.of(item), scaling);
        this.persistOpacityChanges(previous, applied);
        WiredOpacityBroadcaster.broadcast(room, applied, 0, 0);
        return !applied.isEmpty();
    }

    /** Sets and persists one visual scale level for every item in a builder selection. */
    public List<WiredOpacityState> setGlobalScalingForItems(Room room, Collection<HabboItem> items, int scaling) {
        if (room != this.room || items == null || items.isEmpty() || scaling < 25 || scaling > 300) {
            return List.of();
        }

        int normalizedScaling = WiredFurnitureScaling.normalize(scaling);
        Map<Integer, WiredOpacityState> previous = this.opacitySnapshot(items);
        List<WiredOpacityState> applied = this.opacity.applyGlobalScaling(items, normalizedScaling);
        if (applied.isEmpty()) {
            return List.of();
        }

        this.persistOpacityChanges(previous, applied);
        WiredOpacityBroadcaster.broadcast(room, applied, 0, 0);
        return applied;
    }

    /** Persistently updates opacity for a selection while retaining each item's click-through state. */
    public List<WiredOpacityState> setGlobalOpacityForItems(Room room, Collection<HabboItem> items, int opacity) {
        if (room != this.room || items == null || items.isEmpty()) {
            return List.of();
        }

        Map<Integer, WiredOpacityState> previous = this.opacitySnapshot(items);
        List<WiredOpacityState> applied = this.opacity.applyGlobalOpacity(items, opacity);
        if (applied.isEmpty()) {
            return List.of();
        }

        this.persistOpacityChanges(previous, applied);
        WiredOpacityBroadcaster.broadcast(room, applied, 0, 0);
        return applied;
    }

    /** Stores and broadcasts a per-layer color configuration for a selection of floor furniture. */
    public List<WiredOpacityState> setGlobalCustomColorForItems(
            Room room, Collection<HabboItem> items, String customColor) {
        if (room != this.room || items == null || items.isEmpty()) {
            return List.of();
        }

        final String normalized;
        try {
            normalized = WiredFurnitureCustomColor.normalize(customColor);
        } catch (IllegalArgumentException exception) {
            return List.of();
        }

        Map<Integer, WiredOpacityState> previous = this.opacitySnapshot(items);
        List<WiredOpacityState> applied = this.opacity.applyGlobalCustomColor(items, normalized);
        this.persistOpacityChanges(previous, applied);
        // Echo the accepted effective value for every requested item. This corrects an optimistic
        // local preview if the room's bounded visual-state budget rejects part of a large selection.
        WiredFurniCustomColorBroadcaster.broadcast(
                room, this.opacitySnapshot(items).values());
        return applied;
    }

    private Map<Integer, WiredOpacityState> opacitySnapshot(Collection<HabboItem> items) {
        if (items == null || items.isEmpty()) {
            return Map.of();
        }

        List<Integer> itemIds = items.stream()
                .filter(java.util.Objects::nonNull)
                .map(HabboItem::getId)
                .distinct()
                .toList();
        return this.opacity.globalStates(itemIds).stream()
                .collect(java.util.stream.Collectors.toMap(WiredOpacityState::itemId, state -> state));
    }

    private void persistOpacityChanges(Map<Integer, WiredOpacityState> previous, List<WiredOpacityState> applied) {
        if (applied == null || applied.isEmpty()) {
            return;
        }

        this.visualStatePersistence.save(applied.stream()
                .filter(state -> !state.equals(previous.get(state.itemId())))
                .toList());
    }

    /** Restores persisted room-wide opacity and scale after the room's furniture has loaded. */
    void loadPersistentVisualState() {
        Map<Integer, RoomOpacityPersistence.PersistedState> persisted;
        try {
            persisted = this.visualStatePersistence.load();
        } catch (SQLException exception) {
            LOGGER.error("Unable to load furniture visual state for room {}", this.room.getId(), exception);
            return;
        }

        Map<RoomOpacityPersistence.PersistedState, java.util.ArrayList<HabboItem>> grouped = new HashMap<>();
        for (Map.Entry<Integer, RoomOpacityPersistence.PersistedState> entry : persisted.entrySet()) {
            HabboItem item = this.room.getHabboItem(entry.getKey());
            if (item == null || item.getRoomId() != this.room.getId()) {
                continue;
            }
            grouped.computeIfAbsent(entry.getValue(), ignored -> new java.util.ArrayList<>())
                    .add(item);
        }

        for (Map.Entry<RoomOpacityPersistence.PersistedState, java.util.ArrayList<HabboItem>> entry :
                grouped.entrySet()) {
            RoomOpacityPersistence.PersistedState state = entry.getKey();
            this.opacity.applyGlobal(entry.getValue(), state.opacity(), false, state.scaling());
            if (state.customColor() != null) {
                this.opacity.applyGlobalCustomColor(entry.getValue(), state.customColor());
            }
        }
    }

    public List<WiredOpacityState> customColorSnapshot() {
        return this.opacity.customColorSnapshot();
    }

    void forgetOpacity(HabboItem item) {
        this.opacity.forgetItem(item);
    }

    void forgetOpacityUser(int userId) {
        this.opacity.forgetUser(userId);
    }

    void dispose() {
        variableMonitor.clear();
        this.gravity.dispose();
        this.opacity.dispose();
    }

    void resetForRoomLoad() {
        this.gravity.dispose();
        this.opacity.resetForRoomLoad();
    }

    record RollbackSnapshot(WiredGravityService.Snapshot gravity, WiredOpacityService.Snapshot opacity) {}
}
