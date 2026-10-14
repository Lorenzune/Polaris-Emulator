package com.eu.habbo.habbohotel.wired.core;

import com.eu.habbo.habbohotel.items.interactions.wired.triggers.WiredTriggerVariableChanged;
import com.eu.habbo.habbohotel.rooms.Room;
import com.eu.habbo.habbohotel.rooms.RoomUnit;
import com.eu.habbo.habbohotel.users.HabboItem;
import com.eu.habbo.habbohotel.wired.WiredTriggerType;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/** Watches only internal variables selected by active triggers; state belongs to one room. */
public final class WiredInternalVariableMonitor {
    private final Map<Key, Integer> values = new HashMap<>();
    private java.time.LocalDate observedDay = java.time.LocalDate.now();

    private record Key(int target, Object entity, String token) {}

    private record Subscription(int target, String token) {}

    public synchronized void clear() {
        values.clear();
    }

    public void poll(Room room) {
        if (room == null || room.getRoomSpecialTypes() == null) return;
        var today = java.time.LocalDate.now();
        if (!today.equals(observedDay)) {
            observedDay = today;
            for (var habbo : room.getCurrentHabbos().values())
                room.getUserVariableManager()
                        .refreshDailyTaskVariables(habbo.getHabboInfo().getId());
        }
        var triggers = room.getRoomSpecialTypes().getTriggers(WiredTriggerType.VARIABLE_CHANGED);
        if (triggers.isEmpty()) {
            clear();
            return;
        }
        Set<Subscription> subscriptions = new HashSet<>();
        Set<RoomUnit> units = null;
        Set<HabboItem> furniture = null;
        Set<Key> live = new HashSet<>();
        var changes = new ArrayList<WiredEvent>();
        for (var trigger : triggers) {
            if (!(trigger instanceof WiredTriggerVariableChanged variable) || !variable.isInternalVariable()) continue;
            int target = variable.getVariableTargetType();
            String token = variable.getVariableToken();
            // Identical watchers share one observation, and each entity collection
            // is captured at most once. Events are still dispatched after polling.
            if (!subscriptions.add(new Subscription(target, token))) continue;
            String name = token.substring(9);
            if (target == 0) {
                if (units == null) units = room.getRoomUnits();
                for (RoomUnit unit : units) {
                    Key key = new Key(target, unit, token);
                    if (!live.add(key)) continue;
                    Integer next = WiredInternalVariableSupport.hasUserValue(room, unit, name)
                            ? WiredInternalVariableSupport.readUserValue(room, unit, name)
                            : null;
                    collect(room, key, next, unit, null, changes);
                }
            } else if (target == 1) {
                if (furniture == null) furniture = room.getFloorItems();
                for (HabboItem item : furniture) {
                    Key key = new Key(target, item.getId(), token);
                    if (!live.add(key)) continue;
                    collect(
                            room,
                            key,
                            WiredInternalVariableSupport.readFurniValue(room, item, name),
                            null,
                            item,
                            changes);
                }
            } else {
                Key key = new Key(target, room.getId(), token);
                if (live.add(key))
                    collect(room, key, WiredInternalVariableSupport.readRoomValue(room, name), null, null, changes);
            }
        }
        synchronized (this) {
            values.keySet().retainAll(live);
        }
        changes.forEach(WiredManager::handleEvent);
    }

    private synchronized void collect(
            Room room, Key key, Integer next, RoomUnit actor, HabboItem item, java.util.List<WiredEvent> changes) {
        boolean known = values.containsKey(key);
        Integer previous = values.put(key, next);
        if (known && !Objects.equals(previous, next)) changes.add(change(room, key, previous, next, actor, item));
    }

    public void changed(
            Room room, int target, RoomUnit actor, HabboItem item, String name, Integer previous, Integer next) {
        if (previous == null && next == null) return;
        Key key = new Key(
                target,
                target == 0 ? actor : item.getId(),
                "internal:" + WiredInternalVariableSupport.normalizeKey(name));
        synchronized (this) {
            values.put(key, next);
        }
        WiredManager.handleEvent(change(room, key, previous, next, actor, item));
    }

    private static WiredEvent change(
            Room room, Key key, Integer previous, Integer next, RoomUnit actor, HabboItem item) {
        return WiredEvent.builder(WiredEvent.Type.VARIABLE_CHANGED, room)
                .actor(actor)
                .sourceItem(item)
                .variableTargetType(key.target())
                .variableToken(key.token())
                .variableCreated(previous == null && next != null)
                .variableDeleted(previous != null && next == null)
                .variableChangeKind(
                        previous == null || next == null
                                ? WiredEvent.VariableChangeKind.NONE
                                : next.equals(previous)
                                        ? WiredEvent.VariableChangeKind.UNCHANGED
                                        : next > previous
                                                ? WiredEvent.VariableChangeKind.INCREASED
                                                : WiredEvent.VariableChangeKind.DECREASED)
                .build();
    }
}
