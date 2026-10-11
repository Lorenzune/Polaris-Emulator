package com.eu.habbo.habbohotel.items.interactions.games;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;

import com.eu.habbo.habbohotel.items.Item;
import com.eu.habbo.habbohotel.rooms.Room;
import com.eu.habbo.habbohotel.wired.core.WiredManager;
import org.junit.jupiter.api.Test;

class InteractionGameUpCounterIsolationTest {
    @Test
    void restartingAndControllingTheCounterDoesNotRestartPauseOrStopRoomGames() {
        var room = mock(Room.class);
        var counter = spy(new InteractionGameUpCounter(1, 1, mock(Item.class), "0", 0, 0));
        doReturn(false).when(counter).tryActivateTimerThread();
        try (var wired = mockStatic(WiredManager.class)) {
            counter.startTimer(room);
            assertTrue(counter.isRunning());
            counter.advanceCounterInMs(2500);
            counter.restartFromZero(room);
            assertEquals(0, counter.getCurrentTimeInMs());
            assertTrue(counter.isRunning());
            counter.pauseCounter(room);
            assertTrue(counter.isPaused());
            counter.resumeCounter(room);
            assertFalse(counter.isPaused());
            counter.stopCounter(room);
            assertFalse(counter.isRunning());
            counter.startTimer(room);
            counter.resetCounter(room);
            assertFalse(counter.isRunning());
            assertEquals(0, counter.getCurrentTimeInMs());
            verify(room, never()).getGames();
            wired.verify(() -> WiredManager.triggerGameStarts(room), never());
            wired.verify(() -> WiredManager.triggerGameEnds(room), never());
        }
    }
}
