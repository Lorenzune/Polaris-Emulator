package com.eu.habbo.threading.runnables;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.RETURNS_DEEP_STUBS;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.anyBoolean;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.eu.habbo.habbohotel.rooms.Room;
import com.eu.habbo.habbohotel.rooms.RoomTile;
import com.eu.habbo.habbohotel.rooms.RoomTileState;
import com.eu.habbo.habbohotel.rooms.RoomUnit;
import com.eu.habbo.habbohotel.rooms.RoomUnitStatus;
import java.util.HashMap;
import org.junit.jupiter.api.Test;

class RoomUnitTeleportTest {
    @Test
    void delayedTeleportCannotMoveAnAvatarInAnotherRoom() {
        var source = mock(Room.class);
        var target = mock(Room.class);
        var unit = mock(RoomUnit.class);
        when(source.getLifecycleGeneration()).thenReturn(1L);
        when(unit.getRoom()).thenReturn(source);
        var task = new RoomUnitTeleport(unit, source, 2, 3, 0, 0);
        when(unit.getRoom()).thenReturn(target);
        unit.isWiredTeleporting = true;
        task.run();
        verify(unit, never()).setLocation(any());
        verify(unit, never()).setCanWalk(anyBoolean());
        assertTrue(unit.isWiredTeleporting);
        verifyNoInteractions(target);
    }

    @Test
    void staleRoomGenerationCannotOverrideANewerTeleport() {
        var room = mock(Room.class);
        var unit = mock(RoomUnit.class);
        when(unit.getRoom()).thenReturn(room);
        when(room.getLifecycleGeneration()).thenReturn(1L);
        var task = new RoomUnitTeleport(unit, room, 2, 3, 0, 0);
        when(room.getLifecycleGeneration()).thenReturn(2L);
        unit.isWiredTeleporting = true;
        task.run();
        verify(unit, never()).setLocation(any());
        assertTrue(unit.isWiredTeleporting);
    }

    @Test
    void missingDestinationReleasesThePendingTeleportAndWalking() {
        var room = mock(Room.class, RETURNS_DEEP_STUBS);
        var unit = mock(RoomUnit.class);
        when(unit.getRoom()).thenReturn(room);
        when(room.isLoaded()).thenReturn(true);
        when(unit.getCacheable()).thenReturn(new HashMap<>());
        when(room.getLayout().getTile((short) 2, (short) 3)).thenReturn(null);
        unit.isWiredTeleporting = true;
        new RoomUnitTeleport(unit, room, 2, 3, 0, 0).run();
        verify(unit).setCanWalk(true);
        assertFalse(unit.isWiredTeleporting);
        verify(unit, never()).setLocation(any());
    }

    @Test
    void successfulTeleportClearsTheOldPathGoalAndRestoresWalking() {
        var room = mock(Room.class, RETURNS_DEEP_STUBS);
        var unit = mock(RoomUnit.class);
        var old = new RoomTile((short) 1, (short) 1, (short) 0, RoomTileState.OPEN, true);
        var destination = new RoomTile((short) 2, (short) 3, (short) 0, RoomTileState.OPEN, true);
        when(unit.getRoom()).thenReturn(room);
        when(room.isLoaded()).thenReturn(true);
        when(unit.getCacheable()).thenReturn(new HashMap<>());
        when(unit.getCurrentLocation()).thenReturn(old);
        when(room.getLayout().getTile((short) 2, (short) 3)).thenReturn(destination);
        unit.isWiredTeleporting = true;
        new RoomUnitTeleport(unit, room, 2, 3, 1.5, 0).run();
        verify(unit).setLocation(destination);
        verify(unit).setGoalLocation(destination);
        verify(unit).setZ(1.5);
        verify(unit).setCanWalk(true);
        verify(unit).removeStatus(RoomUnitStatus.MOVE);
        assertFalse(unit.isWiredTeleporting);
    }
}
