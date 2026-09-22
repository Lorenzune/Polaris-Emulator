package com.eu.habbo.habbohotel.wired.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.eu.habbo.habbohotel.rooms.Room;
import com.eu.habbo.habbohotel.rooms.RoomTile;
import com.eu.habbo.habbohotel.rooms.RoomUnit;
import com.eu.habbo.habbohotel.rooms.RoomUnitStatus;
import com.eu.habbo.habbohotel.users.HabboItem;
import java.util.List;
import org.junit.jupiter.api.Test;

class WiredStackedItemDetectionTest {

    @Test
    void containsItemOrTileMatchesOnlySpecificSourceItemNotStackedNeighbors() {
        Room room = mock(Room.class);

        HabboItem tileUnderneath = mock(HabboItem.class);
        when(tileUnderneath.getId()).thenReturn(101);
        when(tileUnderneath.getX()).thenReturn((short) 5);
        when(tileUnderneath.getY()).thenReturn((short) 5);

        HabboItem pillowOnTop = mock(HabboItem.class);
        when(pillowOnTop.getId()).thenReturn(202);
        when(pillowOnTop.getX()).thenReturn((short) 5);
        when(pillowOnTop.getY()).thenReturn((short) 5);

        // Room has both items on tile (5, 5)
        when(room.getItemsAt(5, 5)).thenReturn(new java.util.HashSet<>(List.of(tileUnderneath, pillowOnTop)));

        // Trigger is configured only for tileUnderneath (id 101)
        List<HabboItem> triggerItems = List.of(tileUnderneath);

        // When user walks on the pillow, sourceItem is pillowOnTop (id 202)
        // Must NOT match the tile underneath trigger
        assertFalse(WiredTriggerSourceUtil.containsItemOrTile(room, triggerItems, pillowOnTop));

        // When user walks directly on the tile, sourceItem is tileUnderneath (id 101)
        // MUST match the trigger
        assertTrue(WiredTriggerSourceUtil.containsItemOrTile(room, triggerItems, tileUnderneath));
    }

    @Test
    void getItemUserIsOnReturnsChairOrWalkableItemNotUnderlyingTile() {
        Room room = mock(Room.class);
        RoomTile tile =
                new RoomTile((short) 3, (short) 4, (short) 0, com.eu.habbo.habbohotel.rooms.RoomTileState.OPEN, true);

        RoomUnit unit = mock(RoomUnit.class);
        when(unit.getCurrentLocation()).thenReturn(tile);
        when(unit.hasStatus(RoomUnitStatus.SIT)).thenReturn(true);

        HabboItem pillowChair = mock(HabboItem.class);
        when(pillowChair.getId()).thenReturn(505);

        HabboItem floorTile = mock(HabboItem.class);
        when(floorTile.getId()).thenReturn(404);

        when(room.getTallestChair(tile)).thenReturn(pillowChair);

        HabboItem resolved = WiredSourceUtil.getItemUserIsOn(room, unit);
        assertEquals(pillowChair, resolved);
        assertEquals(505, resolved.getId());
    }
}
