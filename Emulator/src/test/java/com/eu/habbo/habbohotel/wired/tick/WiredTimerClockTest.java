package com.eu.habbo.habbohotel.wired.tick;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class WiredTimerClockTest {
    @Test
    void waitsForFullIntervalAfterRegistrationAndReset() {
        var clock = new WiredTimerClock();
        clock.reset(103);
        assertFalse(clock.poll(110, 50, 500, true));
        assertTrue(clock.poll(113, 50, 500, true));
        clock.reset(115);
        assertFalse(clock.poll(123, 50, 500, true));
        assertTrue(clock.poll(125, 50, 500, true));
    }

    @Test
    void intervalsNotDivisibleByTickStillFireAndKeepTheRemainder() {
        var clock = new WiredTimerClock();
        clock.reset(0);
        assertFalse(clock.poll(5, 90, 500, true));
        assertTrue(clock.poll(6, 90, 500, true));
        assertFalse(clock.poll(11, 90, 500, true));
        assertTrue(clock.poll(12, 90, 500, true));
    }

    @Test
    void accountsForSkippedTicksWithoutBurstsOrDuplicateTicks() {
        var clock = new WiredTimerClock();
        clock.reset(0);
        assertTrue(clock.poll(101, 50, 500, true));
        assertFalse(clock.poll(101, 50, 500, true));
        assertFalse(clock.poll(109, 50, 500, true));
        assertTrue(clock.poll(110, 50, 500, true));
    }

    @Test
    void oneShotWaitsForResetEvenAfterManyIntervals() {
        var clock = new WiredTimerClock();
        clock.reset(100);
        assertTrue(clock.poll(110, 50, 500, false));
        assertFalse(clock.poll(120, 50, 500, false));
        clock.reset(121);
        assertFalse(clock.poll(130, 50, 500, false));
        assertTrue(clock.poll(131, 50, 500, false));
    }

    @Test
    void deniedOneShotCanRunOnceWhenTheExecutionLimitAllowsIt() {
        var clock = new WiredTimerClock();
        clock.reset(0);
        assertFalse(clock.poll(10, 50, 500, false, () -> false));
        assertTrue(clock.poll(11, 50, 500, false, () -> true));
        assertFalse(clock.poll(20, 50, 500, false, () -> true));
    }
}
