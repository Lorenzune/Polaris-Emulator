package com.eu.habbo.habbohotel.rooms;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

class RoomCycleServiceTest {

    @Test
    void roomsAreAssignedToStableWorkersAndWorkerCountIsBounded() {
        RoomCycleService service = new RoomCycleService(1_000, 250);
        try {
            assertEquals(RoomCycleService.MAX_WORKERS, service.workerCount());
            assertEquals(service.workerOf(8735), service.workerOf(8735));
            assertEquals(31, service.workerOf(8735));
            assertTrue(RoomCycleService.defaultWorkers() >= 2);
            assertTrue(RoomCycleService.defaultWorkers() <= 16);
        } finally {
            service.dispose();
        }
    }

    @Test
    void aSlowRoomDoesNotBlockARoomFromTheSameLegacyWorker() throws Exception {
        RoomCycleService service = new RoomCycleService(2, 250);
        CountDownLatch slowRoomStarted = new CountDownLatch(1);
        CountDownLatch releaseSlowRoom = new CountDownLatch(1);
        CountDownLatch fastRoomRan = new CountDownLatch(1);
        ScheduledFuture<?> slowFuture = null;
        ScheduledFuture<?> fastFuture = null;

        try {
            Room slowRoom = mock(Room.class);
            when(slowRoom.getId()).thenReturn(0);
            doAnswer(invocation -> {
                        slowRoomStarted.countDown();
                        releaseSlowRoom.await();
                        return null;
                    })
                    .when(slowRoom)
                    .run();

            Room fastRoom = mock(Room.class);
            when(fastRoom.getId()).thenReturn(2);
            doAnswer(invocation -> {
                        fastRoomRan.countDown();
                        return null;
                    })
                    .when(fastRoom)
                    .run();

            slowFuture = service.schedule(slowRoom);
            assertTrue(slowRoomStarted.await(2, TimeUnit.SECONDS));

            fastFuture = service.schedule(fastRoom);
            assertTrue(fastRoomRan.await(2, TimeUnit.SECONDS));
        } finally {
            releaseSlowRoom.countDown();
            if (slowFuture != null) slowFuture.cancel(false);
            if (fastFuture != null) fastFuture.cancel(false);
            service.dispose();
        }
    }

    @Test
    void aCycleFailureDoesNotCancelTheRoomSchedule() throws Exception {
        RoomCycleService service = new RoomCycleService(1, 250);
        CountDownLatch secondRun = new CountDownLatch(1);
        AtomicInteger runs = new AtomicInteger();
        ScheduledFuture<?> future = null;

        try {
            Room room = mock(Room.class);
            when(room.getId()).thenReturn(5);
            doAnswer(invocation -> {
                        if (runs.getAndIncrement() == 0) {
                            throw new IllegalStateException("test failure");
                        }
                        secondRun.countDown();
                        return null;
                    })
                    .when(room)
                    .run();

            future = service.schedule(room);
            assertTrue(secondRun.await(3, TimeUnit.SECONDS));
        } finally {
            if (future != null) future.cancel(false);
            service.dispose();
        }
    }

    @Test
    void aStackOverflowIsLoggedWithoutBreakingTheCycleWorker() {
        RoomCycleService service = new RoomCycleService(1, 250);
        try {
            Room room = mock(Room.class);
            when(room.getId()).thenReturn(5);
            doThrow(new StackOverflowError()).when(room).run();

            service.runCycle(room);
        } finally {
            service.dispose();
        }
    }

    @Test
    void slowRoomMovesToDedicatedPoolWithoutOverlappingOrChangingCycleCount() throws Exception {
        java.util.concurrent.atomic.AtomicLong clock = new java.util.concurrent.atomic.AtomicLong();
        RoomCycleService service = new RoomCycleService(2, 1, clock::get);
        java.util.List<String> threads = new java.util.concurrent.CopyOnWriteArrayList<>();
        CountDownLatch second = new CountDownLatch(1), release = new CountDownLatch(1);
        AtomicInteger active = new AtomicInteger(), maximum = new AtomicInteger();
        ScheduledFuture<?> future = null;
        try {
            Room room = mock(Room.class);
            when(room.getId()).thenReturn(8);
            doAnswer(invocation -> {
                        maximum.accumulateAndGet(active.incrementAndGet(), Math::max);
                        try {
                            threads.add(Thread.currentThread().getName());
                            if (threads.size() == 1) clock.addAndGet(2_000_000);
                            else {
                                second.countDown();
                                release.await(5, TimeUnit.SECONDS);
                            }
                        } finally {
                            active.decrementAndGet();
                        }
                        return null;
                    })
                    .when(room)
                    .run();
            future = service.schedule(room);
            assertTrue(second.await(3, TimeUnit.SECONDS));
            assertEquals(2, threads.size());
            assertTrue(threads.get(0).startsWith("RoomCycleIndependent-"));
            assertTrue(threads.get(1).startsWith("RoomCycleSlow-"));
            assertEquals(1, maximum.get());
            assertTrue(future.cancel(false));
            org.junit.jupiter.api.Assertions.assertFalse(future.cancel(false));
            org.junit.jupiter.api.Assertions.assertThrows(
                    java.util.concurrent.CancellationException.class, future::get);
        } finally {
            release.countDown();
            if (future != null) future.cancel(false);
            service.dispose();
        }
        assertEquals(2, threads.size());
    }

    @Test
    void cancellingBeforeFirstCyclePreventsExecutionAndDisposalCancelsOutstandingFutures() {
        RoomCycleService service = new RoomCycleService(2, 250);
        Room room = mock(Room.class);
        when(room.getId()).thenReturn(10);
        ScheduledFuture<?> cancelled = service.schedule(room);
        assertTrue(cancelled.cancel(false));
        ScheduledFuture<?> pending = service.schedule(room);
        service.dispose();
        assertTrue(pending.isCancelled());
        org.mockito.Mockito.verify(room, org.mockito.Mockito.never()).run();
    }

    @Test
    void legacySchedulerRemainsAvailableFromConfiguration() {
        com.eu.habbo.core.ConfigurationManager config = mock(com.eu.habbo.core.ConfigurationManager.class);
        when(config.getInt(RoomCycleService.WORKERS_SETTING, 0)).thenReturn(1);
        when(config.getBoolean("room.cycle.room.isolation", true)).thenReturn(false);
        RoomCycleService service = RoomCycleService.fromConfig(config);
        try {
            assertEquals(1, service.workerCount());
            Room room = mock(Room.class);
            when(room.getId()).thenReturn(10);
            ScheduledFuture<?> future = service.schedule(room);
            assertTrue(future.cancel(false));
            org.junit.jupiter.api.Assertions.assertFalse(future.cancel(false));
        } finally {
            service.dispose();
        }
    }
}
