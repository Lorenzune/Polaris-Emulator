package com.eu.habbo.habbohotel.wired.core;

import com.eu.habbo.WiredCompatibilityDiagnostics;
import com.eu.habbo.habbohotel.rooms.Room;
import com.eu.habbo.habbohotel.wired.api.IWiredEffect;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.LongSupplier;

/** Internal owner of delayed WIRED admission, timing and room/actor lifecycle checks. */
final class WiredDelayedScheduler {
    private static final int TASK_PENDING = 0;
    private static final int TASK_RUNNING = 1;
    private static final int TASK_COMPLETED = 2;
    private static final int TASK_CANCELLED = 3;

    @FunctionalInterface
    interface ScheduledTask {
        boolean cancel();
    }

    @FunctionalInterface
    interface TaskScheduler {
        ScheduledTask schedule(Runnable task, long delayMs);
    }

    @FunctionalInterface
    interface DiagnosticSink {
        void log(Room room, String format, Object... arguments);
    }

    @FunctionalInterface
    interface RoomResolver {
        Room resolve(int roomId);
    }

    @FunctionalInterface
    interface ResolvedWork {
        void run(WiredDelayedExecutionSnapshot.Resolved resolved);
    }

    private final TaskScheduler taskScheduler;
    private final LongSupplier clock;
    private final DiagnosticSink diagnostics;
    private final RoomResolver roomResolver;
    private final Set<PendingTask> pendingTasks = ConcurrentHashMap.newKeySet();
    private final Set<Object> pendingFeedback = ConcurrentHashMap.newKeySet();
    private final java.util.ArrayList<SignalFrame> signalFrames = new java.util.ArrayList<>();
    // Group nearby native signal deliveries before their dependent turn/cleanup
    // receivers run. Keep each payload independent; never execute before due.
    private static final long SIGNAL_FRAME_GRACE_MS = 25L;

    private final class SignalFrame {
        final int roomId;
        final long generation;
        final long deadline;
        final java.util.ArrayList<Runnable> work = new java.util.ArrayList<>();
        ScheduledTask scheduled;

        SignalFrame(Room room, long deadline) {
            this.roomId = room.getId();
            this.generation = room.getLifecycleGeneration();
            this.deadline = deadline;
        }

        void run() {
            List<Runnable> batch;
            synchronized (signalFrames) {
                signalFrames.remove(this);
                batch = List.copyOf(this.work);
                this.work.clear();
            }
            Room room = null;
            try {
                room = roomResolver.resolve(this.roomId);
            } catch (RuntimeException exception) {
                WiredCompatibilityDiagnostics.record(
                        WiredCompatibilityDiagnostics.FailurePoint.DELAYED_ROOM_RESOLVE, this.roomId, 0, exception);
            }
            if (room != null && room.getLifecycleGeneration() == this.generation)
                WiredManager.runDelayedSignalWave(room, batch);
            else batch.forEach(Runnable::run);
        }
    }

    private ScheduledTask scheduleSignalFrame(Room room, Runnable work, long delayMs) {
        long deadline = this.clock.getAsLong() + delayMs;
        synchronized (this.signalFrames) {
            SignalFrame frame = this.signalFrames.stream()
                    .filter(candidate -> candidate.roomId == room.getId()
                            && candidate.generation == room.getLifecycleGeneration()
                            && deadline >= candidate.deadline
                            && deadline <= candidate.deadline + SIGNAL_FRAME_GRACE_MS)
                    .findFirst()
                    .orElse(null);
            if (frame == null) {
                frame = new SignalFrame(room, deadline);
                this.signalFrames.add(frame);
                frame.work.add(work);
                try {
                    frame.scheduled = this.taskScheduler.schedule(frame::run, delayMs + SIGNAL_FRAME_GRACE_MS);
                    if (frame.scheduled == null) {
                        this.signalFrames.remove(frame);
                        return null;
                    }
                } catch (RuntimeException exception) {
                    this.signalFrames.remove(frame);
                    throw exception;
                }
            } else frame.work.add(work);
            SignalFrame chosen = frame;
            return () -> {
                synchronized (this.signalFrames) {
                    boolean removed = chosen.work.remove(work);
                    if (chosen.work.isEmpty()) {
                        this.signalFrames.remove(chosen);
                        if (chosen.scheduled != null) chosen.scheduled.cancel();
                    }
                    return removed;
                }
            };
        }
    }

    private final AtomicBoolean accepting = new AtomicBoolean(true);

    WiredDelayedScheduler(
            TaskScheduler taskScheduler, LongSupplier clock, DiagnosticSink diagnostics, RoomResolver roomResolver) {
        this.taskScheduler = Objects.requireNonNull(taskScheduler, "taskScheduler");
        this.clock = Objects.requireNonNull(clock, "clock");
        this.diagnostics = Objects.requireNonNull(diagnostics, "diagnostics");
        this.roomResolver = Objects.requireNonNull(roomResolver, "roomResolver");
    }

    void scheduleEffect(
            IWiredEffect effect,
            WiredContext context,
            int delay,
            long triggerTime,
            WiredRoomDiagnostics roomDiagnostics,
            String sourceLabel,
            int sourceId,
            Runnable work) {
        scheduleEffect(
                effect, context, delay, triggerTime, roomDiagnostics, sourceLabel, sourceId, ignored -> work.run());
    }

    void scheduleEffect(
            IWiredEffect effect,
            WiredContext context,
            int delay,
            long triggerTime,
            WiredRoomDiagnostics roomDiagnostics,
            String sourceLabel,
            int sourceId,
            ResolvedWork work) {
        String reason = String.format(
                "Scheduling delayed effect %s with delay %d tick(s)",
                effect.getClass().getSimpleName(), delay);
        String blockedMessage = "Delayed events cap blocked effect {}";
        schedule(
                List.of(effect),
                context,
                delay,
                triggerTime,
                roomDiagnostics,
                sourceLabel,
                sourceId,
                reason,
                blockedMessage,
                new Object[] {effect.getClass().getSimpleName()},
                work);
    }

    void scheduleOrderedBatch(
            List<IWiredEffect> batch,
            WiredContext context,
            int delay,
            long triggerTime,
            WiredRoomDiagnostics roomDiagnostics,
            String sourceLabel,
            int sourceId,
            Runnable work) {
        scheduleOrderedBatch(
                batch, context, delay, triggerTime, roomDiagnostics, sourceLabel, sourceId, ignored -> work.run());
    }

    void scheduleOrderedBatch(
            List<IWiredEffect> batch,
            WiredContext context,
            int delay,
            long triggerTime,
            WiredRoomDiagnostics roomDiagnostics,
            String sourceLabel,
            int sourceId,
            ResolvedWork work) {
        String reason =
                String.format("Scheduling ordered batch with %d effect(s) and delay %d tick(s)", batch.size(), delay);
        schedule(
                batch,
                context,
                delay,
                triggerTime,
                roomDiagnostics,
                sourceLabel,
                sourceId,
                reason,
                "Delayed events cap blocked ordered batch with {} effect(s)",
                new Object[] {batch.size()},
                work);
    }

    private void schedule(
            List<IWiredEffect> effects,
            WiredContext context,
            int delay,
            long triggerTime,
            WiredRoomDiagnostics roomDiagnostics,
            String sourceLabel,
            int sourceId,
            String reason,
            String blockedMessage,
            Object[] blockedArguments,
            ResolvedWork work) {
        WiredDelayedExecutionSnapshot snapshot = WiredDelayedExecutionSnapshot.capture(effects, context, triggerTime);
        Object feedbackKey = context.eventType() == WiredEvent.Type.SIGNAL_RECEIVED
                        && context.event().isSignalPayloadExplicit()
                        && effects.size() == 1
                        && effects.getFirst()
                                instanceof
                                com.eu.habbo.habbohotel.items.interactions.wired.effects.WiredEffectSendSignal sender
                        && sender.isNativeSelfFeedback(context)
                ? snapshot.feedbackKey(delay, sender.getWiredData())
                : null;
        if (feedbackKey != null && !this.pendingFeedback.add(feedbackKey)) {
            return;
        }
        if (!roomDiagnostics.tryScheduleDelayedEvent(this.clock.getAsLong(), sourceLabel, sourceId, reason)) {
            if (feedbackKey != null) this.pendingFeedback.remove(feedbackKey);
            this.diagnostics.log(context.room(), blockedMessage, blockedArguments);
            return;
        }

        long delayMs = delay * 500L;
        long elapsedSinceTrigger = Math.max(0L, this.clock.getAsLong() - snapshot.triggerTime());
        long remainingDelayMs = Math.max(0L, delayMs - elapsedSinceTrigger);

        Runnable delayedWork = () -> {
            Room room = null;
            try {
                room = this.roomResolver.resolve(snapshot.roomId());
            } catch (RuntimeException ignored) {
                // Tests and pre-index programmatic contexts can have no global room manager.
                WiredCompatibilityDiagnostics.record(
                        WiredCompatibilityDiagnostics.FailurePoint.DELAYED_ROOM_RESOLVE,
                        snapshot.roomId(),
                        sourceId,
                        ignored);
            }
            // A loaded room can be empty while its end-of-game reset is pending.
            // Finish furniture effects; lifecycle validation below rejects work
            // from unloaded/reloaded rooms and actor checks reject departed users.
            if (room == null || !room.isLoaded()) {
                return;
            }
            // A scheduled effect starts a new synchronous execution chain.
            // Keeping the previous depth turns legitimate timer/signal loops
            // into exhausted Call Stacks recursion after a few seconds.
            var resolved = snapshot.resolveForNewChain(room);
            if (resolved.isEmpty() || !prepareDelayedExecution(resolved.get())) {
                return;
            }

            work.run(resolved.get());
        };

        PendingTask pendingTask = new PendingTask(delayedWork, roomDiagnostics, feedbackKey);
        this.pendingTasks.add(pendingTask);

        try {
            if (!this.accepting.get()) {
                pendingTask.cancel();
                this.diagnostics.log(context.room(), "Delayed scheduler rejected work during shutdown");
                return;
            }

            boolean nativeSignals = effects.stream()
                    .allMatch(effect -> effect
                                    instanceof
                                    com.eu.habbo.habbohotel.items.interactions.wired.effects.WiredEffectSendSignal
                                            sender
                            && sender.isNativeSignal());
            ScheduledTask scheduledTask = nativeSignals
                    ? scheduleSignalFrame(context.room(), pendingTask::run, remainingDelayMs)
                    : this.taskScheduler.schedule(pendingTask::run, remainingDelayMs);
            if (scheduledTask == null) {
                pendingTask.cancel();
                this.diagnostics.log(context.room(), "Delayed scheduler rejected work during shutdown");
                return;
            }

            pendingTask.attach(scheduledTask);
            if (!this.accepting.get()) {
                pendingTask.cancel();
            }
        } catch (RuntimeException exception) {
            pendingTask.cancel();
            this.diagnostics.log(
                    context.room(),
                    "Delayed scheduler failed to accept work: {}",
                    exception.getClass().getSimpleName());
        }
    }

    private static boolean prepareDelayedExecution(WiredDelayedExecutionSnapshot.Resolved resolved) {
        for (IWiredEffect effect : resolved.effects()) {
            if (effect != null && effect.requiresActor() && !resolved.context().hasActor()) {
                return false;
            }
        }
        return true;
    }

    int shutdown() {
        this.accepting.set(false);
        int cancelled = 0;

        for (PendingTask pendingTask : List.copyOf(this.pendingTasks)) {
            if (pendingTask.cancel()) {
                cancelled++;
            }
        }

        return cancelled;
    }

    private final class PendingTask implements Runnable {
        private final Runnable work;
        private final WiredRoomDiagnostics roomDiagnostics;
        private final AtomicInteger state = new AtomicInteger(TASK_PENDING);
        private final AtomicReference<ScheduledTask> scheduledTask = new AtomicReference<>();

        private final Object feedbackKey;

        private PendingTask(Runnable work, WiredRoomDiagnostics roomDiagnostics, Object feedbackKey) {
            this.work = work;
            this.roomDiagnostics = roomDiagnostics;
            this.feedbackKey = feedbackKey;
        }

        private void attach(ScheduledTask task) {
            this.scheduledTask.set(task);

            if (this.state.get() != TASK_PENDING) {
                cancelQuietly(task);
            }
        }

        @Override
        public void run() {
            if (!this.state.compareAndSet(TASK_PENDING, TASK_RUNNING)) {
                return;
            }

            pendingTasks.remove(this);
            if (this.feedbackKey != null) pendingFeedback.remove(this.feedbackKey);
            try {
                this.work.run();
            } finally {
                this.state.set(TASK_COMPLETED);
                this.roomDiagnostics.completeDelayedEvent();
            }
        }

        private boolean cancel() {
            if (!this.state.compareAndSet(TASK_PENDING, TASK_CANCELLED)) {
                return false;
            }

            ScheduledTask task = this.scheduledTask.get();
            if (task != null) {
                cancelQuietly(task);
            }
            pendingTasks.remove(this);
            if (this.feedbackKey != null) pendingFeedback.remove(this.feedbackKey);
            this.roomDiagnostics.completeDelayedEvent();
            return true;
        }

        private static void cancelQuietly(ScheduledTask task) {
            try {
                task.cancel();
            } catch (RuntimeException ignored) {
                // Executor cancellation must not strand diagnostics or prevent runtime cleanup.
                WiredCompatibilityDiagnostics.record(
                        WiredCompatibilityDiagnostics.FailurePoint.DELAYED_TASK_CANCEL, ignored);
            }
        }
    }
}
