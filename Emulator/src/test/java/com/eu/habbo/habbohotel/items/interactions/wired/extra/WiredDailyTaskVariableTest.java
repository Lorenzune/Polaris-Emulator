package com.eu.habbo.habbohotel.items.interactions.wired.extra;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.RETURNS_DEEP_STUBS;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.eu.habbo.Emulator;
import com.eu.habbo.habbohotel.GameEnvironment;
import com.eu.habbo.habbohotel.dailytasks.DailyTaskManager;
import com.eu.habbo.habbohotel.items.Item;
import com.eu.habbo.habbohotel.items.interactions.wired.WiredSettings;
import com.eu.habbo.habbohotel.rooms.Room;
import com.eu.habbo.habbohotel.rooms.RoomUserVariableManager;
import com.eu.habbo.messages.incoming.wired.WiredSaveException;
import org.junit.jupiter.api.Test;

class WiredDailyTaskVariableTest {
    @Test
    void nativeFieldsExposeCachedDailyProgressWithoutCreatingAnIndependentCounter() throws Exception {
        var environment = mock(GameEnvironment.class, RETURNS_DEEP_STUBS);
        var room = mock(Room.class, RETURNS_DEEP_STUBS);
        when(environment.getRoomManager().getRoom(0)).thenReturn(room);
        var daily = new WiredExtraDailyTaskVariable(33, 2, mock(Item.class), "", 0, 0);
        when(room.getRoomSpecialTypes().getExtra(33)).thenReturn(daily);
        var today = new DailyTaskManager.WiredProgress(true, 4, 100, 110);
        var absent = new DailyTaskManager.WiredProgress(false, 0, 0, 0);
        when(environment.getDailyTaskManager().getWiredProgress(9, 12)).thenReturn(today, absent);
        try (var emulator = mockStatic(Emulator.class)) {
            emulator.when(Emulator::getGameEnvironment).thenReturn(environment);
            daily.saveData(new WiredSettings(new int[0], "DAILY_SCORE\t12", new int[0], 0), null);
            assertEquals("daily_score", daily.getVariableName());
            assertEquals("12", daily.getTaskCode());
            assertEquals(today, daily.readProgress(9));
            assertEquals(today, daily.readProgress(9));
            verify(environment.getDailyTaskManager(), times(1)).getWiredProgress(9, 12);
            daily.invalidateProgress(9);
            assertEquals(absent, daily.readProgress(9));
            var info = new RoomUserVariableManager(room).getDefinitionInfo(33);
            assertEquals(true, info.isReadOnly());
            assertFalse(info.canCreateAndDelete());
            assertThrows(
                    WiredSaveException.class,
                    () -> daily.saveData(new WiredSettings(new int[0], "score\t2147483648", new int[0], 0), null));
            assertEquals("12", daily.getTaskCode());
        }
    }

    @Test
    void sharedReferenceOriginsPreserveInspectionAndExternalSources() {
        var reference = mock(WiredExtraVariableReference.class);
        when(reference.getRoomId()).thenReturn(10);
        when(reference.getSourceRoomId()).thenReturn(20);
        assertEquals(2, WiredVariableReferenceSupport.sharedOrigin(reference));
        try (var inspection = com.eu.habbo.habbohotel.wired.core.WiredVariableChangeOrigin.begin(4)) {
            assertEquals(4, WiredVariableReferenceSupport.sharedOrigin(reference));
        }
        try (var external = com.eu.habbo.habbohotel.wired.core.WiredVariableChangeOrigin.begin(8)) {
            assertEquals(8, WiredVariableReferenceSupport.sharedOrigin(reference));
        }
        when(reference.getSourceRoomId()).thenReturn(10);
        assertEquals(1, WiredVariableReferenceSupport.sharedOrigin(reference));
    }
}
