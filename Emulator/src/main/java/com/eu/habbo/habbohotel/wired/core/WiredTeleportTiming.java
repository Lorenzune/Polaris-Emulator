package com.eu.habbo.habbohotel.wired.core;

/**
 * Standard server-side timings for wired teleports.
 *
 * <p>The SWF stores only the fast-teleport option. These values are therefore
 * deliberately code constants and must not be overridden by emulator settings.</p>
 */
public final class WiredTeleportTiming {
    /** Matches the SWF MovingObjectLogic.DEFAULT_UPDATE_INTERVAL. */
    public static final int MOVEMENT_UPDATE_INTERVAL_MS = 500;

    /**
     * The movement packet is emitted after the walk-on callback. This margin
     * gives the client time to receive that packet before the teleport state
     * is published.
     */
    public static final int WALK_ON_SYNC_BUFFER_MS = 100;

    /** Base delay for a teleport that is not waiting on an avatar step. */
    public static final int NORMAL_DELAY_MS = 500;

    /** Base delay for the fast variant; walk-on teleports clamp this to movement completion. */
    public static final int FAST_DELAY_MS = 100;

    public static final int NORMAL_EFFECT_DURATION_MS = 1500;
    public static final int FAST_EFFECT_DURATION_MS = 500;

    private WiredTeleportTiming() {}
}
