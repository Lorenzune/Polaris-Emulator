package com.eu.habbo.habbohotel.wired.core;

import com.eu.habbo.habbohotel.rooms.Room;
import com.eu.habbo.habbohotel.rooms.RoomUnit;
import com.eu.habbo.habbohotel.users.HabboItem;
import java.util.ArrayDeque;

/** Publish native state changes after sibling effects finish updating their controls. */
public final class WiredFurnitureStateBatch implements AutoCloseable {
    private static final ThreadLocal<WiredFurnitureStateBatch> CURRENT = new ThreadLocal<>();
    private final WiredFurnitureStateBatch parent;
    private final ArrayDeque<WiredEvent> events = new ArrayDeque<>();

    private WiredFurnitureStateBatch(WiredFurnitureStateBatch parent) {
        this.parent = parent;
    }

    public static WiredFurnitureStateBatch begin() {
        var scope = new WiredFurnitureStateBatch(CURRENT.get());
        CURRENT.set(scope);
        return scope;
    }

    static boolean defer(Room room, RoomUnit actor, HabboItem item) {
        var scope = CURRENT.get();
        if (scope == null) return false;
        if (WiredManager.isEnabled() && room != null && room.getLayout() != null) {
            scope.events.add(WiredEvent.builder(WiredEvent.Type.FURNI_STATE_UPDATED, room)
                    .actor(actor).sourceItem(item).text(item.getExtradata())
                    .tile(room.getLayout().getTile(item.getX(), item.getY())).build());
        }
        return true;
    }

    @Override
    public void close() {
        if (parent != null) {
            parent.events.addAll(events);
            CURRENT.set(parent);
            return;
        }
        try {
            // Keep a queue while dispatching: nested effect batches append their
            // changes instead of recursively interrupting a sibling effect.
            final int maxStateEvents = 2048;
            int dispatched = 0;
            while (!events.isEmpty() && dispatched++ < maxStateEvents) {
                WiredManager.handleEvent(events.removeFirst());
            }
            if (!events.isEmpty()) {
                org.slf4j.LoggerFactory.getLogger(WiredFurnitureStateBatch.class).warn(
                        "Stopped cyclic WIRED furniture state events in room {} after {} events",
                        events.peek().getRoom().getId(), maxStateEvents);
                events.clear();
            }
        } finally {
            CURRENT.remove();
        }
    }
}
