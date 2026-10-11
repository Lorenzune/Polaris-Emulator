package com.eu.habbo.habbohotel.wired.tick;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.RejectedExecutionException;
import java.util.function.BiConsumer;
import java.util.function.LongSupplier;

/**
 * One outstanding turn per room, rather than one draining loop per shard.
 * Callbacks are never concurrent for a room; pool changes occur between turns.
 * This schedules clock notifications only, not WIRED publications or effects.
 */
final class RoomWiredTickDispatcher {
    private final ExecutorService normalWorkers;
    private final ExecutorService slowWorkers;
    private final BiConsumer<Integer, Long> callback;
    private final BiConsumer<Integer, Throwable> failure;
    private final LongSupplier nanoClock;
    private final long slowThresholdNanos;
    private final Map<Integer, Lane> rooms = new HashMap<>();
    private boolean closed;

    RoomWiredTickDispatcher(
            ExecutorService normalWorkers,
            ExecutorService slowWorkers,
            BiConsumer<Integer, Long> callback,
            BiConsumer<Integer, Throwable> failure,
            long slowThresholdMs,
            LongSupplier nanoClock) {
        this.normalWorkers = normalWorkers;
        this.slowWorkers = slowWorkers;
        this.callback = callback;
        this.failure = failure;
        this.slowThresholdNanos = slowThresholdMs * 1_000_000L;
        this.nanoClock = nanoClock;
    }

    synchronized void request(int roomId, long tick) {
        if (closed) return;
        Lane lane = rooms.computeIfAbsent(roomId, id -> new Lane(id, tick - 1));
        if (lane.retired) {
            // A newly registered room starts at the current clock, never replays
            // notifications belonging to its previous registration.
            lane.resumeFloor = tick - 1;
            lane.processed = Math.max(lane.processed, lane.resumeFloor);
            lane.requested = tick;
        }
        lane.retired = false;
        lane.requested = Math.max(lane.requested, tick);
        if (!lane.scheduled && lane.processed < lane.requested) submit(lane);
    }

    /** Retain a running lane until completion, even if the room is immediately reloaded. */
    synchronized void forget(int roomId) {
        Lane lane = rooms.get(roomId);
        if (lane == null) return;
        lane.retired = true;
        if (!lane.scheduled) rooms.remove(roomId, lane);
    }

    synchronized int roomCount() {
        return rooms.size();
    }

    synchronized int slowRoomCount() {
        return (int) rooms.values().stream().filter(lane -> lane.slow).count();
    }

    synchronized long pendingTicks(int roomId) {
        Lane lane = rooms.get(roomId);
        return lane == null ? 0 : Math.max(0, lane.requested - lane.processed);
    }

    synchronized long maxTurnNanos(int roomId) {
        Lane lane = rooms.get(roomId);
        return lane == null ? 0 : lane.maxTurnNanos;
    }

    synchronized void close() {
        closed = true;
        rooms.clear();
    }

    private void submit(Lane lane) {
        lane.scheduled = true;
        try {
            (lane.slow ? slowWorkers : normalWorkers).execute(() -> runTurn(lane));
        } catch (RejectedExecutionException rejected) {
            lane.scheduled = false;
            // During normal operation the pools have no per-event rejection policy.
            // A live lane is retained so the next clock dispatch can retry it.
            if (!closed) failure.accept(lane.roomId, rejected);
        }
    }

    private void runTurn(Lane lane) {
        long tick;
        synchronized (this) {
            if (closed || rooms.get(lane.roomId) != lane || lane.retired) {
                lane.scheduled = false;
                rooms.remove(lane.roomId, lane);
                return;
            }
            tick = lane.processed + 1;
            // Preserve the previous service's clock catch-up rule, independently
            // per room. This does not remove any accepted signal/effect work.
            if (lane.requested - tick > 5) tick = lane.requested - 5;
        }
        long started = nanoClock.getAsLong();
        try {
            callback.accept(lane.roomId, tick);
        } catch (Throwable error) {
            failure.accept(lane.roomId, error);
        } finally {
            long elapsed = Math.max(0, nanoClock.getAsLong() - started);
            synchronized (this) {
                lane.processed = Math.max(tick, lane.resumeFloor);
                lane.maxTurnNanos = Math.max(lane.maxTurnNanos, elapsed);
                lane.slow |= elapsed > slowThresholdNanos;
                lane.scheduled = false;
                if (closed || lane.retired) rooms.remove(lane.roomId, lane);
                else if (lane.processed < lane.requested) submit(lane);
            }
        }
    }

    private static final class Lane {
        final int roomId;
        long requested;
        long processed;
        long maxTurnNanos;
        long resumeFloor;
        boolean scheduled;
        boolean slow;
        boolean retired;

        Lane(int roomId, long processed) {
            this.roomId = roomId;
            this.processed = processed;
            this.requested = processed;
        }
    }
}
