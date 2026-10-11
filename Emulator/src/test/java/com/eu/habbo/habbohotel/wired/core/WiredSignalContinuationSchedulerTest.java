package com.eu.habbo.habbohotel.wired.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.eu.habbo.habbohotel.rooms.Room;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

class WiredSignalContinuationSchedulerTest {
    private Room room() {
        Room room = mock(Room.class);
        when(room.isLoaded()).thenReturn(true);
        when(room.getLifecycleGeneration()).thenReturn(7L);
        return room;
    }

    @Test
    void scheduledContinuationExecutesOnceAndLeavesNoPendingEntry() {
        List<Runnable> tasks = new ArrayList<>();
        var scheduler = new WiredSignalContinuationScheduler((task, delay) -> {
            assertTrue(delay >= 1);
            tasks.add(task);
            return () -> true;
        });
        AtomicInteger executed = new AtomicInteger();
        scheduler.schedule(room(), executed::incrementAndGet, () -> fail("unexpected fallback"), 0);
        assertEquals(1, scheduler.pendingCount());
        tasks.getFirst().run();
        tasks.getFirst().run();
        assertEquals(1, executed.get());
        assertEquals(0, scheduler.pendingCount());
    }

    @Test
    void overloadAndNullSchedulerResultPreserveAcceptedWorkThroughInlineFallback() {
        for (boolean exception : List.of(false, true)) {
            var scheduler = new WiredSignalContinuationScheduler((task, delay) -> {
                if (exception) throw new java.util.concurrent.RejectedExecutionException();
                return null;
            });
            AtomicInteger finished = new AtomicInteger();
            scheduler.schedule(room(), () -> fail("must use inline drain"), finished::incrementAndGet, 1);
            assertEquals(1, finished.get());
            assertEquals(0, scheduler.pendingCount());
        }
    }

    @Test
    void roomReloadAndShutdownCancelOldWorkAndReleaseOwnership() {
        List<Runnable> tasks = new ArrayList<>();
        var scheduler = new WiredSignalContinuationScheduler((task, delay) -> {
            tasks.add(task);
            return () -> true;
        });
        Room room = room();
        scheduler.schedule(room, () -> fail("old lifecycle"), () -> fail("unexpected fallback"), 1);
        when(room.getLifecycleGeneration()).thenReturn(8L);
        tasks.getFirst().run();
        assertEquals(0, scheduler.pendingCount());
        scheduler.schedule(room, () -> fail("shutdown work"), () -> fail("unexpected fallback"), 1);
        assertEquals(1, scheduler.shutdown());
        tasks.getLast().run();
        scheduler.schedule(room, () -> fail("new work during shutdown"), () -> fail("fallback during shutdown"), 1);
        assertEquals(0, scheduler.pendingCount());
    }

    @Test
    void failedExecutorCancellationCannotStrandOtherPendingChainsOrReplayCancelledCallbacks() {
        List<Runnable> tasks = new ArrayList<>();
        var scheduler = new WiredSignalContinuationScheduler((task, delay) -> {
            tasks.add(task);
            return () -> {
                throw new IllegalStateException("cancel fixture");
            };
        });
        for (int i = 0; i < 3; i++) scheduler.schedule(room(), () -> fail("cancelled"), () -> fail("fallback"), 1);
        assertEquals(3, scheduler.shutdown());
        assertEquals(0, scheduler.pendingCount());
        tasks.forEach(Runnable::run);
    }
}
