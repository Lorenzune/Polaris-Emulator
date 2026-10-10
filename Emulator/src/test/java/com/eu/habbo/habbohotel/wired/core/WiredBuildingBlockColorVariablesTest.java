package com.eu.habbo.habbohotel.wired.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.eu.habbo.habbohotel.items.FurnitureType;
import com.eu.habbo.habbohotel.items.Item;
import com.eu.habbo.habbohotel.items.ItemInteraction;
import com.eu.habbo.habbohotel.items.interactions.InteractionDefault;
import com.eu.habbo.habbohotel.rooms.BuildersClubPalette;
import com.eu.habbo.habbohotel.rooms.BuildersClubRecolorService;
import com.eu.habbo.habbohotel.rooms.Room;
import com.eu.habbo.habbohotel.rooms.RoomWiredRuntime;
import com.eu.habbo.habbohotel.users.HabboItem;
import org.junit.jupiter.api.Test;

class WiredBuildingBlockColorVariablesTest {
    @Test
    void readsTheInstanceRgbButRejectsWritesToNonBuildersClubItems() {
        Room room = mock(Room.class);
        RoomWiredRuntime runtime = mock(RoomWiredRuntime.class);
        when(runtime.getVariableMonitor()).thenReturn(mock(WiredInternalVariableMonitor.class));
        when(room.getWiredRuntime()).thenReturn(runtime);
        HabboItem item = mock(HabboItem.class);
        Item base = mock(Item.class);
        when(item.getBaseItem()).thenReturn(base);
        when(base.getType()).thenReturn(FurnitureType.FLOOR);
        when(base.getName()).thenReturn("bc_block_0_recolorable");
        when(base.getInteractionType()).thenReturn(new ItemInteraction("default", InteractionDefault.class));
        when(runtime.buildersBlockColor(item)).thenReturn(0x2196f3);
        for (String key : new String[] {
            "~recolorable_furni.color.rgb",
            "~recolorable_furni.color.rgb.r",
            "~recolorable_furni.color.rgb.g",
            "~recolorable_furni.color.rgb.b"
        }) {
            assertTrue(WiredInternalVariableSupport.canUseFurniReference(key));
            assertTrue(WiredInternalVariableSupport.hasFurniValue(item, key));
            assertTrue(WiredInternalVariableSupport.canUseFurniDestination(key));
            assertFalse(WiredInternalVariableSupport.writeFurniValue(room, item, key, 0));
        }
        assertEquals(0x2196f3, WiredInternalVariableSupport.readFurniValue(room, item, "~recolorable_furni.color.rgb"));
        assertEquals(33, WiredInternalVariableSupport.readFurniValue(room, item, "~recolorable_furni.color.rgb.r"));
        assertEquals(150, WiredInternalVariableSupport.readFurniValue(room, item, "~recolorable_furni.color.rgb.g"));
        assertEquals(243, WiredInternalVariableSupport.readFurniValue(room, item, "~recolorable_furni.color.rgb.b"));
        when(base.getName()).thenReturn("bc_block_0*1");
        assertFalse(WiredInternalVariableSupport.hasFurniValue(item, "~recolorable_furni.color.rgb"));
        assertNull(WiredInternalVariableSupport.readFurniValue(room, item, "~recolorable_furni.color.rgb"));
        verify(item, never()).setExtradata(anyString());
        when(item.isBuildersClubItem()).thenReturn(true);
        when(room.getHabboItem(item.getId())).thenReturn(item);
        when(runtime.writeBuildersBlockColor(item, BuildersClubPalette.RED, 21)).thenReturn(true);
        when(base.getName()).thenReturn("bc_block_0_recolorable");
        assertTrue(WiredInternalVariableSupport.writeFurniValue(room, item, BuildersClubPalette.RED, 21));
        assertFalse(WiredInternalVariableSupport.writeFurniValue(room, item, BuildersClubPalette.RED, 256));
    }

    @Test
    void paletteRgbRoundTripsWithWhiteAsReset() {
        for (int rgb : new int[] {0, 0x001122, 0x2196f3, 0xffffff})
            assertEquals(
                    rgb, BuildersClubRecolorService.colorValue(BuildersClubRecolorService.colorConfiguration(rgb)));
    }

    @Test
    void componentEditsPreserveTheOtherChannelsAndRejectOutOfRangeValues() {
        assertEquals(0x0096f3, BuildersClubPalette.withComponent(0x2196f3, BuildersClubPalette.RED, 0));
        assertEquals(0x2100f3, BuildersClubPalette.withComponent(0x2196f3, BuildersClubPalette.GREEN, 0));
        assertEquals(0x219600, BuildersClubPalette.withComponent(0x2196f3, BuildersClubPalette.BLUE, 0));
        assertFalse(BuildersClubPalette.validValue(BuildersClubPalette.RGB, 0x1000000));
        assertFalse(BuildersClubPalette.validValue(BuildersClubPalette.RED, -1));
        assertFalse(BuildersClubPalette.validValue("@state", 1));
    }
}
