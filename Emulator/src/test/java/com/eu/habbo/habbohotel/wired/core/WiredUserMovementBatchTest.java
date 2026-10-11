package com.eu.habbo.habbohotel.wired.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import com.eu.habbo.habbohotel.rooms.Room;
import com.eu.habbo.messages.ServerMessage;
import com.eu.habbo.messages.outgoing.rooms.WiredMovementsComposer;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class WiredUserMovementBatchTest {
    @Test
    void chairServiceTileRoundTripPublishesNeitherMovementNorTemporaryDirection() {
        var room = mock(Room.class);
        try (var outer = WiredUserMovementBatch.begin(room)) {
            WiredUserMovementBatch.send(room, List.of(slide(25, 25, 1, 8)));
            try (var nested = WiredUserMovementBatch.begin(room)) {
                WiredUserMovementBatch.send(room, List.of(slide(1, 8, 25, 25)));
            }
            verifyNoInteractions(room);
        }
        var message = ArgumentCaptor.forClass(ServerMessage.class);
        verify(room).sendComposer(message.capture());
        var packet = message.getValue().get();
        try {
            assertEquals(0, packet.getInt(6));
        } finally {
            packet.release();
        }
        // Closing the batch must restore immediate delivery.
        WiredUserMovementBatch.send(room, List.of(slide(25, 25, 26, 25)));
        verify(room, times(2)).sendComposer(any(ServerMessage.class));
    }

    @Test
    void explicitStationaryDirectionChangeStillPublishesDirection() {
        var packet = new WiredMovementsComposer(List.of(slide(25, 25, 25, 25)))
                .compose()
                .get();
        try {
            assertEquals(1, packet.getInt(6));
            assertEquals(WiredMovementsComposer.TYPE_USER_DIRECTION, packet.getInt(10));
            assertEquals(4, packet.getInt(18));
            assertEquals(4, packet.getInt(22));
        } finally {
            packet.release();
        }
    }

    @Test
    void genuineMultiStepMoveKeepsInitialOriginAndFinalDestination() {
        var packet = new WiredMovementsComposer(List.of(slide(25, 25, 1, 8), slide(1, 8, 26, 25)))
                .compose()
                .get();
        try {
            assertEquals(1, packet.getInt(6));
            assertEquals(WiredMovementsComposer.TYPE_USER_MOVE, packet.getInt(10));
            assertEquals(25, packet.getInt(14));
            assertEquals(25, packet.getInt(18));
            assertEquals(26, packet.getInt(22));
            assertEquals(25, packet.getInt(26));
        } finally {
            packet.release();
        }
    }

    @Test
    void separatelyTimedBatchesRemainSeparateAnimations() {
        var room = mock(Room.class);
        try (var batch = WiredUserMovementBatch.begin(room)) {
            WiredUserMovementBatch.send(room, List.of(slide(25, 25, 1, 8)));
        }
        try (var batch = WiredUserMovementBatch.begin(room)) {
            WiredUserMovementBatch.send(room, List.of(slide(1, 8, 25, 25)));
        }
        var messages = ArgumentCaptor.forClass(ServerMessage.class);
        verify(room, times(2)).sendComposer(messages.capture());
        for (var message : messages.getAllValues()) {
            var packet = message.get();
            try {
                assertEquals(WiredMovementsComposer.TYPE_USER_MOVE, packet.getInt(10));
            } finally {
                packet.release();
            }
        }
    }

    private static WiredMovementsComposer.MovementData slide(int fromX, int fromY, int toX, int toY) {
        return WiredMovementsComposer.userSlideMovement(1, fromX, fromY, toX, toY, 0.1, 0.1, 4, 4, 200);
    }
}
