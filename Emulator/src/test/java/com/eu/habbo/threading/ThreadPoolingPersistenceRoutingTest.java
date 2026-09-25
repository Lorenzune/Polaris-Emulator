package com.eu.habbo.threading;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.same;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.eu.habbo.database.PersistenceExecutor;
import com.eu.habbo.habbohotel.users.HabboItem;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;

class ThreadPoolingPersistenceRoutingTest {

    @Test
    void persistenceTasksUseTheInjectedDatabaseExecutor() {
        PersistenceExecutor persistence = mock(PersistenceExecutor.class);
        ThreadPooling threading = new ThreadPooling(1, persistence);
        Runnable task = () -> {};
        try {
            threading.runPersistence(task);

            verify(persistence).execute(same(task));
        } finally {
            threading.shutDown();
        }
    }

    @Test
    void tryRunPersistenceDoesNotRunRoomWorkOnTheCallerWhenTheQueueIsFull() throws Exception {
        PersistenceExecutor persistence = new PersistenceExecutor(1, 1);
        ThreadPooling threading = new ThreadPooling(1, persistence);
        CountDownLatch workerStarted = new CountDownLatch(1);
        CountDownLatch releaseWorker = new CountDownLatch(1);
        try {
            persistence.execute(() -> {
                workerStarted.countDown();
                try {
                    releaseWorker.await();
                } catch (InterruptedException interrupted) {
                    Thread.currentThread().interrupt();
                }
            });
            assertTrue(workerStarted.await(2, TimeUnit.SECONDS));
            assertTrue(persistence.tryExecute(() -> {}));

            assertFalse(threading.tryRunPersistence(() -> {
                throw new AssertionError("Saturated work must not run on this thread");
            }));
        } finally {
            releaseWorker.countDown();
            threading.shutDown();
            persistence.shutDown();
        }
    }

    @Test
    void repeatedFurniSavesWaitInOneDeferredSlotAndDrainAfterSaturation() throws Exception {
        PersistenceExecutor persistence = new PersistenceExecutor(1, 1);
        ThreadPooling threading = new ThreadPooling(1, persistence);
        CountDownLatch workerStarted = new CountDownLatch(1);
        CountDownLatch releaseWorker = new CountDownLatch(1);
        CountDownLatch itemSaved = new CountDownLatch(1);
        HabboItem item = mock(HabboItem.class);
        org.mockito.Mockito.doAnswer(invocation -> {
                    itemSaved.countDown();
                    return null;
                })
                .when(item)
                .run();

        try {
            persistence.execute(() -> {
                workerStarted.countDown();
                try {
                    releaseWorker.await();
                } catch (InterruptedException interrupted) {
                    Thread.currentThread().interrupt();
                }
            });
            assertTrue(workerStarted.await(2, TimeUnit.SECONDS));
            assertTrue(persistence.tryExecute(() -> {}));

            threading.runHabboItemPersistence(item);
            threading.runHabboItemPersistence(item);
            assertEquals(1, threading.getDeferredHabboItemPersistenceCount());

            releaseWorker.countDown();
            assertTrue(itemSaved.await(2, TimeUnit.SECONDS));
            assertEquals(0, threading.getDeferredHabboItemPersistenceCount());
            verify(item, times(1)).run();
        } finally {
            releaseWorker.countDown();
            threading.shutDown();
            persistence.shutDown();
        }
    }
}
