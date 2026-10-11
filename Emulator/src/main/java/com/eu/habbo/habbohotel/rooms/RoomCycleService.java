package com.eu.habbo.habbohotel.rooms;

import com.eu.habbo.core.ConfigurationManager;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Delayed;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.function.LongSupplier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Runs room cycles on a bounded set of workers, separate from the shared game task pool. */
public final class RoomCycleService {

    public static final String WORKERS_SETTING = "room.cycle.workers";
    public static final String SLOW_MS_SETTING = "room.cycle.slow_ms";
    public static final int CYCLE_MS = 500;
    public static final int MAX_WORKERS = 64;
    public static final int DEFAULT_SLOW_MS = 250;

    private static final Logger LOGGER = LoggerFactory.getLogger(RoomCycleService.class);
    private static final long SLOW_WARNING_INTERVAL_MS = 30_000L;
    private static final int MAX_SLOW_WARNING_KEYS = 10_000;

    private final ScheduledExecutorService[] workers;
    private final ScheduledThreadPoolExecutor roomWorkers;
    private final ScheduledThreadPoolExecutor slowRoomWorkers;
    private final boolean isolationEnabled;
    private final Set<RoomSchedule> schedules = ConcurrentHashMap.newKeySet();
    private volatile boolean disposed;
    private final long slowNanos;
    private final LongSupplier nanoTime;
    private final ConcurrentHashMap<Integer, Long> lastSlowWarning = new ConcurrentHashMap<>();

    public RoomCycleService(int workerCount, int slowMs) {
        this(workerCount, slowMs, System::nanoTime);
    }

    RoomCycleService(int workerCount, int slowMs, LongSupplier nanoTime) {
        this(workerCount, slowMs, nanoTime, true, 2);
    }

    private RoomCycleService(
            int workerCount, int slowMs, LongSupplier nanoTime, boolean isolationEnabled, int slowWorkers) {
        int count = Math.max(isolationEnabled ? 2 : 1, Math.min(MAX_WORKERS, workerCount));
        this.workers = new ScheduledExecutorService[count];
        for (int index = 0; index < count; index++) {
            ThreadFactory threadFactory = Thread.ofPlatform()
                    .name("RoomCycle-" + index + "-")
                    .daemon(true)
                    .factory();
            ScheduledThreadPoolExecutor worker = new ScheduledThreadPoolExecutor(1, threadFactory);
            worker.setRemoveOnCancelPolicy(true);
            worker.setExecuteExistingDelayedTasksAfterShutdownPolicy(false);
            worker.setContinueExistingPeriodicTasksAfterShutdownPolicy(false);
            this.workers[index] = worker;
        }
        this.isolationEnabled = isolationEnabled;
        this.roomWorkers = createPool(Math.max(2, count), "RoomCycleIndependent-");
        this.slowRoomWorkers = createPool(Math.max(1, Math.min(MAX_WORKERS, slowWorkers)), "RoomCycleSlow-");
        this.slowNanos = TimeUnit.MILLISECONDS.toNanos(Math.max(1, slowMs));
        this.nanoTime = nanoTime;
        LOGGER.info(
                "Room cycles -> {} normal workers, {} slow workers, isolation={}",
                count,
                slowRoomWorkers.getCorePoolSize(),
                isolationEnabled);
    }

    public static RoomCycleService fromConfig(ConfigurationManager config) {
        int configuredWorkers = config == null ? 0 : config.getInt(WORKERS_SETTING, 0);
        int slowMs = config == null ? DEFAULT_SLOW_MS : config.getInt(SLOW_MS_SETTING, DEFAULT_SLOW_MS);
        int workers = configuredWorkers > 0 ? configuredWorkers : defaultWorkers();
        boolean isolation = config == null || config.getBoolean("room.cycle.room.isolation", true);
        int slowWorkers = config == null ? 2 : config.getInt("room.cycle.slow.workers", 2);
        return new RoomCycleService(workers, slowMs, System::nanoTime, isolation, slowWorkers);
    }

    static int defaultWorkers() {
        return Math.max(2, Math.min(16, Runtime.getRuntime().availableProcessors()));
    }

    public int workerCount() {
        return this.workers.length;
    }

    int workerOf(int roomId) {
        return Math.floorMod(roomId, this.workers.length);
    }

    public ScheduledFuture<?> schedule(Room room) {
        if (isolationEnabled) {
            RoomSchedule schedule = new RoomSchedule(room);
            schedules.add(schedule);
            schedule.start();
            return schedule;
        }
        return this.workers[this.workerOf(room.getId())].scheduleAtFixedRate(
                () -> this.runCycle(room), CYCLE_MS, CYCLE_MS, TimeUnit.MILLISECONDS);
    }

    void runCycle(Room room) {
        long started = this.nanoTime.getAsLong();
        try {
            room.run();
        } catch (StackOverflowError error) {
            LOGGER.error("Room {} cycle overflowed its stack", room.getId(), error);
        } catch (VirtualMachineError error) {
            throw error;
        } catch (Throwable throwable) {
            LOGGER.error("Room {} cycle failed", room.getId(), throwable);
        }

        long took = this.nanoTime.getAsLong() - started;
        if (took > this.slowNanos) {
            this.warnSlow(room, took);
        }
    }

    private void warnSlow(Room room, long tookNanos) {
        long nowMs = TimeUnit.NANOSECONDS.toMillis(this.nanoTime.getAsLong());
        Long last = this.lastSlowWarning.get(room.getId());
        if (last != null && nowMs - last < SLOW_WARNING_INTERVAL_MS) {
            return;
        }
        if (this.lastSlowWarning.size() >= MAX_SLOW_WARNING_KEYS) {
            this.lastSlowWarning.clear();
        }
        this.lastSlowWarning.put(room.getId(), nowMs);
        LOGGER.warn(
                "Room {} cycle took {} ms (legacyShard={}, isolation={})",
                room.getId(),
                TimeUnit.NANOSECONDS.toMillis(tookNanos),
                this.workerOf(room.getId()),
                isolationEnabled);
    }

    private static ScheduledThreadPoolExecutor createPool(int count, String name) {
        ScheduledThreadPoolExecutor pool = new ScheduledThreadPoolExecutor(
                count, Thread.ofPlatform().name(name, 0).daemon(true).factory());
        pool.setRemoveOnCancelPolicy(true);
        pool.setExecuteExistingDelayedTasksAfterShutdownPolicy(false);
        pool.setContinueExistingPeriodicTasksAfterShutdownPolicy(false);
        return pool;
    }

    /** Keeps fixed-rate deadlines and cancellation while switching pools only between cycles. */
    private final class RoomSchedule implements ScheduledFuture<Object>, Runnable {
        private final Room room;
        private final CompletableFuture<Object> completion = new CompletableFuture<>();
        private long deadline = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(CYCLE_MS);
        private ScheduledFuture<?> task;
        private boolean slow;

        RoomSchedule(Room room) {
            this.room = room;
        }

        synchronized void start() {
            if (completion.isDone()) return;
            if (disposed) {
                cancel(false);
                return;
            }
            try {
                task = (slow ? slowRoomWorkers : roomWorkers)
                        .schedule(this, Math.max(0, deadline - System.nanoTime()), TimeUnit.NANOSECONDS);
            } catch (RuntimeException rejected) {
                completion.completeExceptionally(rejected);
                schedules.remove(this);
            }
        }

        @Override
        public void run() {
            synchronized (this) {
                if (completion.isDone() || disposed) return;
            }
            long started = nanoTime.getAsLong();
            try {
                runCycle(room);
            } catch (Throwable fatal) {
                completion.completeExceptionally(fatal);
                schedules.remove(this);
                throw fatal;
            }
            synchronized (this) {
                if (completion.isDone() || disposed) return;
                slow |= nanoTime.getAsLong() - started > slowNanos;
                deadline += TimeUnit.MILLISECONDS.toNanos(CYCLE_MS);
                start();
            }
        }

        @Override
        public synchronized boolean cancel(boolean interrupt) {
            if (completion.isDone() || !completion.cancel(false)) return false;
            if (task != null) task.cancel(interrupt);
            schedules.remove(this);
            return true;
        }

        @Override
        public boolean isCancelled() {
            return completion.isCancelled();
        }

        @Override
        public boolean isDone() {
            return completion.isDone();
        }

        @Override
        public Object get() throws InterruptedException, ExecutionException {
            return completion.get();
        }

        @Override
        public Object get(long timeout, TimeUnit unit)
                throws InterruptedException, ExecutionException, TimeoutException {
            return completion.get(timeout, unit);
        }

        @Override
        public synchronized long getDelay(TimeUnit unit) {
            return unit.convert(deadline - System.nanoTime(), TimeUnit.NANOSECONDS);
        }

        @Override
        public int compareTo(Delayed other) {
            return Long.compare(getDelay(TimeUnit.NANOSECONDS), other.getDelay(TimeUnit.NANOSECONDS));
        }
    }

    public void dispose() {
        disposed = true;
        for (RoomSchedule schedule : schedules) schedule.cancel(false);
        java.util.List<ScheduledExecutorService> pools = new java.util.ArrayList<>(java.util.Arrays.asList(workers));
        pools.add(roomWorkers);
        pools.add(slowRoomWorkers);
        for (ScheduledExecutorService pool : pools) pool.shutdown();
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
        for (ScheduledExecutorService pool : pools) {
            try {
                if (!pool.awaitTermination(Math.max(0, deadline - System.nanoTime()), TimeUnit.NANOSECONDS)) {
                    pool.shutdownNow();
                }
            } catch (InterruptedException interrupted) {
                pool.shutdownNow();
                Thread.currentThread().interrupt();
            }
        }
        LOGGER.info("Room cycles -> Disposed!");
    }
}
