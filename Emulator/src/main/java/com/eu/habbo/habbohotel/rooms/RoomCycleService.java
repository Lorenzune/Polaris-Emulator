package com.eu.habbo.habbohotel.rooms;

import com.eu.habbo.core.ConfigurationManager;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
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
    private final long slowNanos;
    private final LongSupplier nanoTime;
    private final ConcurrentHashMap<Integer, Long> lastSlowWarning = new ConcurrentHashMap<>();

    public RoomCycleService(int workerCount, int slowMs) {
        this(workerCount, slowMs, System::nanoTime);
    }

    RoomCycleService(int workerCount, int slowMs, LongSupplier nanoTime) {
        int count = Math.max(1, Math.min(MAX_WORKERS, workerCount));
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
        this.slowNanos = TimeUnit.MILLISECONDS.toNanos(Math.max(1, slowMs));
        this.nanoTime = nanoTime;
        LOGGER.info("Room cycles -> {} workers", count);
    }

    public static RoomCycleService fromConfig(ConfigurationManager config) {
        int configuredWorkers = config == null ? 0 : config.getInt(WORKERS_SETTING, 0);
        int slowMs = config == null ? DEFAULT_SLOW_MS : config.getInt(SLOW_MS_SETTING, DEFAULT_SLOW_MS);
        int workers = configuredWorkers > 0 ? configuredWorkers : defaultWorkers();
        return new RoomCycleService(workers, slowMs);
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
                "Room {} cycle took {} ms on worker {}; rooms on the same worker wait for it",
                room.getId(),
                TimeUnit.NANOSECONDS.toMillis(tookNanos),
                this.workerOf(room.getId()));
    }

    public void dispose() {
        for (ScheduledExecutorService worker : this.workers) {
            worker.shutdown();
        }
        for (ScheduledExecutorService worker : this.workers) {
            try {
                if (!worker.awaitTermination(5, TimeUnit.SECONDS)) {
                    worker.shutdownNow();
                }
            } catch (InterruptedException interrupted) {
                worker.shutdownNow();
                Thread.currentThread().interrupt();
            }
        }
        LOGGER.info("Room cycles -> Disposed!");
    }
}
