package com.eu.habbo.habbohotel.items.interactions;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.eu.habbo.habbohotel.items.Item;
import com.eu.habbo.habbohotel.rooms.Room;
import com.eu.habbo.habbohotel.rooms.RoomTile;
import com.eu.habbo.habbohotel.rooms.RoomUnit;
import java.util.Set;
import org.junit.jupiter.api.Test;

class InteractionStackWalkHelperTest {

    @Test
    void multiWalkFlagControlsTileOverrideWithoutChangingHeight() {
        InteractionStackWalkHelper helper = new InteractionStackWalkHelper(1, 1, mock(Item.class), "0", 0, 0);
        helper.setZ(3.25D);

        assertFalse(helper.isMultiWalkEnabled());
        assertFalse(helper.canOverrideTile(null, null, null));

        helper.setMultiWalkEnabled(true);

        assertTrue(helper.isMultiWalkEnabled());
        assertTrue(helper.canOverrideTile(null, null, null));
        assertEquals(3.25D, helper.getZ());
        assertEquals("1", helper.getDatabaseExtraData());

        helper.setMultiWalkEnabled(false);

        assertFalse(helper.isMultiWalkEnabled());
        assertEquals("0", helper.getDatabaseExtraData());
    }

    @Test
    void legacyHeightExtraDataDoesNotAccidentallyEnableMultiWalk() {
        InteractionStackWalkHelper helper = new InteractionStackWalkHelper(1, 1, mock(Item.class), "350", 0, 0);

        assertFalse(helper.isMultiWalkEnabled());
    }

    @Test
    void enabledHelperAllowsAnyKindOfRoomUnitToShareItsTile() {
        InteractionStackWalkHelper helper = new InteractionStackWalkHelper(1, 1, mock(Item.class), "1", 0, 0);
        Room room = mock(Room.class);
        RoomTile tile = mock(RoomTile.class);
        RoomUnit user = mock(RoomUnit.class);
        RoomUnit bot = mock(RoomUnit.class);
        RoomUnit pet = mock(RoomUnit.class);

        when(room.getItemsAt(tile)).thenReturn(Set.of(helper));

        assertTrue(InteractionStackWalkHelper.allowsMultipleUnits(room, tile));
        assertTrue(helper.canOverrideTile(user, room, tile));
        assertTrue(helper.canOverrideTile(bot, room, tile));
        assertTrue(helper.canOverrideTile(pet, room, tile));
    }
}
