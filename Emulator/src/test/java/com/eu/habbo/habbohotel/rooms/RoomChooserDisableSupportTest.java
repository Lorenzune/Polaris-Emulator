package com.eu.habbo.habbohotel.rooms;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.eu.habbo.habbohotel.items.Item;
import com.eu.habbo.habbohotel.items.ItemInteraction;
import com.eu.habbo.habbohotel.items.interactions.InteractionChooserDisableControl;
import com.eu.habbo.habbohotel.users.HabboItem;
import com.eu.habbo.messages.outgoing.rooms.items.ChooserDisabledStateComposer;
import io.netty.buffer.ByteBuf;
import java.util.Set;
import org.junit.jupiter.api.Test;

class RoomChooserDisableSupportTest {
    @Test
    void onlyStateOneOfTheRegisteredInteractionDisablesChooser() {
        Room room = mock(Room.class);
        HabboItem control = item("conf_chooser_disable", "0");
        when(room.getFloorItems()).thenReturn(Set.of(control));
        assertFalse(RoomChooserDisableSupport.isChooserDisabled(room));
        when(control.getExtradata()).thenReturn("1");
        assertTrue(RoomChooserDisableSupport.isChooserDisabled(room));
        when(control.getExtradata()).thenReturn("2");
        assertFalse(RoomChooserDisableSupport.isChooserDisabled(room));
        HabboItem unrelated = item("default", "1");
        when(room.getFloorItems()).thenReturn(Set.of(unrelated));
        assertFalse(RoomChooserDisableSupport.isChooserDisabled(room));
        assertFalse(RoomChooserDisableSupport.isChooserDisabled(null));
    }

    @Test
    void removingOneControllerDoesNotReleaseAnotherActiveController() {
        Room room = mock(Room.class);
        HabboItem first = item("conf_chooser_disable", "1");
        HabboItem second = item("conf_chooser_disable", "1");
        when(room.getFloorItems()).thenReturn(Set.of(first, second));
        assertTrue(RoomChooserDisableSupport.isChooserDisabled(room));
        when(room.getFloorItems()).thenReturn(Set.of(second));
        assertTrue(RoomChooserDisableSupport.isChooserDisabled(room));
        when(room.getFloorItems()).thenReturn(Set.of());
        assertFalse(RoomChooserDisableSupport.isChooserDisabled(room));
    }

    @Test
    void packetContainsRoomIdAndTheCurrentDisabledFlag() {
        Room room = mock(Room.class);
        when(room.getId()).thenReturn(259);
        HabboItem control = item("conf_chooser_disable", "1");
        when(room.getFloorItems()).thenReturn(Set.of(control));
        ByteBuf packet = new ChooserDisabledStateComposer(room).compose().get();
        try {
            assertEquals(5114, packet.getUnsignedShort(4));
            assertEquals(259, packet.getInt(6));
            assertTrue(packet.getBoolean(10));
            assertEquals(11, packet.readableBytes());
        } finally {
            packet.release();
        }
    }

    private static HabboItem item(String interaction, String state) {
        Item base = mock(Item.class);
        when(base.getInteractionType())
                .thenReturn(new ItemInteraction(interaction, InteractionChooserDisableControl.class));
        HabboItem item = mock(HabboItem.class);
        when(item.getBaseItem()).thenReturn(base);
        when(item.getExtradata()).thenReturn(state);
        return item;
    }
}
