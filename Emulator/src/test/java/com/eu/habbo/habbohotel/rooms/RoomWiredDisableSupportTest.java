package com.eu.habbo.habbohotel.rooms;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.eu.habbo.habbohotel.items.Item;
import com.eu.habbo.habbohotel.items.interactions.InteractionWiredDisableControl;
import java.util.Set;
import org.junit.jupiter.api.Test;

class RoomWiredDisableSupportTest {
    @Test
    void checksIndexedDisableControllersInsteadOfScanningAllFloorItems() {
        Room room = mock(Room.class);
        RoomSpecialTypes specialTypes = mock(RoomSpecialTypes.class);
        InteractionWiredDisableControl controller = mock(InteractionWiredDisableControl.class);
        Item baseItem = mock(Item.class);
        when(room.getRoomSpecialTypes()).thenReturn(specialTypes);
        when(specialTypes.getItemsOfType(InteractionWiredDisableControl.class)).thenReturn(Set.of(controller));
        when(controller.getBaseItem()).thenReturn(baseItem);
        when(controller.getExtradata()).thenReturn("1");

        assertTrue(RoomWiredDisableSupport.isWiredDisabled(room));
        verify(specialTypes).getItemsOfType(InteractionWiredDisableControl.class);
        verify(room, never()).getFloorItems();
    }

    @Test
    void anInactiveIndexedControllerDoesNotDisableWired() {
        Room room = mock(Room.class);
        RoomSpecialTypes specialTypes = mock(RoomSpecialTypes.class);
        InteractionWiredDisableControl controller = mock(InteractionWiredDisableControl.class);
        Item baseItem = mock(Item.class);
        when(room.getRoomSpecialTypes()).thenReturn(specialTypes);
        when(specialTypes.getItemsOfType(InteractionWiredDisableControl.class)).thenReturn(Set.of(controller));
        when(controller.getBaseItem()).thenReturn(baseItem);
        when(controller.getExtradata()).thenReturn("0");

        assertFalse(RoomWiredDisableSupport.isWiredDisabled(room));
    }
}
