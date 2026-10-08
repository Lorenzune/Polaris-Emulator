package com.eu.habbo.habbohotel.wired.core;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import com.eu.habbo.habbohotel.items.interactions.games.WiredGameTimerAdjustment;
import com.eu.habbo.habbohotel.rooms.Room;
import org.junit.jupiter.api.Test;

class WiredCountdownAdjustmentTest {
    @Test
    void setsTenMinutesWithoutStartingGameAndPreservesBaseTime() {
        var timer = new WiredCountdownClockTest.Countdown();
        var room = mock(Room.class);
        WiredGameTimerAdjustment.adjust(timer, room, 2, 10, 0);
        assertEquals(600000, timer.getClockTimeInMs());
        assertEquals(600, timer.getTimeNow());
        assertEquals(5, timer.getBaseTime());
        assertFalse(timer.isRunning());
        assertEquals("600\t5", timer.getExtradata());
        verify(room).updateItem(timer);
    }

    @Test
    void preservesHalfSecondPrecisionAndClearsPhaseAtZero() {
        var timer = new WiredCountdownClockTest.Countdown();
        var room = mock(Room.class);
        timer.setRunning(true);
        WiredGameTimerAdjustment.adjust(timer, room, 1, 0, 1);
        assertEquals(4500, timer.getClockTimeInMs());
        WiredGameTimerAdjustment.adjust(timer, room, 0, 0, 1);
        assertEquals(5000, timer.getClockTimeInMs());
        WiredGameTimerAdjustment.adjust(timer, room, 2, 0, 1);
        assertEquals(500, timer.getClockTimeInMs());
        WiredGameTimerAdjustment.adjust(timer, room, 1, 1, 0);
        assertEquals(0, timer.getClockTimeInMs());
        WiredGameTimerAdjustment.adjust(timer, room, 0, 0, 1);
        assertEquals(500, timer.getClockTimeInMs());
        assertTrue(timer.isRunning());
    }
}
