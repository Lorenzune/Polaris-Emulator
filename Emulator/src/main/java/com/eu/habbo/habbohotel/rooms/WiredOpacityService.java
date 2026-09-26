package com.eu.habbo.habbohotel.rooms;

import com.eu.habbo.WiredPlatform;
import com.eu.habbo.core.ConfigurationManager;
import com.eu.habbo.habbohotel.items.FurnitureType;
import com.eu.habbo.habbohotel.users.HabboItem;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Room-owned bounded state for global furniture visuals and per-user opacity. */
final class WiredOpacityService {
    static final int DEFAULT_MAXIMUM_STATES = 5_000;
    static final int ABSOLUTE_MAXIMUM_STATES = 20_000;

    private static final Logger LOGGER = LoggerFactory.getLogger(WiredOpacityService.class);

    private final Room room;
    private final int maximumStates;
    private final Map<Integer, VisualState> globalStates = new HashMap<>();
    private final Map<Integer, Map<Integer, VisualState>> userStates = new HashMap<>();
    private int stateCount;
    private int customColorBytes;
    private boolean disposed;

    WiredOpacityService(Room room) {
        this(room, configuredMaximumStates());
    }

    WiredOpacityService(Room room, int maximumStates) {
        this.room = room;
        this.maximumStates = Math.max(1, Math.min(ABSOLUTE_MAXIMUM_STATES, maximumStates));
    }

    synchronized List<WiredOpacityState> applyGlobal(Collection<HabboItem> items, int opacity, boolean clickThrough) {
        return this.applyGlobal(items, opacity, clickThrough, null);
    }

    synchronized List<WiredOpacityState> applyGlobal(
            Collection<HabboItem> items, int opacity, boolean clickThrough, Integer scaling) {
        if (this.disposed) {
            return List.of();
        }

        ArrayList<WiredOpacityState> applied = new ArrayList<>();
        for (HabboItem item : validItems(items)) {
            VisualState previous = this.globalStates.get(item.getId());
            VisualState next = new VisualState(
                    opacity, clickThrough, scaling == null ? (previous == null ? 100 : previous.scaling()) : scaling,
                    previous == null ? null : previous.customColor());
            if (next.isDefault()) {
                if (previous != null) {
                    this.globalStates.remove(item.getId());
                    this.stateCount--;
                }
                applied.add(state(item, next));
            } else if (previous != null || reserveState(item.getId())) {
                this.globalStates.put(item.getId(), next);
                applied.add(state(item, next));
            }
        }
        return List.copyOf(applied);
    }

    /** Sets room-wide opacity while retaining each furniture item's existing click-through setting. */
    synchronized List<WiredOpacityState> applyGlobalOpacity(Collection<HabboItem> items, int opacity) {
        if (this.disposed) {
            return List.of();
        }

        ArrayList<WiredOpacityState> applied = new ArrayList<>();
        for (HabboItem item : validItems(items)) {
            VisualState previous = this.globalStates.get(item.getId());
            boolean clickThrough = previous != null && previous.clickThrough();
            VisualState next = new VisualState(opacity, clickThrough, previous == null ? 100 : previous.scaling(),
                    previous == null ? null : previous.customColor());
            if (next.isDefault()) {
                if (previous != null) {
                    this.globalStates.remove(item.getId());
                    this.stateCount--;
                }
                applied.add(state(item, next));
            } else if (previous != null || reserveState(item.getId())) {
                this.globalStates.put(item.getId(), next);
                applied.add(state(item, next));
            }
        }
        return List.copyOf(applied);
    }

    synchronized List<WiredOpacityState> applyUser(
            int userId, Collection<HabboItem> items, int opacity, boolean clickThrough) {
        if (userId <= 0 || this.disposed) {
            return List.of();
        }

        VisualState next = new VisualState(opacity, clickThrough, 100);
        Map<Integer, VisualState> states = this.userStates.computeIfAbsent(userId, ignored -> new HashMap<>());
        ArrayList<WiredOpacityState> applied = new ArrayList<>();
        for (HabboItem item : validItems(items)) {
            if (states.containsKey(item.getId()) || reserveState(item.getId())) {
                states.put(item.getId(), next);
                applied.add(state(item, next));
            }
        }
        if (states.isEmpty()) {
            this.userStates.remove(userId);
        }
        return List.copyOf(applied);
    }

    synchronized List<WiredOpacityState> effective(int userId, Collection<Integer> itemIds) {
        if (this.disposed || itemIds == null || itemIds.isEmpty()) {
            return List.of();
        }

        Map<Integer, VisualState> personal = this.userStates.get(userId);
        ArrayList<WiredOpacityState> result = new ArrayList<>();
        Set<Integer> seen = new HashSet<>();
        for (Integer itemId : itemIds) {
            if (itemId == null || !seen.add(itemId)) {
                continue;
            }
            HabboItem item = this.room.getHabboItem(itemId);
            if (!isCurrentRoomItem(item)) {
                continue;
            }
            VisualState global = this.globalStates.getOrDefault(itemId, VisualState.DEFAULT);
            VisualState state = personal == null ? null : personal.get(itemId);
            result.add(state(item, state == null
                    ? global
                    : new VisualState(state.opacity(), state.clickThrough(), global.scaling(), global.customColor())));
        }
        return List.copyOf(result);
    }

    synchronized List<WiredOpacityState> snapshot(int userId) {
        if (this.disposed) {
            return List.of();
        }

        Set<Integer> itemIds = new HashSet<>(this.globalStates.keySet());
        Map<Integer, VisualState> personal = this.userStates.get(userId);
        if (personal != null) {
            itemIds.addAll(personal.keySet());
        }
        return effective(userId, itemIds.stream().sorted().toList());
    }

    synchronized void forgetItem(HabboItem item) {
        if (item == null) {
            return;
        }
        int itemId = item.getId();
        VisualState removedGlobal = this.globalStates.remove(itemId);
        if (removedGlobal != null) {
            this.stateCount--;
            if (removedGlobal.customColor() != null) {
                this.customColorBytes -= removedGlobal.customColor().getBytes(java.nio.charset.StandardCharsets.UTF_8).length;
            }
        }
        for (Map<Integer, VisualState> states : this.userStates.values()) {
            if (states.remove(itemId) != null) {
                this.stateCount--;
            }
        }
        this.userStates.values().removeIf(Map::isEmpty);
    }

    synchronized void forgetUser(int userId) {
        Map<Integer, VisualState> removed = this.userStates.remove(userId);
        if (removed != null) {
            this.stateCount -= removed.size();
        }
    }

    synchronized void dispose() {
        this.disposed = true;
        this.globalStates.clear();
        this.userStates.clear();
        this.stateCount = 0;
        this.customColorBytes = 0;
    }

    synchronized void resetForRoomLoad() {
        this.disposed = false;
        this.globalStates.clear();
        this.userStates.clear();
        this.stateCount = 0;
        this.customColorBytes = 0;
    }

    synchronized int stateCount() {
        return this.stateCount;
    }

    /** Current room-wide opacity for one item, ignoring per-user overlays. */
    synchronized int globalOpacity(int itemId) {
        VisualState state = this.globalStates.get(itemId);
        return state == null ? VisualState.DEFAULT.opacity() : state.opacity();
    }

    synchronized int globalScaling(int itemId) {
        VisualState state = this.globalStates.get(itemId);
        return state == null ? VisualState.DEFAULT.scaling() : state.scaling();
    }

    synchronized List<WiredOpacityState> applyGlobalScaling(Collection<HabboItem> items, int scaling) {
        if (this.disposed) {
            return List.of();
        }

        ArrayList<WiredOpacityState> applied = new ArrayList<>();
        for (HabboItem item : validItems(items)) {
            VisualState previous = this.globalStates.get(item.getId());
            int opacity = previous == null ? 100 : previous.opacity();
            boolean clickThrough = previous != null && previous.clickThrough();
            VisualState next = new VisualState(opacity, clickThrough, scaling, previous == null ? null : previous.customColor());
            if (next.isDefault()) {
                if (previous != null) {
                    this.globalStates.remove(item.getId());
                    this.stateCount--;
                }
                applied.add(state(item, next));
            } else if (previous != null || reserveState(item.getId())) {
                this.globalStates.put(item.getId(), next);
                applied.add(state(item, next));
            }
        }
        return List.copyOf(applied);
    }

    synchronized List<WiredOpacityState> applyGlobalCustomColor(Collection<HabboItem> items, String customColor) {
        if (this.disposed) {
            return List.of();
        }

        int nextBytes = customColor == null ? 0 : customColor.getBytes(java.nio.charset.StandardCharsets.UTF_8).length;
        if (nextBytes > WiredFurnitureCustomColor.MAXIMUM_BYTES_PER_ITEM) {
            return List.of();
        }

        ArrayList<WiredOpacityState> applied = new ArrayList<>();
        for (HabboItem item : validItems(items)) {
            VisualState previous = this.globalStates.get(item.getId());
            int previousBytes = previous == null || previous.customColor() == null
                    ? 0
                    : previous.customColor().getBytes(java.nio.charset.StandardCharsets.UTF_8).length;
            if (nextBytes > previousBytes
                    && this.customColorBytes > WiredFurnitureCustomColor.MAXIMUM_BYTES_PER_ROOM - (nextBytes - previousBytes)) {
                LOGGER.warn("Furniture custom color memory limit reached: room={}, rejectedItem={}", this.room.getId(), item.getId());
                continue;
            }

            int opacity = previous == null ? 100 : previous.opacity();
            boolean clickThrough = previous != null && previous.clickThrough();
            int scaling = previous == null ? 100 : previous.scaling();
            VisualState next = new VisualState(opacity, clickThrough, scaling, customColor);
            if (next.isDefault()) {
                if (previous != null) {
                    this.globalStates.remove(item.getId());
                    this.stateCount--;
                    this.customColorBytes -= previousBytes;
                }
                applied.add(state(item, next));
            } else if (previous != null || reserveState(item.getId())) {
                this.globalStates.put(item.getId(), next);
                this.customColorBytes += nextBytes - previousBytes;
                applied.add(state(item, next));
            }
        }
        return List.copyOf(applied);
    }

    synchronized List<WiredOpacityState> customColorSnapshot() {
        if (this.disposed || this.globalStates.isEmpty()) {
            return List.of();
        }

        ArrayList<WiredOpacityState> result = new ArrayList<>();
        for (Map.Entry<Integer, VisualState> entry : this.globalStates.entrySet()) {
            if (entry.getValue().customColor() == null) {
                continue;
            }
            HabboItem item = this.room.getHabboItem(entry.getKey());
            if (isCurrentRoomItem(item)) {
                result.add(state(item, entry.getValue()));
            }
        }
        result.sort(java.util.Comparator.comparingInt(WiredOpacityState::itemId));
        return List.copyOf(result);
    }

    synchronized List<WiredOpacityState> globalStates(Collection<Integer> itemIds) {
        if (this.disposed || itemIds == null || itemIds.isEmpty()) {
            return List.of();
        }

        ArrayList<WiredOpacityState> result = new ArrayList<>();
        Set<Integer> seen = new HashSet<>();
        for (Integer itemId : itemIds) {
            if (itemId == null || !seen.add(itemId)) {
                continue;
            }
            HabboItem item = this.room.getHabboItem(itemId);
            if (!isCurrentRoomItem(item)) {
                continue;
            }
            result.add(state(item, this.globalStates.getOrDefault(itemId, VisualState.DEFAULT)));
        }
        return List.copyOf(result);
    }

    synchronized Snapshot createSnapshot(Set<Integer> itemIds) {
        if (this.disposed || itemIds == null || itemIds.isEmpty()) {
            return new Snapshot(Map.of());
        }

        Map<Integer, VisualState> copiedStates = new HashMap<>();
        for (Integer itemId : itemIds) {
            if (itemId == null) {
                continue;
            }
            VisualState state = this.globalStates.get(itemId);
            if (state != null) {
                copiedStates.put(itemId, state);
            }
        }
        return new Snapshot(Map.copyOf(copiedStates));
    }

    /** Restores only room-wide opacity, which is the state exposed by the {@code @opacity} variable. */
    synchronized Set<Integer> restoreSnapshot(Snapshot snapshot, Set<Integer> itemIds) {
        if (this.disposed || snapshot == null || itemIds == null || itemIds.isEmpty()) {
            return Set.of();
        }

        Set<Integer> changedItemIds = new HashSet<>();
        for (Integer itemId : itemIds) {
            HabboItem item = itemId == null ? null : this.room.getHabboItem(itemId);
            if (!isCurrentRoomItem(item)) {
                continue;
            }

            VisualState previous = this.globalStates.remove(itemId);
            VisualState restored = snapshot.globalStates().get(itemId);
            if (restored != null && !restored.isDefault()) {
                this.globalStates.put(itemId, restored);
            }

            VisualState effectivePrevious = previous == null ? VisualState.DEFAULT : previous;
            VisualState effectiveRestored = restored == null ? VisualState.DEFAULT : restored;
            if (!effectivePrevious.equals(effectiveRestored)) {
                changedItemIds.add(itemId);
            }
        }

        this.recalculateStateCount();
        return Set.copyOf(changedItemIds);
    }

    /** Current room-wide click-through for one item, ignoring per-user overlays. */
    synchronized boolean globalClickThrough(int itemId) {
        VisualState state = this.globalStates.get(itemId);
        return state != null && state.clickThrough();
    }

    private List<HabboItem> validItems(Collection<HabboItem> items) {
        if (items == null || items.isEmpty()) {
            return List.of();
        }
        return items.stream()
                .filter(this::isCurrentRoomItem)
                .distinct()
                .sorted(java.util.Comparator.comparingInt(HabboItem::getId))
                .toList();
    }

    private boolean isCurrentRoomItem(HabboItem item) {
        return item != null
                && item.getId() > 0
                && item.getRoomId() == this.room.getId()
                && item.getBaseItem() != null
                && this.room.getHabboItem(item.getId()) == item;
    }

    private boolean reserveState(int itemId) {
        if (this.stateCount >= this.maximumStates) {
            LOGGER.warn(
                    "Opacity state limit reached: room={}, configuredLimit={}, rejectedItem={}",
                    this.room.getId(),
                    this.maximumStates,
                    itemId);
            return false;
        }
        this.stateCount++;
        return true;
    }

    private void recalculateStateCount() {
        int total = this.globalStates.size();
        this.customColorBytes = this.globalStates.values().stream()
                .map(VisualState::customColor)
                .filter(java.util.Objects::nonNull)
                .mapToInt(value -> value.getBytes(java.nio.charset.StandardCharsets.UTF_8).length)
                .sum();
        for (Map<Integer, VisualState> states : this.userStates.values()) {
            total += states.size();
        }
        this.stateCount = total;
    }

    private static WiredOpacityState state(HabboItem item, VisualState state) {
        return new WiredOpacityState(
                item.getId(),
                item.getBaseItem().getType() == FurnitureType.WALL,
                state.opacity(),
                state.clickThrough(),
                state.scaling(),
                state.customColor());
    }

    private static int configuredMaximumStates() {
        ConfigurationManager configuration = WiredPlatform.configuration();
        if (configuration == null) {
            return DEFAULT_MAXIMUM_STATES;
        }
        return Math.max(
                1,
                Math.min(
                        ABSOLUTE_MAXIMUM_STATES,
                        configuration.getInt("wired.opacity.max_states_per_room", DEFAULT_MAXIMUM_STATES)));
    }

    private record VisualState(int opacity, boolean clickThrough, int scaling, String customColor) {
        private static final VisualState DEFAULT = new VisualState(100, false, 100, null);

        private VisualState(int opacity, boolean clickThrough, int scaling) {
            this(opacity, clickThrough, scaling, null);
        }

        private VisualState {
            opacity = Math.max(0, Math.min(100, opacity));
            scaling = WiredFurnitureScaling.normalize(scaling);
        }

        private boolean isDefault() {
            return this.opacity == 100 && !this.clickThrough && this.scaling == 100 && this.customColor == null;
        }
    }

    record Snapshot(Map<Integer, VisualState> globalStates) {}
}
