package com.eu.habbo.habbohotel.wired.core;

import static org.junit.jupiter.api.Assertions.*;

import com.eu.habbo.messages.outgoing.rooms.WiredMovementsComposer;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;

class WiredMovementCollectionTest {
    @Test
    void foreignResetRevokesBothBatchesButKeepsNewMovesAndUnrelatedFurnitureAndUsers() throws Exception {
        var first = new WiredMovementCollection();
        var second = new WiredMovementCollection();
        var old = WiredMovementsComposer.furniMovement(141, 18, 19, 20, 21, 0.02, 0.02);
        var independent = WiredMovementsComposer.furniMovement(810, 19, 18, 20, 19, 2.04, 2.04);
        var otherRoom = WiredMovementsComposer.furniMovement(900, 3, 4, 4, 4, 0, 0);
        var direction = WiredMovementsComposer.userDirectionUpdate(7, 2, 2);
        try {
            WiredMovementCollection.addFurniture(first, 141, old);
            WiredMovementCollection.addFurniture(first, 810, independent);
            first.add(direction);
            WiredMovementCollection.addFurniture(second, 141, old);
            WiredMovementCollection.addFurniture(second, 900, otherRoom);
            try (var executor = Executors.newSingleThreadExecutor()) {
                executor.submit(() -> WiredMoveCarryHelper.discardCollectedFurnitureMovements(141))
                        .get(5, TimeUnit.SECONDS);
            }
            assertEquals(List.of(old, independent, direction), first,
                    "Another thread must not mutate the owner's list while effects append to it");
            var subsequent = WiredMovementsComposer.furniMovement(141, 1, 3, 7, 14, 0.02, 0.02);
            WiredMovementCollection.addFurniture(first, 141, subsequent);
            first.discardInvalidated();
            second.discardInvalidated();
            assertEquals(List.of(independent, direction, subsequent), first);
            assertEquals(List.of(otherRoom), second);
        } finally {
            first.release();
            second.release();
        }
        assertNoPending(141, 810, 900);
    }

    @Test
    void finishingOrAbandoningABatchReleasesAllItsRegistrations() throws Exception {
        var batch = new WiredMovementCollection();
        try {
            for (int index = 0; index < 64; index++) {
                WiredMovementCollection.addFurniture(batch, 811,
                        WiredMovementsComposer.furniMovement(811, index, 1, index + 1, 1, 0, 0));
            }
        } finally { batch.release(); }
        // Teardown can run after finish has already released the same collection.
        batch.release();
        assertNoPending(811);
    }

    private static void assertNoPending(int... itemIds) throws Exception {
        var field = WiredMovementCollection.class.getDeclaredField("PENDING");
        field.setAccessible(true);
        Map<?, ?> pending = (Map<?, ?>) field.get(null);
        for (int itemId : itemIds) assertFalse(pending.containsKey(itemId), "Completed batch retained item " + itemId);
    }
}
