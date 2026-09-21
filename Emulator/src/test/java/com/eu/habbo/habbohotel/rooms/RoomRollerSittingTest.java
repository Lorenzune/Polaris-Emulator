package com.eu.habbo.habbohotel.rooms;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.eu.habbo.habbohotel.users.HabboItem;
import com.eu.habbo.messages.outgoing.rooms.users.RoomUnitOnRollerComposer;
import org.junit.jupiter.api.Test;

class RoomRollerSittingTest {

    @Test
    void roomUnitOnRollerComposerStoresRidingItem() {
        Room room = mock(Room.class);
        RoomUnit unit = mock(RoomUnit.class);
        HabboItem roller = mock(HabboItem.class);
        HabboItem pillow = mock(HabboItem.class);

        RoomTile oldTile = new RoomTile((short) 1, (short) 1, (short) 0, RoomTileState.OPEN, true);
        RoomTile newTile = new RoomTile((short) 1, (short) 2, (short) 0, RoomTileState.OPEN, true);

        RoomUnitOnRollerComposer composer = new RoomUnitOnRollerComposer(
                unit, roller, oldTile, 1.0, newTile, 1.0, room, pillow);

        assertSame(pillow, composer.getRidingItem());
    }

    @Test
    void defaultComposerHasNullRidingItem() {
        Room room = mock(Room.class);
        RoomUnit unit = mock(RoomUnit.class);
        HabboItem roller = mock(HabboItem.class);

        RoomTile oldTile = new RoomTile((short) 1, (short) 1, (short) 0, RoomTileState.OPEN, true);
        RoomTile newTile = new RoomTile((short) 1, (short) 2, (short) 0, RoomTileState.OPEN, true);

        RoomUnitOnRollerComposer composer = new RoomUnitOnRollerComposer(
                unit, roller, oldTile, 0.5, newTile, 0.5, room);

        assertNull(composer.getRidingItem());
    }

    @Test
    void ridingItemMovementPreservesSitUpdateWithoutCallingWalkOffOn() throws Exception {
        Room room = mock(Room.class);
        when(room.isLoaded()).thenReturn(true);
        RoomLayout layout = mock(RoomLayout.class);
        when(room.getLayout()).thenReturn(layout);

        RoomUnit unit = new RoomUnit();
        unit.setId(1);
        RoomTile oldTile = new RoomTile((short) 2, (short) 2, (short) 0, RoomTileState.SIT, true);
        RoomTile newTile = new RoomTile((short) 2, (short) 3, (short) 0, RoomTileState.SIT, true);
        unit.setLocation(oldTile);
        unit.setStatus(RoomUnitStatus.SIT, "0.5");

        HabboItem roller = mock(HabboItem.class);
        when(roller.getId()).thenReturn(10);

        HabboItem pillow = mock(HabboItem.class);
        when(pillow.getId()).thenReturn(20);

        // Even if old tile and new tile have items, when ridingItem != null they must NOT be called
        HabboItem oldTileItem = mock(HabboItem.class);
        HabboItem newTileItem = mock(HabboItem.class);
        when(room.getTopItemAt(2, 2)).thenReturn(oldTileItem);
        when(room.getTopItemAt(2, 3)).thenReturn(newTileItem);

        RoomUnitOnRollerComposer composer = new RoomUnitOnRollerComposer(
                unit, roller, oldTile, 1.0, newTile, 1.0, room, pillow);

        composer.compose();

        // Unit should have moved to newTile and kept SIT with sitUpdate = true
        assertEquals(newTile, unit.getCurrentLocation());
        assertTrue(unit.hasStatus(RoomUnitStatus.SIT));
        assertTrue(unit.sitUpdate);

        // oldTileItem and newTileItem must NOT have onWalkOff or onWalkOn invoked
        verify(oldTileItem, never()).onWalkOff(any(), any(), any());
        verify(newTileItem, never()).onWalkOn(any(), any(), any());
    }
}
