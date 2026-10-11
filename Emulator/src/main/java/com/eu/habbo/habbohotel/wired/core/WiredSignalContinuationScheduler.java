package com.eu.habbo.habbohotel.wired.core;

import com.eu.habbo.habbohotel.rooms.Room;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;

/** Keeps one task per yielded chain, with explicit lifecycle cancellation and shutdown ownership. */
final class WiredSignalContinuationScheduler {
    private static final org.slf4j.Logger LOGGER =
            org.slf4j.LoggerFactory.getLogger(WiredSignalContinuationScheduler.class);
    private final WiredDelayedScheduler.TaskScheduler scheduler;
    private final Set<Continuation> pending = ConcurrentHashMap.newKeySet();
    private final AtomicBoolean accepting = new AtomicBoolean(true);
    private final AtomicBoolean fallbackReported = new AtomicBoolean();
    private final AtomicBoolean cancellationFailureReported = new AtomicBoolean();

    WiredSignalContinuationScheduler(WiredDelayedScheduler.TaskScheduler scheduler) {
        this.scheduler = scheduler;
    }

    void schedule(Room room, Runnable work, Runnable inlineFallback, long delayMs) {
        Continuation continuation = new Continuation(room, work);
        if (!accepting.get()) return;
        pending.add(continuation);
        if (!accepting.get()) {
            continuation.cancel();
            return;
        }
        WiredDelayedScheduler.ScheduledTask task;
        String fallbackReason = "executor unavailable or continuation backlog";
        try {
            // Already accepted publications finish inline if a live executor
            // cannot accept a continuation. Bound the scheduler backlog as well.
            task = pending.size() > 10_000 ? null : scheduler.schedule(continuation, Math.max(1L, delayMs));
        } catch (RuntimeException failure) {
            task = null;
            fallbackReason = failure.getClass().getSimpleName();
        }
        if (task == null) {
            if (continuation.claim() && accepting.get() && continuation.valid()) {
                if (fallbackReported.compareAndSet(false, true)) {
                    LOGGER.warn(
                            "WIRED continuations finish inline in room {}: {}; accepted work is preserved",
                            room.getId(),
                            fallbackReason);
                }
                inlineFallback.run();
            }
        } else {
            continuation.task = task;
            if (continuation.finished.get() || !accepting.get()) cancelTask(task);
        }
    }

    int shutdown() {
        accepting.set(false);
        int cancelled = 0;
        for (Continuation continuation : List.copyOf(pending)) if (continuation.cancel()) cancelled++;
        return cancelled;
    }

    int pendingCount() {
        return pending.size();
    }

    private void cancelTask(WiredDelayedScheduler.ScheduledTask task) {
        try {
            task.cancel();
        } catch (RuntimeException failure) {
            if (cancellationFailureReported.compareAndSet(false, true)) {
                LOGGER.warn(
                        "WIRED continuation cancellation failed: {}; cancelled callbacks cannot execute",
                        failure.getClass().getSimpleName());
            }
        }
    }

    private final class Continuation implements Runnable {
        final Room room;
        final long generation;
        final Runnable work;
        final AtomicBoolean finished = new AtomicBoolean();
        volatile WiredDelayedScheduler.ScheduledTask task;

        Continuation(Room room, Runnable work) {
            this.room = room;
            this.generation = room.getLifecycleGeneration();
            this.work = work;
        }

        boolean valid() {
            return room.isLoaded() && room.getLifecycleGeneration() == generation;
        }

        boolean claim() {
            if (!finished.compareAndSet(false, true)) return false;
            pending.remove(this);
            return true;
        }

        boolean cancel() {
            if (!claim()) return false;
            if (task != null) cancelTask(task);
            return true;
        }

        public void run() {
            if (claim() && accepting.get() && valid()) work.run();
        }
    }
}
