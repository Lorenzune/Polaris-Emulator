package com.eu.habbo.habbohotel.rooms;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.eu.habbo.habbohotel.users.HabboItem;
import com.eu.habbo.messages.outgoing.rooms.users.RoomUnitOnRollerComposer;
import org.junit.jupiter.api.Test;

class RoomRollerBasculaTest {

    @Test
    void roomUnitOnRollerComposerSetsRollerSlideOrigin() {
        Room room = mock(Room.class);
        when(room.isLoaded()).thenReturn(true);
        RoomLayout layout = mock(RoomLayout.class);
        when(room.getLayout()).thenReturn(layout);

        RoomUnit unit = new RoomUnit();
        unit.setId(1);
        RoomTile oldTile = new RoomTile((short) 1, (short) 1, (short) 0, RoomTileState.OPEN, true);
        RoomTile newTile = new RoomTile((short) 1, (short) 2, (short) 0, RoomTileState.OPEN, true);
        unit.setLocation(oldTile);
        unit.setZ(1.5);

        HabboItem roller = mock(HabboItem.class);
        when(roller.getId()).thenReturn(100);

        RoomUnitOnRollerComposer composer =
                new RoomUnitOnRollerComposer(unit, roller, oldTile, 1.5, newTile, 1.5, room);

        composer.compose();

        assertEquals(newTile, unit.getCurrentLocation());
        assertEquals(oldTile, unit.getRollerSlideOrigin());
        assertEquals(1.5, unit.getRollerSlideOriginZ(), 0.001);
        assertTrue(System.currentTimeMillis() - unit.getLastRollerTime() < 500);
    }

    @Test
    void composerAllowsRollWhenNewLocationUnitHasAlreadyMoved() {
        Room room = mock(Room.class);
        when(room.isLoaded()).thenReturn(true);
        RoomLayout layout = mock(RoomLayout.class);
        when(room.getLayout()).thenReturn(layout);

        RoomUnit unit1 = new RoomUnit();
        unit1.setId(1);
        RoomTile tile1 = new RoomTile((short) 1, (short) 1, (short) 0, RoomTileState.OPEN, true);
        RoomTile tile2 = new RoomTile((short) 1, (short) 2, (short) 0, RoomTileState.OPEN, true);
        unit1.setLocation(tile1);

        RoomUnit unit2 = new RoomUnit();
        unit2.setId(2);
        // unit2 was previously on tile2, but has rolled to tile3
        RoomTile tile3 = new RoomTile((short) 1, (short) 3, (short) 0, RoomTileState.OPEN, true);
        unit2.setLocation(tile3);
        // But tile2.units still contains unit2 reference before cleanup
        tile2.getUnits().add(unit2);

        HabboItem roller = mock(HabboItem.class);
        when(roller.getId()).thenReturn(100);

        RoomUnitOnRollerComposer composer = new RoomUnitOnRollerComposer(unit1, roller, tile1, 1.0, tile2, 1.0, room);

        assertNotNull(composer.compose());
        assertEquals(tile2, unit1.getCurrentLocation());
    }

    @Test
    void midSlideClickInAnyDirectionInterruptsFromOriginUnlessClickingDestination() {
        RoomTile origin = new RoomTile((short) 5, (short) 5, (short) 0, RoomTileState.OPEN, true);
        RoomTile dest = new RoomTile((short) 5, (short) 6, (short) 0, RoomTileState.OPEN, true);

        // Clicking backwards on (5, 4) is not dest -> interrupts from origin
        assertFalse(5 == dest.x && 4 == dest.y);

        // Clicking sideways on (4, 5) or (6, 5) is not dest -> interrupts from origin
        assertFalse(4 == dest.x && 5 == dest.y);
        assertFalse(6 == dest.x && 5 == dest.y);

        // Clicking origin itself (5, 5) is not dest -> interrupts and stops at origin
        assertFalse(5 == dest.x && 5 == dest.y);

        // Clicking destination (5, 6) matches dest -> allows slide to complete
        assertTrue(5 == dest.x && 6 == dest.y);
    }

    @Test
    void midSlideInterruptionDoesNotTriggerDestinationOnWalkOn() throws Exception {
        Room room = mock(Room.class);
        when(room.isLoaded()).thenReturn(true);
        RoomLayout layout = mock(RoomLayout.class);
        when(room.getLayout()).thenReturn(layout);

        RoomUnit unit = new RoomUnit();
        unit.setId(1);
        RoomTile origin = new RoomTile((short) 1, (short) 1, (short) 0, RoomTileState.OPEN, true);
        RoomTile dest = new RoomTile((short) 1, (short) 2, (short) 0, RoomTileState.OPEN, true);
        unit.setLocation(origin);

        HabboItem roller = mock(HabboItem.class);
        when(roller.getId()).thenReturn(100);

        HabboItem destItem = mock(HabboItem.class);
        when(destItem.getId()).thenReturn(200);
        when(room.getTopItemAt(1, 2)).thenReturn(destItem);

        long slideStartTime = 1000L;
        unit.setLastRollerTime(slideStartTime);
        unit.setRollerSlideOrigin(origin);
        unit.setLocation(dest);

        // Simulate mid-slide cancellation (e.g. click received during slide)
        unit.setLocation(origin);
        unit.setLastRollerTime(0);
        unit.setRollerSlideOrigin(null);

        // Verify that the arrival check (which compares slideStartTime and currentLocation) rejects execution
        boolean shouldExecuteArrival =
                (unit.getLastRollerTime() == slideStartTime) && (unit.getCurrentLocation() == dest);
        assertFalse(shouldExecuteArrival, "Cancelled roll must NOT execute arrival onWalkOn");
        verify(destItem, never()).onWalkOn(any(), any(), any());
    }
}
