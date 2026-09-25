package com.eu.habbo.threading;

import com.eu.habbo.Emulator;
import com.eu.habbo.database.PersistenceExecutor;
import com.eu.habbo.habbohotel.users.HabboItem;
import io.netty.util.concurrent.DefaultThreadFactory;
import java.util.Set;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ThreadPooling {

    private static final Logger LOGGER = LoggerFactory.getLogger(ThreadPooling.class);

    public final int threads;
    private final ScheduledExecutorService scheduledPool;
    private final PersistenceExecutor persistenceExecutor;
    private final Set<HabboItem> pendingHabboItemPersistence = ConcurrentHashMap.newKeySet();
    private final ConcurrentLinkedQueue<HabboItem> deferredHabboItemPersistence = new ConcurrentLinkedQueue<>();
    private final AtomicBoolean itemPersistenceDrainScheduled = new AtomicBoolean();
    private volatile boolean canAdd;

    public ThreadPooling(Integer threads) {
        this(threads, null);
    }

    public ThreadPooling(Integer threads, PersistenceExecutor persistenceExecutor) {
        this.threads = threads;
        this.scheduledPool = new HabboExecutorService(this.threads, new DefaultThreadFactory("HabExec"));
        this.persistenceExecutor = persistenceExecutor;
        this.canAdd = true;
        LOGGER.info("Thread Pool -> Loaded!");
    }

    public ScheduledFuture<?> run(Runnable run) {
        try {
            if (this.canAdd) {
                return this.run(run, 0);
            } else {
                if (Emulator.isShuttingDown) {
                    run.run();
                }
            }
        } catch (Exception e) {
            LOGGER.error("Caught exception", e);
        }

        return null;
    }

    public ScheduledFuture<?> run(Runnable run, long delay) {
        try {
            if (this.canAdd) {
                return this.scheduledPool.schedule(
                        () -> {
                            try {
                                run.run();
                            } catch (Exception e) {
                                LOGGER.error("Caught exception", e);
                            }
                        },
                        delay,
                        TimeUnit.MILLISECONDS);
            } else {
                LOGGER.warn("Rejected delayed task during shutdown (delay: {} ms)", delay);
            }
        } catch (Exception e) {
            LOGGER.error("Caught exception", e);
        }

        return null;
    }

    public void runPersistence(Runnable task) {
        if (this.persistenceExecutor == null) {
            this.run(task);
            return;
        }

        this.persistenceExecutor.execute(task);
    }

    /**
     * Tries to queue persistence work without running it on the submitting room worker when full.
     * Returns false only while the bounded persistence queue is saturated; shutdown still drains
     * the work synchronously so accepted room state is not lost.
     */
    public boolean tryRunPersistence(Runnable task) {
        if (this.persistenceExecutor == null) {
            return this.run(task) != null;
        }

        if (this.persistenceExecutor.tryExecute(task)) {
            return true;
        }

        if (!this.persistenceExecutor.isAccepting()) {
            this.persistenceExecutor.execute(task);
            return true;
        }

        return false;
    }

    /**
     * Persists a room furni without allowing repeated moves to enqueue duplicate saves.
     * If it changes while a save is running, one follow-up save captures its latest state.
     */
    public void runHabboItemPersistence(HabboItem item) {
        if (item == null || !this.pendingHabboItemPersistence.add(item)) {
            return;
        }

        Runnable save = this.createHabboItemSave(item);

        if (this.persistenceExecutor == null) {
            this.runPersistence(save);
        } else if (!this.persistenceExecutor.tryExecute(save)) {
            if (!this.persistenceExecutor.isAccepting()) {
                this.runPersistence(save);
            } else {
                this.deferredHabboItemPersistence.offer(item);
                this.scheduleItemPersistenceDrain();
            }
        }
    }

    private Runnable createHabboItemSave(HabboItem item) {
        return () -> {
            long startingRevision = item.getPersistenceRevision();
            try {
                item.run();
            } finally {
                this.pendingHabboItemPersistence.remove(item);
                if (item.needsUpdate() || item.getPersistenceRevision() != startingRevision) {
                    this.runHabboItemPersistence(item);
                }
            }
        };
    }

    private void scheduleItemPersistenceDrain() {
        if (this.persistenceExecutor == null
                || this.deferredHabboItemPersistence.isEmpty()
                || !this.itemPersistenceDrainScheduled.compareAndSet(false, true)) {
            return;
        }

        try {
            this.scheduledPool.schedule(this::drainDeferredItemPersistence, 25, TimeUnit.MILLISECONDS);
        } catch (RuntimeException exception) {
            this.itemPersistenceDrainScheduled.set(false);
            LOGGER.warn("Could not schedule deferred furni persistence drain", exception);
        }
    }

    private void drainDeferredItemPersistence() {
        try {
            HabboItem item;
            while ((item = this.deferredHabboItemPersistence.peek()) != null) {
                if (!this.persistenceExecutor.tryExecute(this.createHabboItemSave(item))) {
                    return;
                }
                this.deferredHabboItemPersistence.poll();
            }
        } finally {
            this.itemPersistenceDrainScheduled.set(false);
            this.scheduleItemPersistenceDrain();
        }
    }

    public int getDeferredHabboItemPersistenceCount() {
        return this.deferredHabboItemPersistence.size();
    }

    public int getScheduledActiveCount() {
        return this.scheduledPool instanceof ScheduledThreadPoolExecutor executor
                ? executor.getActiveCount()
                : -1;
    }

    public int getScheduledPoolSize() {
        return this.scheduledPool instanceof ScheduledThreadPoolExecutor executor
                ? executor.getPoolSize()
                : -1;
    }

    public int getScheduledQueueDepth() {
        return this.scheduledPool instanceof ScheduledThreadPoolExecutor executor
                ? executor.getQueue().size()
                : -1;
    }

    public int getPersistenceActiveCount() {
        return this.persistenceExecutor != null ? this.persistenceExecutor.getActiveCount() : -1;
    }

    public int getPersistenceQueueDepth() {
        return this.persistenceExecutor != null ? this.persistenceExecutor.getQueueDepth() : -1;
    }

    public void shutDown() {
        this.shutDown(5, TimeUnit.SECONDS);
    }

    ShutdownResult shutDown(long timeout, TimeUnit unit) {
        this.canAdd = false;
        this.scheduledPool.shutdown();

        boolean terminatedGracefully = false;
        boolean terminated = false;
        boolean interrupted = false;
        int cancelledTasks = 0;

        try {
            terminatedGracefully = this.scheduledPool.awaitTermination(Math.max(0L, timeout), unit);
        } catch (InterruptedException exception) {
            interrupted = true;
        }

        if (!terminatedGracefully) {
            cancelledTasks = this.scheduledPool.shutdownNow().size();

            if (!interrupted) {
                try {
                    terminated = this.scheduledPool.awaitTermination(Math.max(0L, timeout), unit);
                } catch (InterruptedException exception) {
                    interrupted = true;
                }
            }
        } else {
            terminated = true;
        }

        long completedTasks = this.scheduledPool instanceof ScheduledThreadPoolExecutor executor
                ? executor.getCompletedTaskCount()
                : 0L;

        if (interrupted) {
            Thread.currentThread().interrupt();
        }

        ShutdownResult result =
                new ShutdownResult(terminatedGracefully, terminated, cancelledTasks, completedTasks, interrupted);

        if (terminatedGracefully) {
            LOGGER.info("Threading -> Disposed gracefully (completed tasks: {})", completedTasks);
        } else if (terminated) {
            LOGGER.warn(
                    "Threading -> Disposed after forced shutdown (completed tasks: {}, cancelled queued tasks: {})",
                    completedTasks,
                    cancelledTasks);
        } else {
            LOGGER.error(
                    "Threading -> Workers still active after forced shutdown (completed tasks: {}, cancelled queued tasks: {}, interrupted: {})",
                    completedTasks,
                    cancelledTasks,
                    interrupted);
        }

        return result;
    }

    record ShutdownResult(
            boolean terminatedGracefully,
            boolean terminated,
            int cancelledTasks,
            long completedTasks,
            boolean interrupted) {}

    public void setCanAdd(boolean canAdd) {
        this.canAdd = canAdd;
    }

    public ScheduledExecutorService getService() {
        return this.scheduledPool;
    }
}
