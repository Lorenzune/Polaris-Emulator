package com.eu.habbo.messages.incoming.rooms;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class RoomLoadTimingTest {
    @Test
    void secondsAndMillisecondsBothPreventImmediateDuplicateEntries() {
        long seconds = 1_790_000_000L;
        assertFalse(RoomLoadTiming.canStart(0, seconds, seconds * 1000 + 500));
        assertFalse(RoomLoadTiming.canStart(0, seconds * 1000, seconds * 1000 + 500));
        assertTrue(RoomLoadTiming.canStart(0, seconds, seconds * 1000 + 1500));
    }

    @Test
    void freshLoadingDeadlineIsNotBasedOnTheOldSourceRoomVisit() {
        long now = 1_790_000_000_000L;
        assertFalse(RoomLoadTiming.expired(now - 100, now));
        assertFalse(RoomLoadTiming.expired(now - 5000, now));
        assertTrue(RoomLoadTiming.expired(now - 5001, now));
        assertFalse(RoomLoadTiming.expired(0, now));
    }

    @Test
    void anActiveEntryAlwaysBlocksAnotherRequest() {
        assertFalse(RoomLoadTiming.canStart(42, 0, 1_790_000_000_000L));
        assertTrue(RoomLoadTiming.canStart(0, 0, 1_790_000_000_000L));
    }
}
