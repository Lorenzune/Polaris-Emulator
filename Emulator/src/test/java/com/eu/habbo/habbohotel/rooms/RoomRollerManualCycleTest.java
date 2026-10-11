package com.eu.habbo.habbohotel.rooms;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anySet;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.eu.habbo.habbohotel.items.interactions.InteractionRoller;
import com.eu.habbo.habbohotel.users.HabboItem;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;

class RoomRollerManualCycleTest {

    @Test
    void sameTileClickQueuesTheRollerUnderTheUserForTheRoomCycle() {
        Room room = mock(Room.class);
        RoomLayout layout = mock(RoomLayout.class);
        RoomSpecialTypes specialTypes = mock(RoomSpecialTypes.class);
        when(room.getLayout()).thenReturn(layout);
        when(room.getRoomSpecialTypes()).thenReturn(specialTypes);

        RoomTile tile = new RoomTile((short) 2, (short) 3, (short) 0, RoomTileState.OPEN, true);
        when(layout.getTile((short) 2, (short) 3)).thenReturn(tile);

        InteractionRoller roller = mock(InteractionRoller.class);
        when(roller.getId()).thenReturn(41);
        when(room.getItemsAt(tile)).thenReturn(Set.<HabboItem>of(roller));
        when(specialTypes.rollerSnapshot()).thenReturn(Map.of(41, roller));

        RoomUnit unit = new RoomUnit();
        unit.setId(12);
        unit.setLocation(tile);

        RoomRollerManager manager = spy(new RoomRollerManager(room));
        manager.requestRoll(unit, tile);

        assertTrue(manager.hasPendingRollRequests());
        manager.processPendingRollRequests(new HashSet<>());

        assertFalse(manager.hasPendingRollRequests());
        verify(manager).processRoller(eq(roller), anyList(), anySet(), anySet(), anySet());
    }

    @Test
    void queuedRollIsIgnoredIfTheUserLeavesTheTileBeforeTheRoomCycle() {
        Room room = mock(Room.class);
        RoomLayout layout = mock(RoomLayout.class);
        when(room.getLayout()).thenReturn(layout);

        RoomTile requestedTile = new RoomTile((short) 2, (short) 3, (short) 0, RoomTileState.OPEN, true);
        RoomTile newTile = new RoomTile((short) 2, (short) 4, (short) 0, RoomTileState.OPEN, true);
        RoomUnit unit = new RoomUnit();
        unit.setId(12);
        unit.setLocation(requestedTile);

        RoomRollerManager manager = spy(new RoomRollerManager(room));
        manager.requestRoll(unit, requestedTile);
        unit.setLocation(newTile);

        manager.processPendingRollRequests(new HashSet<>());

        assertFalse(manager.hasPendingRollRequests());
        verify(room, never()).getItemsAt(requestedTile);
        verify(manager, never()).processRoller(any(InteractionRoller.class), anyList(), anySet(), anySet(), anySet());
    }
}
