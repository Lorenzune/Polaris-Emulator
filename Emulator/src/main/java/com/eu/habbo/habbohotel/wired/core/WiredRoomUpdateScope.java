package com.eu.habbo.habbohotel.wired.core;

/**
 * Marks a wired execution section where item visual updates should be emitted
 * as one response batch when the execution finishes.
 *
 * <p>This is intentionally narrow: logical wired execution still happens
 * immediately and signal events are still dispatched one-by-one. Only the room
 * item composer delivery is grouped until the signal chain completes; it does
 * not wait for the room scheduler tick.</p>
 */
public final class WiredRoomUpdateScope implements AutoCloseable {
    private static final ThreadLocal<Integer> DEFER_VISUAL_UPDATES_DEPTH =
        ThreadLocal.withInitial(() -> 0);

    private boolean closed;

    private WiredRoomUpdateScope() {
    }

    public static WiredRoomUpdateScope batchVisualUpdates() {
        DEFER_VISUAL_UPDATES_DEPTH.set(DEFER_VISUAL_UPDATES_DEPTH.get() + 1);
        return new WiredRoomUpdateScope();
    }

    public static boolean shouldDeferVisualUpdates() {
        return DEFER_VISUAL_UPDATES_DEPTH.get() > 0;
    }

    @Override
    public void close() {
        if (this.closed) {
            return;
        }

        int depth = DEFER_VISUAL_UPDATES_DEPTH.get() - 1;

        if (depth <= 0) {
            DEFER_VISUAL_UPDATES_DEPTH.remove();
        } else {
            DEFER_VISUAL_UPDATES_DEPTH.set(depth);
        }

        this.closed = true;
    }
}
