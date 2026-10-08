package com.eu.habbo.habbohotel.wired.core;

import com.eu.habbo.habbohotel.rooms.Room;
import com.eu.habbo.messages.outgoing.rooms.WiredMovementsComposer;
import java.util.ArrayList;
import java.util.List;

/** Publish the final avatar movement after all effects in one batch have run. */
public final class WiredUserMovementBatch implements AutoCloseable {
    private static final ThreadLocal<WiredUserMovementBatch> CURRENT = new ThreadLocal<>();
    private final WiredUserMovementBatch parent;
    private final Room room;
    private final List<WiredMovementsComposer.MovementData> movements = new ArrayList<>();

    private WiredUserMovementBatch(Room room) {
        this.parent = CURRENT.get();
        this.room = room;
        CURRENT.set(this);
    }

    public static WiredUserMovementBatch begin(Room room) {
        return new WiredUserMovementBatch(room);
    }

    public static void send(Room room, List<WiredMovementsComposer.MovementData> movements) {
        var scope = CURRENT.get();
        if (scope != null && scope.room == room) scope.movements.addAll(movements);
        else room.sendComposer(new WiredMovementsComposer(movements).compose());
    }

    @Override public void close() {
        if (parent == null) CURRENT.remove();
        else CURRENT.set(parent);
        if (!movements.isEmpty()) send(room, movements);
    }
}
