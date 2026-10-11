package com.eu.habbo.habbohotel.wired.tick;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.AbstractExecutorService;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.Test;

class RoomWiredTickDispatcherTest {
    @Test
    void notificationsHaveOneOutstandingTurnAndKeepTickOrder() {
        ManualPool normal = new ManualPool(), slow = new ManualPool();
        List<Long> ticks = new ArrayList<>();
        var dispatcher = dispatcher(normal, slow, (id, tick) -> ticks.add(tick));
        dispatcher.request(7, 1);
        dispatcher.request(7, 2);
        dispatcher.request(7, 3);
        dispatcher.request(7, 3);
        assertEquals(1, normal.tasks.size());
        normal.drain();
        assertEquals(List.of(1L, 2L, 3L), ticks);
        assertEquals(0, dispatcher.pendingTicks(7));
    }

    @Test
    void catchUpYieldsToOtherRoomsAfterEachCompleteTurn() {
        ManualPool normal = new ManualPool(), slow = new ManualPool();
        List<Integer> rooms = new ArrayList<>();
        var dispatcher = dispatcher(normal, slow, (id, tick) -> rooms.add(id));
        dispatcher.request(0, 1);
        dispatcher.request(0, 4);
        dispatcher.request(2, 1);
        normal.drain();
        assertEquals(List.of(0, 2, 0, 0, 0), rooms);
    }

    @Test
    void clockCatchUpRetainsTheExistingSixNotificationWindow() {
        ManualPool normal = new ManualPool(), slow = new ManualPool();
        List<Long> ticks = new ArrayList<>();
        var dispatcher = dispatcher(normal, slow, (id, tick) -> ticks.add(tick));
        dispatcher.request(7, 1);
        normal.next();
        dispatcher.request(7, 25);
        normal.drain();
        assertEquals(List.of(1L, 20L, 21L, 22L, 23L, 24L, 25L), ticks);
    }

    @Test
    void slowRoomMovesPoolsOnlyAfterItsTurnAndRemainsSerial() {
        ManualPool normal = new ManualPool(), slow = new ManualPool();
        AtomicLong clock = new AtomicLong();
        List<Long> ticks = new ArrayList<>();
        var dispatcher = new RoomWiredTickDispatcher(
                normal,
                slow,
                (id, tick) -> {
                    ticks.add(tick);
                    clock.addAndGet(51_000_000L);
                },
                (id, error) -> fail(error),
                50,
                clock::get);
        dispatcher.request(3, 1);
        dispatcher.request(3, 2);
        normal.next();
        assertEquals(List.of(1L), ticks);
        assertEquals(0, normal.tasks.size());
        assertEquals(1, slow.tasks.size());
        assertEquals(1, dispatcher.slowRoomCount());
        assertEquals(51_000_000L, dispatcher.maxTurnNanos(3));
        dispatcher.request(3, 3);
        assertEquals(1, slow.tasks.size());
        slow.drain();
        assertEquals(List.of(1L, 2L, 3L), ticks);
    }

    @Test
    void blockedRoomDoesNotBlockAnotherRoomFromTheSameLegacyShard() throws Exception {
        ExecutorService normal = Executors.newFixedThreadPool(2), slow = Executors.newFixedThreadPool(1);
        CountDownLatch blocked = new CountDownLatch(1),
                release = new CountDownLatch(1),
                otherRan = new CountDownLatch(1);
        var dispatcher = dispatcher(normal, slow, (id, tick) -> {
            if (id == 0) {
                blocked.countDown();
                await(release);
            } else otherRan.countDown();
        });
        try {
            dispatcher.request(0, 1);
            assertTrue(blocked.await(2, TimeUnit.SECONDS));
            dispatcher.request(2, 1); // 0 and 2 used to share shard 0 with two workers.
            assertTrue(otherRan.await(2, TimeUnit.SECONDS));
        } finally {
            release.countDown();
            dispatcher.close();
            normal.shutdownNow();
            slow.shutdownNow();
        }
    }

    @Test
    void blockedSlowPoolCannotConsumeNormalRoomWorkers() throws Exception {
        ExecutorService normal = Executors.newFixedThreadPool(2), slow = Executors.newFixedThreadPool(1);
        AtomicLong clock = new AtomicLong();
        CountDownLatch firstCompleted = new CountDownLatch(1), slowStarted = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1), fastRan = new CountDownLatch(1);
        var dispatcher = new RoomWiredTickDispatcher(
                normal,
                slow,
                (id, tick) -> {
                    if (id == 0 && tick == 1) {
                        clock.addAndGet(51_000_000L);
                        firstCompleted.countDown();
                    } else if (id == 0) {
                        slowStarted.countDown();
                        await(release);
                    } else fastRan.countDown();
                },
                (id, error) -> fail(error),
                50,
                clock::get);
        try {
            dispatcher.request(0, 1);
            assertTrue(firstCompleted.await(2, TimeUnit.SECONDS));
            dispatcher.request(0, 2);
            assertTrue(slowStarted.await(2, TimeUnit.SECONDS));
            dispatcher.request(2, 2);
            assertTrue(fastRan.await(2, TimeUnit.SECONDS));
        } finally {
            release.countDown();
            dispatcher.close();
            normal.shutdownNow();
            slow.shutdownNow();
        }
    }

    @Test
    void forgetAndReregisterCannotOverlapAnExistingRoomTurn() throws Exception {
        ExecutorService normal = Executors.newFixedThreadPool(2), slow = Executors.newFixedThreadPool(1);
        CountDownLatch started = new CountDownLatch(1),
                release = new CountDownLatch(1),
                finished = new CountDownLatch(1);
        AtomicInteger active = new AtomicInteger(), maximum = new AtomicInteger();
        List<Long> ticks = new java.util.concurrent.CopyOnWriteArrayList<>();
        var dispatcher = dispatcher(normal, slow, (id, tick) -> {
            maximum.accumulateAndGet(active.incrementAndGet(), Math::max);
            try {
                ticks.add(tick);
                if (tick == 1) {
                    started.countDown();
                    await(release);
                } else if (tick == 25) finished.countDown();
            } finally {
                active.decrementAndGet();
            }
        });
        try {
            dispatcher.request(5, 1);
            assertTrue(started.await(2, TimeUnit.SECONDS));
            dispatcher.forget(5);
            dispatcher.request(5, 25);
            release.countDown();
            assertTrue(finished.await(2, TimeUnit.SECONDS));
            assertEquals(1, maximum.get());
            assertEquals(List.of(1L, 25L), ticks);
        } finally {
            release.countDown();
            dispatcher.close();
            normal.shutdownNow();
            slow.shutdownNow();
        }
    }

    @Test
    void reregisteringQueuedRoomDoesNotReplayClockFromItsPreviousRegistration() {
        ManualPool normal = new ManualPool(), slow = new ManualPool();
        List<Long> ticks = new ArrayList<>();
        var dispatcher = dispatcher(normal, slow, (id, tick) -> ticks.add(tick));
        dispatcher.request(9, 1);
        dispatcher.request(9, 9);
        dispatcher.forget(9);
        dispatcher.request(9, 25);
        normal.drain();
        assertEquals(List.of(25L), ticks);
    }

    @Test
    void forgettingQueuedRoomAndShutdownReleaseLanesWithoutExecutingOldCallbacks() {
        ManualPool normal = new ManualPool(), slow = new ManualPool();
        AtomicInteger calls = new AtomicInteger();
        var dispatcher = dispatcher(normal, slow, (id, tick) -> calls.incrementAndGet());
        dispatcher.request(1, 1);
        dispatcher.forget(1);
        normal.drain();
        assertEquals(0, calls.get());
        assertEquals(0, dispatcher.roomCount());
        dispatcher.request(2, 1);
        dispatcher.close();
        dispatcher.request(3, 2);
        normal.drain();
        assertEquals(0, calls.get());
        assertEquals(0, dispatcher.roomCount());
    }

    @Test
    void aFailedRoomTurnDoesNotStrandItOrOtherRooms() {
        ManualPool normal = new ManualPool(), slow = new ManualPool();
        List<Integer> calls = new ArrayList<>(), errors = new ArrayList<>();
        var dispatcher = new RoomWiredTickDispatcher(
                normal,
                slow,
                (id, tick) -> {
                    calls.add(id);
                    if (tick == 1 && id == 1) throw new IllegalStateException("fixture");
                },
                (id, error) -> errors.add(id),
                50,
                () -> 0);
        dispatcher.request(1, 1);
        dispatcher.request(1, 2);
        dispatcher.request(2, 1);
        normal.drain();
        assertEquals(List.of(1, 2, 1), calls);
        assertEquals(List.of(1), errors);
    }

    @Test
    void temporaryExecutorRejectionRetainsPendingNotificationsForRetry() {
        ManualPool normal = new ManualPool(), slow = new ManualPool();
        List<Long> ticks = new ArrayList<>();
        AtomicInteger failures = new AtomicInteger();
        var dispatcher = new RoomWiredTickDispatcher(
                normal, slow, (id, tick) -> ticks.add(tick), (id, error) -> failures.incrementAndGet(), 50, () -> 0);
        normal.closed = true;
        dispatcher.request(1, 1);
        assertEquals(1, failures.get());
        assertEquals(1, dispatcher.pendingTicks(1));
        normal.closed = false;
        dispatcher.request(1, 2);
        normal.drain();
        assertEquals(List.of(1L, 2L), ticks);
    }

    private RoomWiredTickDispatcher dispatcher(
            ExecutorService normal, ExecutorService slow, java.util.function.BiConsumer<Integer, Long> callback) {
        return new RoomWiredTickDispatcher(normal, slow, callback, (id, error) -> fail(error), 50, () -> 0);
    }

    private static void await(CountDownLatch latch) {
        try {
            if (!latch.await(5, TimeUnit.SECONDS)) throw new AssertionError("latch timeout");
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
        }
    }

    private static final class ManualPool extends AbstractExecutorService {
        final ArrayDeque<Runnable> tasks = new ArrayDeque<>();
        boolean closed;

        public void execute(Runnable task) {
            if (closed) throw new java.util.concurrent.RejectedExecutionException();
            tasks.add(task);
        }

        void next() {
            tasks.remove().run();
        }

        void drain() {
            while (!tasks.isEmpty()) next();
        }

        public void shutdown() {
            closed = true;
        }

        public List<Runnable> shutdownNow() {
            closed = true;
            return List.copyOf(tasks);
        }

        public boolean isShutdown() {
            return closed;
        }

        public boolean isTerminated() {
            return closed;
        }

        public boolean awaitTermination(long timeout, TimeUnit unit) {
            return closed;
        }
    }
}
