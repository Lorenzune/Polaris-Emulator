package com.eu.habbo.habbohotel.users.levels;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTimeoutPreemptively;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.Test;

class AvatarHostingQueueTest {
    @Test
    void coalescesCumulativeCheckpointsAndRetriesFailedWrites() {
        var committed = new ArrayList<Long>();
        var available = new AtomicBoolean(false);
        var queue = new AvatarHostingQueue(2, (owner, session, millis) -> {
            if (!available.get()) return false;
            committed.add(millis);
            return true;
        });
        assertTrue(queue.offer(7, "first", 10));
        assertTrue(queue.offer(7, "first", 30));
        assertTrue(queue.offer(7, "first", 20));
        queue.drain(200);
        assertEquals(1, queue.size());
        available.set(true);
        queue.drain(200);
        queue.drain(200);
        assertEquals(java.util.List.of(30L), committed);
        assertEquals(0, queue.size());
    }

    @Test
    void boundedQueueRejectsNewSessionsButAcceptsNewerPendingCheckpoints() {
        var queue = new AvatarHostingQueue(1, (owner, session, millis) -> true);
        assertTrue(queue.offer(7, "first", 10));
        assertFalse(queue.offer(8, "second", 20));
        assertTrue(queue.offer(7, "first", 30));
        assertThrows(IllegalArgumentException.class, () -> queue.offer(8, "first", 40));
        queue.drain(1);
        assertTrue(queue.offer(8, "second", 20));
    }

    @Test
    void stalledDatabaseDoesNotBlockRoomOffersOrDiscardConcurrentUpdates() throws Exception {
        var entered = new CountDownLatch(1);
        var release = new CountDownLatch(1);
        var queue = new AvatarHostingQueue(2, (owner, session, millis) -> {
            entered.countDown();
            try {
                return release.await(5, TimeUnit.SECONDS);
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
                return false;
            }
        });
        queue.offer(7, "first", 10);
        try (var executor = Executors.newSingleThreadExecutor()) {
            var drain = executor.submit(() -> queue.drain(1));
            try {
                assertTrue(entered.await(5, TimeUnit.SECONDS));
                assertTimeoutPreemptively(java.time.Duration.ofSeconds(1), () -> {
                    assertTrue(queue.offer(7, "first", 30));
                    assertTrue(queue.offer(8, "second", 20));
                });
            } finally {
                release.countDown();
            }
            drain.get(5, TimeUnit.SECONDS);
            assertEquals(2, queue.size());
            queue.drain(2);
            assertEquals(0, queue.size());
        }
    }
}
