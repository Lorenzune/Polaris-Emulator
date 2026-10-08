package com.eu.habbo.habbohotel.wired.core;

import com.eu.habbo.habbohotel.rooms.Room;
import com.eu.habbo.habbohotel.rooms.RoomUnit;
import com.eu.habbo.habbohotel.users.HabboItem;
import java.util.HashSet;
import java.util.Set;

/** Separate native use/state events; legacy dispatch remains unchanged. */
public final class WiredFurnitureStateEvents {
    private static final ThreadLocal<RoomUnit> ACTOR = new ThreadLocal<>();

    private record EventKey(WiredEvent.Type type, HabboItem item) {}

    private static final ThreadLocal<Set<EventKey>> ACTIVE = ThreadLocal.withInitial(HashSet::new);

    private WiredFurnitureStateEvents() {}

    public static void use(Room room, RoomUnit actor, HabboItem item, UseAction action) throws Exception {
        RoomUnit previous = ACTOR.get();
        ACTOR.set(actor);
        try {
            action.run();
            emit(WiredEvent.Type.FURNI_USED, room, actor, item);
        } finally {
            if (previous == null) ACTOR.remove();
            else ACTOR.set(previous);
        }
    }

    public static void stateUpdated(Room room, HabboItem item) {
        if (item == null || !item.consumeWiredStateChange()) return;
        if (room == null || !room.isLoaded() || room.getHabboItem(item.getId()) != item) return;
        if (WiredFurnitureStateBatch.defer(room, ACTOR.get(), item)) return;
        emit(WiredEvent.Type.FURNI_STATE_UPDATED, room, ACTOR.get(), item);
    }

    private static void emit(WiredEvent.Type type, Room room, RoomUnit actor, HabboItem item) {
        if (!WiredManager.isEnabled() || room == null || item == null || room.getLayout() == null) return;
        Set<EventKey> active = ACTIVE.get();
        EventKey key = new EventKey(type, item);
        if (!active.add(key)) return;
        try {
            WiredManager.handleEvent(WiredEvent.builder(type, room)
                    .actor(actor)
                    .sourceItem(item)
                    .tile(room.getLayout().getTile(item.getX(), item.getY()))
                    .build());
        } finally {
            active.remove(key);
            if (active.isEmpty()) ACTIVE.remove();
        }
    }

    @FunctionalInterface
    public interface UseAction {
        void run() throws Exception;
    }
}
