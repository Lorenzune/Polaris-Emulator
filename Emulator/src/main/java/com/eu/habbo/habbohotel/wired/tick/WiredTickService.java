package com.eu.habbo.habbohotel.wired.tick;

import com.eu.habbo.Emulator;
import com.eu.habbo.habbohotel.rooms.Room;
import com.eu.habbo.threading.ThreadPooling;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Centralized tick service for all wired timing operations.
 *
 * <p>This version keeps a single global tick clock, but distributes room processing
 * across independent room turns. Slow rooms use a separate pool after completing
 * their current turn. The legacy shard scheduler remains available for rollback.</p>
 */
public final class WiredTickService {

    private static final Logger LOGGER = LoggerFactory.getLogger(WiredTickService.class);

    public static final int DEFAULT_TICK_INTERVAL_MS = 50;
    public static final int MIN_TICK_INTERVAL_MS = 10;
    public static final int MAX_TICK_INTERVAL_MS = 500;

    public static final int DEFAULT_WORKER_COUNT =
            Math.max(2, Math.min(8, Runtime.getRuntime().availableProcessors()));
    public static final int MIN_WORKER_COUNT = 1;
    public static final int MAX_WORKER_COUNT = 32;

    public static final long SLOW_TICKABLE_THRESHOLD_MS = 100L;
    public static final long SLOW_ROOM_THRESHOLD_MS = 50L;
    public static final long SLOW_SHARD_THRESHOLD_MS = 250L;

    private static volatile WiredTickService instance;

    private int tickIntervalMs = DEFAULT_TICK_INTERVAL_MS;
    private boolean debugEnabled = false;
    private int threadPriority = Thread.NORM_PRIORITY + 1;
    private int workerCount = DEFAULT_WORKER_COUNT;
    private boolean roomIsolationEnabled = true;
    private int slowWorkerCount = 2;
    private ExecutorService roomWorkers;
    private ExecutorService slowRoomWorkers;
    private volatile RoomWiredTickDispatcher roomDispatcher;

    /** Global logical tick counter shared by every shard. */
    private final AtomicLong tickCount = new AtomicLong(0);

    /** Schedules the global logical ticks. */
    private ScheduledExecutorService coordinator;

    /** One single-thread executor per shard, preserving order inside the shard. */
    private ExecutorService[] shardExecutors;

    /** Highest logical tick requested for each shard. */
    private AtomicLong[] shardRequestedTicks;

    /** Last logical tick fully processed by each shard. */
    private AtomicLong[] shardProcessedTicks;

    /** Whether a shard worker loop is currently scheduled/running. */
    private AtomicBoolean[] shardScheduled;

    private final ConcurrentHashMap<Integer, Set<WiredTickable>>[] shardRoomTickables;
    private final AtomicBoolean running;

    @SuppressWarnings("unchecked")
    private WiredTickService() {
        this.shardRoomTickables = new ConcurrentHashMap[MAX_WORKER_COUNT];
        for (int i = 0; i < MAX_WORKER_COUNT; i++) {
            this.shardRoomTickables[i] = new ConcurrentHashMap<>();
        }
        this.running = new AtomicBoolean(false);
    }

    private void loadConfiguration() {
        com.eu.habbo.core.ConfigurationManager config = Emulator.getConfig();
        int configuredInterval = config.getInt("wired.tick.interval.ms", DEFAULT_TICK_INTERVAL_MS);
        this.tickIntervalMs = Math.max(MIN_TICK_INTERVAL_MS, Math.min(MAX_TICK_INTERVAL_MS, configuredInterval));

        if (configuredInterval != this.tickIntervalMs) {
            LOGGER.warn(
                    "wired.tick.interval.ms value {} is out of range [{}-{}], using {}",
                    configuredInterval,
                    MIN_TICK_INTERVAL_MS,
                    MAX_TICK_INTERVAL_MS,
                    this.tickIntervalMs);
        }

        this.debugEnabled = config.getBoolean("wired.tick.debug", false);
        this.roomIsolationEnabled = config.getBoolean("wired.tick.room.isolation", true);
        this.slowWorkerCount = Math.max(1, Math.min(MAX_WORKER_COUNT, config.getInt("wired.tick.slow.workers", 2)));

        int configuredPriority = config.getInt("wired.tick.thread.priority", Thread.NORM_PRIORITY + 1);
        this.threadPriority = Math.max(Thread.MIN_PRIORITY, Math.min(Thread.MAX_PRIORITY, configuredPriority));

        int configuredWorkers = config.getInt("wired.tick.workers", DEFAULT_WORKER_COUNT);
        this.workerCount = Math.max(MIN_WORKER_COUNT, Math.min(MAX_WORKER_COUNT, configuredWorkers));

        if (roomIsolationEnabled) this.workerCount = Math.max(2, this.workerCount);

        if (configuredWorkers != this.workerCount) {
            LOGGER.warn(
                    "wired.tick.workers value {} is out of range [{}-{}], using {}",
                    configuredWorkers,
                    MIN_WORKER_COUNT,
                    MAX_WORKER_COUNT,
                    this.workerCount);
        }
    }

    public int getTickIntervalMs() {
        return tickIntervalMs;
    }

    public boolean isDebugEnabled() {
        return debugEnabled;
    }

    public int getWorkerCount() {
        return workerCount;
    }

    public static WiredTickService getInstance() {
        if (instance == null) {
            synchronized (WiredTickService.class) {
                if (instance == null) {
                    instance = new WiredTickService();
                }
            }
        }
        return instance;
    }

    public synchronized void start() {
        if (running.get()) {
            LOGGER.warn("WiredTickService already running");
            return;
        }

        loadConfiguration();

        LOGGER.info(
                "Starting WiredTickService with {}ms tick interval (workers={}, debug={}, priority={})...",
                tickIntervalMs,
                workerCount,
                debugEnabled,
                threadPriority);

        this.coordinator = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "WiredTickCoordinator");
            t.setDaemon(true);
            t.setPriority(threadPriority);
            return t;
        });

        this.shardExecutors = new ExecutorService[workerCount];
        this.shardRequestedTicks = new AtomicLong[workerCount];
        this.shardProcessedTicks = new AtomicLong[workerCount];
        this.shardScheduled = new AtomicBoolean[workerCount];

        for (int i = 0; !roomIsolationEnabled && i < workerCount; i++) {
            final int shardIndex = i;
            this.shardExecutors[i] = Executors.newSingleThreadExecutor(r -> {
                Thread t = new Thread(r, "WiredTickShard-" + shardIndex);
                t.setDaemon(true);
                t.setPriority(threadPriority);
                return t;
            });
            this.shardRequestedTicks[i] = new AtomicLong(0L);
            this.shardProcessedTicks[i] = new AtomicLong(0L);
            this.shardScheduled[i] = new AtomicBoolean(false);
        }

        if (roomIsolationEnabled) {
            this.roomWorkers = createRoomWorkers(Math.max(2, workerCount), "WiredRoomWorker");
            this.slowRoomWorkers = createRoomWorkers(slowWorkerCount, "WiredSlowRoomWorker");
            this.roomDispatcher = new RoomWiredTickDispatcher(
                    roomWorkers,
                    slowRoomWorkers,
                    this::processIsolatedRoomTick,
                    (roomId, error) -> LOGGER.error("WIRED room worker failed in room {}", roomId, error),
                    SLOW_ROOM_THRESHOLD_MS,
                    System::nanoTime);
            LOGGER.info("WIRED room isolation enabled: normalWorkers={}, slowWorkers={}", workerCount, slowWorkerCount);
        }

        this.tickCount.set(0L);
        running.set(true);

        this.coordinator.scheduleAtFixedRate(
                () -> {
                    try {
                        dispatchTick();
                    } catch (Throwable t) {
                        LOGGER.error("WiredTickService fatal coordinator error", t);
                    }
                },
                tickIntervalMs,
                tickIntervalMs,
                TimeUnit.MILLISECONDS);

        LOGGER.info("WiredTickService started successfully");
    }

    public synchronized void stop() {
        if (!running.get()) {
            return;
        }

        LOGGER.info("Stopping WiredTickService...");
        running.set(false);
        RoomWiredTickDispatcher dispatcher = roomDispatcher;
        if (dispatcher != null) dispatcher.close();

        if (coordinator != null) {
            coordinator.shutdown();
            try {
                if (!coordinator.awaitTermination(5, TimeUnit.SECONDS)) {
                    coordinator.shutdownNow();
                }
            } catch (InterruptedException e) {
                coordinator.shutdownNow();
                Thread.currentThread().interrupt();
            }
            coordinator = null;
        }

        if (shardExecutors != null) {
            for (ExecutorService executor : shardExecutors) {
                if (executor != null) {
                    executor.shutdown();
                }
            }

            for (ExecutorService executor : shardExecutors) {
                if (executor == null) {
                    continue;
                }
                try {
                    if (!executor.awaitTermination(5, TimeUnit.SECONDS)) {
                        executor.shutdownNow();
                    }
                } catch (InterruptedException e) {
                    executor.shutdownNow();
                    Thread.currentThread().interrupt();
                }
            }
        }

        stopRoomWorkers();
        roomDispatcher = null;
        shardExecutors = null;
        shardRequestedTicks = null;
        shardProcessedTicks = null;
        shardScheduled = null;

        for (int i = 0; i < MAX_WORKER_COUNT; i++) {
            shardRoomTickables[i].clear();
        }
        LOGGER.info("WiredTickService stopped");
    }

    public boolean isRunning() {
        return running.get();
    }

    public void register(Room room, WiredTickable tickable) {
        if (room == null || tickable == null) {
            return;
        }

        int roomId = room.getId();
        int shardIndex = getShardIndex(roomId);
        Set<WiredTickable> tickables =
                shardRoomTickables[shardIndex].computeIfAbsent(roomId, k -> ConcurrentHashMap.newKeySet());

        if (tickables.add(tickable)) {
            tickable.onRegistered(room, System.currentTimeMillis());
        }
    }

    public void unregister(Room room, WiredTickable tickable) {
        if (room == null || tickable == null) {
            return;
        }

        int roomId = room.getId();
        int shardIndex = getShardIndex(roomId);
        Set<WiredTickable> tickables = shardRoomTickables[shardIndex].get(roomId);

        if (tickables != null) {
            if (tickables.remove(tickable)) {
                tickable.onUnregistered(room);
            }

            if (tickables.isEmpty()) {
                shardRoomTickables[shardIndex].remove(roomId);
                RoomWiredTickDispatcher dispatcher = roomDispatcher;
                if (dispatcher != null) dispatcher.forget(roomId);
            }
        }
    }

    public void unregister(int roomId, int tickableId) {
        int shardIndex = getShardIndex(roomId);
        Set<WiredTickable> tickables = shardRoomTickables[shardIndex].get(roomId);

        if (tickables != null) {
            tickables.removeIf(t -> {
                if (t.getId() == tickableId) {
                    Room room = Emulator.getGameEnvironment().getRoomManager().getRoom(roomId);
                    if (room != null) {
                        t.onUnregistered(room);
                    }
                    return true;
                }
                return false;
            });

            if (tickables.isEmpty()) {
                shardRoomTickables[shardIndex].remove(roomId);
                RoomWiredTickDispatcher dispatcher = roomDispatcher;
                if (dispatcher != null) dispatcher.forget(roomId);
            }
        }
    }

    public void unregisterRoom(Room room) {
        if (room == null) {
            return;
        }

        int roomId = room.getId();
        int shardIndex = getShardIndex(roomId);
        Set<WiredTickable> tickables = shardRoomTickables[shardIndex].remove(roomId);
        RoomWiredTickDispatcher dispatcher = roomDispatcher;
        if (dispatcher != null) dispatcher.forget(roomId);

        if (tickables != null) {
            for (WiredTickable tickable : tickables) {
                try {
                    if (tickable != null) {
                        tickable.onUnregistered(room);
                    }
                } catch (Throwable t) {
                    LOGGER.error(
                            "Error unregistering tickable {} from room {}",
                            tickable != null ? tickable.getId() : -1,
                            room.getId(),
                            t);
                }
            }
            LOGGER.debug("Unregistered {} tickables from room {}", tickables.size(), room.getId());
        }
    }

    public void resetRoomTimers(Room room) {
        if (room == null) {
            return;
        }

        int roomId = room.getId();
        int shardIndex = getShardIndex(roomId);
        Set<WiredTickable> tickables = shardRoomTickables[shardIndex].get(roomId);

        if (tickables != null) {
            for (WiredTickable tickable : tickables) {
                try {
                    if (tickable != null) {
                        tickable.resetTimer();
                    }
                } catch (Throwable e) {
                    LOGGER.error(
                            "Error resetting timer for tickable {} in room {}",
                            tickable != null ? tickable.getId() : -1,
                            room.getId(),
                            e);
                }
            }
        }
    }

    public int getTickableCount(int roomId) {
        int shardIndex = getShardIndex(roomId);
        Set<WiredTickable> tickables = shardRoomTickables[shardIndex].get(roomId);
        return tickables != null ? tickables.size() : 0;
    }

    public int getTotalTickableCount() {
        int count = 0;
        for (int i = 0; i < MAX_WORKER_COUNT; i++) {
            count += shardRoomTickables[i].values().stream().mapToInt(Set::size).sum();
        }
        return count;
    }

    public int getActiveRoomCount() {
        int count = 0;
        for (int i = 0; i < MAX_WORKER_COUNT; i++) {
            count += shardRoomTickables[i].size();
        }
        return count;
    }

    public long getTickCount() {
        return tickCount.get();
    }

    private void dispatchTick() {
        if (!running.get() || Emulator.isShuttingDown) {
            return;
        }

        long currentTick = tickCount.incrementAndGet();
        RoomWiredTickDispatcher dispatcher = roomDispatcher;
        if (dispatcher != null) {
            if (debugEnabled && currentTick % Math.max(1, 1000 / tickIntervalMs) == 0) {
                LOGGER.debug(
                        "WIRED room isolation: activeLanes={}, slowRooms={}, normalWorkers={}, slowWorkers={}",
                        dispatcher.roomCount(),
                        dispatcher.slowRoomCount(),
                        workerCount,
                        slowWorkerCount);
            }
            for (int shard = 0; shard < workerCount; shard++) {
                for (Integer roomId : shardRoomTickables[shard].keySet()) {
                    dispatcher.request(roomId, currentTick);
                }
            }
            return;
        }

        for (int shardIndex = 0; shardIndex < workerCount; shardIndex++) {
            shardRequestedTicks[shardIndex].set(currentTick);
            scheduleShardIfNeeded(shardIndex);
        }
    }

    private void scheduleShardIfNeeded(int shardIndex) {
        if (!running.get() || shardExecutors == null) {
            return;
        }

        if (shardScheduled[shardIndex].compareAndSet(false, true)) {
            shardExecutors[shardIndex].execute(() -> runShardLoop(shardIndex));
        }
    }

    private void runShardLoop(int shardIndex) {
        try {
            while (running.get() && !Emulator.isShuttingDown) {
                long nextTick = shardProcessedTicks[shardIndex].get() + 1L;
                long requestedTick = shardRequestedTicks[shardIndex].get();

                if (nextTick > requestedTick) {
                    break;
                }

                // If lagging by more than 5 ticks (250ms), skip intermediate ticks to avoid CPU starvation
                if (requestedTick - nextTick > 5) {
                    nextTick = requestedTick - 5;
                    shardProcessedTicks[shardIndex].set(nextTick);
                }

                processShardTick(shardIndex, nextTick);
                shardProcessedTicks[shardIndex].set(nextTick);
            }
        } catch (Throwable t) {
            LOGGER.error("Fatal error in WiredTick shard {}", shardIndex, t);
        } finally {
            shardScheduled[shardIndex].set(false);
            if (running.get() && shardProcessedTicks[shardIndex].get() < shardRequestedTicks[shardIndex].get()) {
                scheduleShardIfNeeded(shardIndex);
            }
        }
    }

    private void processShardTick(int shardIndex, long currentTick) {
        long shardStart = System.currentTimeMillis();
        int processedTickables = 0;
        int processedRooms = 0;

        for (Map.Entry<Integer, Set<WiredTickable>> entry : shardRoomTickables[shardIndex].entrySet()) {
            int roomId = entry.getKey();
            Set<WiredTickable> tickables = entry.getValue();
            if (tickables == null || tickables.isEmpty()) {
                continue;
            }

            processedRooms++;
            processedTickables += tickables.size();
            processRoomTick(shardIndex, roomId, tickables, currentTick);
        }

        long shardDuration = System.currentTimeMillis() - shardStart;
        if (shardDuration > SLOW_SHARD_THRESHOLD_MS) {
            ThreadPooling pool = Emulator.getThreading();
            LOGGER.warn(
                    "Slow wired shard tick: shard={}, tick={}, rooms={}, tickables={}, took={}ms, runtimePoolActive={}, runtimePoolSize={}, runtimeQueue={}, persistenceActive={}, persistenceQueue={}, deferredItemSaves={}",
                    shardIndex,
                    currentTick,
                    processedRooms,
                    processedTickables,
                    shardDuration,
                    pool != null ? pool.getScheduledActiveCount() : -1,
                    pool != null ? pool.getScheduledPoolSize() : -1,
                    pool != null ? pool.getScheduledQueueDepth() : -1,
                    pool != null ? pool.getPersistenceActiveCount() : -1,
                    pool != null ? pool.getPersistenceQueueDepth() : -1,
                    pool != null ? pool.getDeferredHabboItemPersistenceCount() : -1);
        }

        if (debugEnabled && processedTickables > 0) {
            LOGGER.debug(
                    "Wired shard tick completed: shard={}, tick={}, rooms={}, tickables={}, took={}ms",
                    shardIndex,
                    currentTick,
                    processedRooms,
                    processedTickables,
                    shardDuration);
        }
    }

    private void processIsolatedRoomTick(int roomId, long currentTick) {
        if (!running.get()) return;
        int shardIndex = getShardIndex(roomId);
        Set<WiredTickable> tickables = shardRoomTickables[shardIndex].get(roomId);
        if (tickables != null && !tickables.isEmpty()) {
            processRoomTick(shardIndex, roomId, tickables, currentTick);
        }
    }

    private void processRoomTick(int shardIndex, int roomId, Set<WiredTickable> tickables, long currentTick) {
        Room room = Emulator.getGameEnvironment().getRoomManager().getRoom(roomId);
        if (room == null || !room.isLoaded()) {
            return;
        }

        if (room.getCurrentHabbos().isEmpty() && room.getCurrentBots().isEmpty()) {
            return;
        }

        long roomStart = System.currentTimeMillis();

        for (WiredTickable tickable : tickables) {
            long tickableStart = System.currentTimeMillis();

            if (tickable == null) {
                continue;
            }

            try {
                if (tickable.getRoomId() != roomId) {
                    unregister(roomId, tickable.getId());
                    continue;
                }

                tickable.onWiredTick(room, currentTick, tickIntervalMs);

                long tickableDuration = System.currentTimeMillis() - tickableStart;
                if (tickableDuration > SLOW_TICKABLE_THRESHOLD_MS) {
                    ThreadPooling pool = Emulator.getThreading();
                    LOGGER.warn(
                            "Slow wired tickable: shard={}, room={}, tick={}, tickableId={}, class={}, took={}ms, runtimePoolActive={}, runtimePoolSize={}, runtimeQueue={}, persistenceActive={}, persistenceQueue={}, deferredItemSaves={}",
                            shardIndex,
                            roomId,
                            currentTick,
                            tickable.getId(),
                            tickable.getClass().getName(),
                            tickableDuration,
                            pool != null ? pool.getScheduledActiveCount() : -1,
                            pool != null ? pool.getScheduledPoolSize() : -1,
                            pool != null ? pool.getScheduledQueueDepth() : -1,
                            pool != null ? pool.getPersistenceActiveCount() : -1,
                            pool != null ? pool.getPersistenceQueueDepth() : -1,
                            pool != null ? pool.getDeferredHabboItemPersistenceCount() : -1);
                }
            } catch (Throwable t) {
                long tickableDuration = System.currentTimeMillis() - tickableStart;
                LOGGER.error(
                        "Error in wired tick for tickable {} in room {} after {}ms",
                        tickable.getId(),
                        roomId,
                        tickableDuration,
                        t);
            }
        }

        long roomDuration = System.currentTimeMillis() - roomStart;
        if (roomDuration > SLOW_ROOM_THRESHOLD_MS) {
            ThreadPooling pool = Emulator.getThreading();
            LOGGER.warn(
                    "Slow wired room tick: shard={}, room={}, tick={}, tickables={}, took={}ms, runtimePoolActive={}, runtimePoolSize={}, runtimeQueue={}, persistenceActive={}, persistenceQueue={}, deferredItemSaves={}",
                    shardIndex,
                    roomId,
                    currentTick,
                    tickables.size(),
                    roomDuration,
                    pool != null ? pool.getScheduledActiveCount() : -1,
                    pool != null ? pool.getScheduledPoolSize() : -1,
                    pool != null ? pool.getScheduledQueueDepth() : -1,
                    pool != null ? pool.getPersistenceActiveCount() : -1,
                    pool != null ? pool.getPersistenceQueueDepth() : -1,
                    pool != null ? pool.getDeferredHabboItemPersistenceCount() : -1);
        }
    }

    private ExecutorService createRoomWorkers(int count, String name) {
        java.util.concurrent.atomic.AtomicInteger sequence = new java.util.concurrent.atomic.AtomicInteger();
        return Executors.newFixedThreadPool(count, task -> {
            Thread thread = new Thread(task, name + "-" + sequence.incrementAndGet());
            thread.setDaemon(true);
            thread.setPriority(threadPriority);
            return thread;
        });
    }

    private void stopRoomWorkers() {
        ExecutorService[] pools = {roomWorkers, slowRoomWorkers};
        for (ExecutorService pool : pools) if (pool != null) pool.shutdown();
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
        for (ExecutorService pool : pools) {
            if (pool == null) continue;
            try {
                if (!pool.awaitTermination(Math.max(0, deadline - System.nanoTime()), TimeUnit.NANOSECONDS)) {
                    pool.shutdownNow();
                }
            } catch (InterruptedException interrupted) {
                pool.shutdownNow();
                Thread.currentThread().interrupt();
            }
        }
        roomWorkers = null;
        slowRoomWorkers = null;
    }

    private int getShardIndex(int roomId) {
        return Math.floorMod(roomId, workerCount);
    }
}
