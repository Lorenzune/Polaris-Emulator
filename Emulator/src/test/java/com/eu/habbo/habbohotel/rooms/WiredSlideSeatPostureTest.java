package com.eu.habbo.habbohotel.rooms;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import com.eu.habbo.habbohotel.items.Item;
import com.eu.habbo.habbohotel.users.Habbo;
import com.eu.habbo.habbohotel.users.HabboItem;
import com.eu.habbo.habbohotel.wired.core.WiredUserMovementHelper;
import java.util.List;
import org.junit.jupiter.api.Test;

class WiredSlideSeatPostureTest {
    @Test
    void suppressedSlidePacketsStillRecalculateSeatBaseHeight() {
        Room room = mock(Room.class, RETURNS_DEEP_STUBS);
        RoomTile tile = new RoomTile((short)25, (short)25, (short)0, RoomTileState.OPEN, true);
        when(room.getLayout().getTile((short)25, (short)25)).thenReturn(tile);
        HabboItem chair = mock(HabboItem.class);
        Item base = mock(Item.class);
        when(chair.getBaseItem()).thenReturn(base);
        when(base.allowSit()).thenReturn(true);
        when(base.getHeight()).thenReturn(1.0);
        when(chair.getZ()).thenReturn(0.1);
        when(chair.getExtradata()).thenReturn("0");
        when(room.getTallestChair(tile)).thenReturn(chair);
        RoomUnit unit = new RoomUnit();
        unit.setCurrentLocation(tile);
        unit.setPreviousLocation(tile);
        unit.setZ(1.1);
        unit.setStatus(RoomUnitStatus.SIT, "1.0");
        Habbo habbo = mock(Habbo.class);
        when(habbo.getRoomUnit()).thenReturn(unit);
        try (var helper = mockStatic(WiredUserMovementHelper.class)) {
            helper.when(() -> WiredUserMovementHelper.shouldSuppressStatusUpdate(unit)).thenReturn(true);
            new RoomUnitManager(room).updateHabbosAt((short)25, (short)25, List.of(habbo));
            assertEquals(0.1, unit.getZ());
            assertTrue(unit.hasStatus(RoomUnitStatus.SIT));
            verify(room, never()).sendComposer(any());
        }
    }
}
