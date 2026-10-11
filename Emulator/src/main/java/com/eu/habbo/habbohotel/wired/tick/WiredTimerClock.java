package com.eu.habbo.habbohotel.wired.tick;

import java.util.function.BooleanSupplier;

/** Per-timer elapsed logical time. Missed ticks are accounted for without replaying bursts. */
public final class WiredTimerClock {
    private long lastTick;
    private long elapsedMs;
    private boolean fired;

    public synchronized void reset(long tick) {
        lastTick = tick;
        elapsedMs = 0;
        fired = false;
    }

    public boolean poll(long tick, int tickIntervalMs, int intervalMs, boolean repeat) {
        return poll(tick, tickIntervalMs, intervalMs, repeat, () -> true);
    }

    public synchronized boolean poll(
            long tick, int tickIntervalMs, int intervalMs, boolean repeat, BooleanSupplier admission) {
        if (tick <= lastTick || tickIntervalMs <= 0 || intervalMs <= 0 || fired) return false;
        elapsedMs += (tick - lastTick) * tickIntervalMs;
        lastTick = tick;
        if (elapsedMs < intervalMs) return false;
        boolean permitted = admission.getAsBoolean();
        if (repeat) elapsedMs %= intervalMs;
        else if (permitted) {
            elapsedMs = 0;
            fired = true;
        }
        return permitted;
    }
}
