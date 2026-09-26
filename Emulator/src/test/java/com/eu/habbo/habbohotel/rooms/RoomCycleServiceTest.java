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
    void aSlowRoomDoesNotBlockARoomOnAnotherWorker() throws Exception {
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
            when(fastRoom.getId()).thenReturn(1);
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
}
