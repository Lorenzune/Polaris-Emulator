package com.eu.habbo.habbohotel.rooms;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.when;

import com.eu.habbo.habbohotel.users.Habbo;
import com.eu.habbo.habbohotel.wired.WiredUserActionType;
import com.eu.habbo.habbohotel.wired.core.WiredManager;
import org.junit.jupiter.api.Test;

class WiredUserIdleActionTest {
    @Test
    void automaticSleepAndWakeEmitTheirActionExactlyOnce() {
        var room = mock(Room.class);
        var habbo = mock(Habbo.class);
        var unit = new RoomUnit();
        when(habbo.getRoomUnit()).thenReturn(unit);
        var manager = new RoomUnitManager(room);
        // Automatic idle is detected after the cycle counter crossed the threshold.
        for (int i = 0; i <= Room.IDLE_CYCLES; i++) unit.increaseIdleTimer();
        try (var wired = mockStatic(WiredManager.class)) {
            manager.idle(habbo);
            assertTrue(unit.isIdle());
            wired.verify(
                    () -> WiredManager.triggerUserPerformsAction(room, unit, WiredUserActionType.RELAX, -1), times(1));
            manager.unIdle(habbo);
            manager.unIdle(habbo);
            assertFalse(unit.isIdle());
            assertEquals(0, unit.getIdleTimer());
            wired.verify(
                    () -> WiredManager.triggerUserPerformsAction(room, unit, WiredUserActionType.AWAKE, -1), times(1));
            wired.verify(() -> WiredManager.triggerUserUnidles(room, unit), times(1));
        }
    }
}
