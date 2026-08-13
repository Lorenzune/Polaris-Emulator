package com.eu.habbo.habbohotel.wired.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.eu.habbo.habbohotel.rooms.Room;
import com.eu.habbo.habbohotel.rooms.RoomUnit;
import com.eu.habbo.habbohotel.rooms.RoomUnitManager;
import org.junit.jupiter.api.Test;

class WiredHandItemInternalVariableTest {

    @Test
    void handItemAliasIsReadableAndWritable() {
        assertEquals("@handitem_id", WiredInternalVariableSupport.normalizeKey("@handitem"));
        assertTrue(WiredInternalVariableSupport.canUseUserReference("@handitem"));
        assertTrue(WiredInternalVariableSupport.canUseUserDestination("@handitem"));
    }

    @Test
    void writesThroughRoomUnitManagerSoVisualUpdateAndTriggerArePreserved() {
        Room room = mock(Room.class);
        RoomUnitManager unitManager = mock(RoomUnitManager.class);
        RoomUnit roomUnit = mock(RoomUnit.class);
        when(room.getUnitManager()).thenReturn(unitManager);

        assertTrue(WiredInternalVariableSupport.writeUserValue(room, roomUnit, "@handitem", 42));

        verify(unitManager).giveHandItem(roomUnit, 42);
    }

    @Test
    void handItemValuesAreClampedToTheSupportedRange() {
        Room room = mock(Room.class);
        RoomUnitManager unitManager = mock(RoomUnitManager.class);
        RoomUnit roomUnit = mock(RoomUnit.class);
        when(room.getUnitManager()).thenReturn(unitManager);

        assertTrue(WiredInternalVariableSupport.writeUserValue(room, roomUnit, "@handitem", -5));
        assertTrue(WiredInternalVariableSupport.writeUserValue(room, roomUnit, "@handitem", 50_000));

        verify(unitManager).giveHandItem(roomUnit, 0);
        verify(unitManager).giveHandItem(roomUnit, 10_000);
    }
}
