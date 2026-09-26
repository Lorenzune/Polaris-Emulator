package com.eu.habbo.habbohotel.wired.core;

import com.eu.habbo.habbohotel.items.interactions.InteractionWiredExtra;
import com.eu.habbo.habbohotel.rooms.Room;
import java.util.IdentityHashMap;
import java.util.Map;

/** Coalesces variable-definition box state updates during one synchronous Wired operation. */
public final class WiredVariableBoxUpdateScope implements AutoCloseable {
    private static final ThreadLocal<State> CURRENT = new ThreadLocal<>();

    private final State state;
    private boolean closed;

    private WiredVariableBoxUpdateScope(State state) {
        this.state = state;
    }

    public static WiredVariableBoxUpdateScope begin() {
        State state = CURRENT.get();
        if (state == null) {
            state = new State();
            CURRENT.set(state);
        }
        state.depth++;
        return new WiredVariableBoxUpdateScope(state);
    }

    public static void activate(InteractionWiredExtra extra, Room room) {
        if (extra == null || room == null) {
            return;
        }

        State state = CURRENT.get();
        if (state == null) {
            extra.activateBox(room);
            return;
        }

        state.pending.put(extra, room);
    }

    @Override
    public void close() {
        if (this.closed) {
            return;
        }
        this.closed = true;

        if (--this.state.depth != 0) {
            return;
        }

        CURRENT.remove();
        for (Map.Entry<InteractionWiredExtra, Room> entry : this.state.pending.entrySet()) {
            entry.getKey().activateBox(entry.getValue());
        }
    }

    private static final class State {
        private final IdentityHashMap<InteractionWiredExtra, Room> pending = new IdentityHashMap<>();
        private int depth;
    }
}
