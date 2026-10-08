package com.eu.habbo.habbohotel.items.interactions.wired.conditions;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.eu.habbo.WiredPlatform;
import com.eu.habbo.habbohotel.items.Item;
import com.eu.habbo.habbohotel.items.interactions.wired.selector.WiredEffectFurniOnFurni;
import com.eu.habbo.habbohotel.rooms.*;
import com.eu.habbo.habbohotel.users.HabboItem;
import com.eu.habbo.habbohotel.wired.core.*;
import java.sql.ResultSet;
import java.util.Set;
import org.junit.jupiter.api.Test;

class WiredFurniAboveOverlapTest {
    @Test
    void overlappingDecorationAboveBaseIsDetectedByConditionsAndSelector() throws Exception {
        Room room = mock(Room.class, RETURNS_DEEP_STUBS);
        when(room.getId()).thenReturn(267);
        HabboItem piece = mock(HabboItem.class, RETURNS_DEEP_STUBS);
        HabboItem skull = mock(HabboItem.class, RETURNS_DEEP_STUBS);
        when(piece.getId()).thenReturn(810);
        when(piece.getRoomId()).thenReturn(267);
        when(piece.getZ()).thenReturn(2.04);
        when(piece.getBaseItem().getHeight()).thenReturn(0.25);
        when(piece.getBaseItem().getWidth()).thenReturn(1);
        when(piece.getBaseItem().getLength()).thenReturn(1);
        when(skull.getZ()).thenReturn(2.21);
        when(room.getHabboItem(810)).thenReturn(piece);
        RoomTile tile = new RoomTile((short) 0, (short) 0, (short) 0, RoomTileState.OPEN, true);
        when(room.getLayout().getTile((short) 0, (short) 0)).thenReturn(tile);
        when(room.getLayout().getTilesAt(tile, 1, 1, 0)).thenReturn(Set.of(tile));
        when(room.getItemsAt(tile)).thenReturn(Set.of(piece, skull));
        var positive = new WiredConditionFurniHaveFurni(1, 16, mock(Item.class), "", 0, 0);
        var negative = new WiredConditionNotFurniHaveFurni(2, 16, mock(Item.class), "", 0, 0);
        ResultSet row = mock(ResultSet.class);
        when(row.getString("wired_data")).thenReturn("{\"all\":true,\"furniSource\":0,\"itemIds\":[]}");
        positive.loadWiredData(row, room);
        negative.loadWiredData(row, room);
        var context = new WiredContext(WiredEvent.builder(WiredEvent.Type.USER_CLICKS_FURNI, room)
                .sourceItem(piece).build(), null, mock(WiredServices.class), new WiredState(100));
        try (var platform = mockStatic(WiredPlatform.class)) {
            assertTrue(positive.evaluate(context));
            assertFalse(negative.evaluate(context));
            var selector = new WiredEffectFurniOnFurni(3, 16, mock(Item.class), "", 0, 0);
            when(row.getString("wired_data")).thenReturn(
                    "{\"selectionType\":0,\"furniSource\":0,\"itemIds\":[]}");
            selector.loadWiredData(row, room);
            selector.execute(context);
            assertEquals(Set.of(skull), context.targets().items());
            when(skull.getZ()).thenReturn(2.04);
            assertFalse(positive.evaluate(context));
            assertTrue(negative.evaluate(context));

            // SWF keeps the origin outside the one-element require-all option.
            // A normal attacker must not make its crowned victim look uncrowned.
            var settings = new com.eu.habbo.habbohotel.items.interactions.wired.WiredSettings(
                    new int[]{0}, "", new int[0], 0, 0);
            settings.setNativeVariableFormat(true);
            settings.setFurniSourceTypes(new int[]{200});
            var attacker = mock(HabboItem.class, RETURNS_DEEP_STUBS);
            when(attacker.getId()).thenReturn(811);
            when(attacker.getRoomId()).thenReturn(267);
            when(room.getHabboItem(811)).thenReturn(attacker);
            when(attacker.getX()).thenReturn((short) 1);
            when(attacker.getBaseItem().getWidth()).thenReturn(1);
            when(attacker.getBaseItem().getLength()).thenReturn(1);
            var emptyTile = new RoomTile((short) 1, (short) 0, (short) 0, RoomTileState.OPEN, true);
            when(room.getLayout().getTile((short) 1, (short) 0)).thenReturn(emptyTile);
            when(room.getLayout().getTilesAt(emptyTile, 1, 1, 0)).thenReturn(Set.of(emptyTile));
            when(room.getItemsAt(emptyTile)).thenReturn(Set.of(attacker));
            var victimContext = new WiredContext(WiredEvent.builder(WiredEvent.Type.SIGNAL_RECEIVED, room)
                    .sourceItem(attacker).signalPayloadExplicit(true).signalSourceItems(java.util.List.of(attacker)).build(),
                    null, mock(WiredServices.class), new WiredState(100));
            victimContext.targets().setItems(java.util.List.of(piece));
            when(skull.getZ()).thenReturn(2.21);
            try (var emulator = mockStatic(com.eu.habbo.Emulator.class)) {
                var config = mock(com.eu.habbo.core.ConfigurationManager.class);
                when(config.getInt("hotel.wired.furni.selection.count")).thenReturn(50);
                emulator.when(com.eu.habbo.Emulator::getConfig).thenReturn(config);
                assertTrue(positive.saveData(settings));
                assertTrue(negative.saveData(settings));
                assertTrue(positive.evaluate(victimContext));
                assertFalse(negative.evaluate(victimContext));
                assertTrue(negative.getWiredData().contains("\"furniSource\":200"));
                settings.setFurniSourceTypes(new int[]{201});
                assertTrue(negative.saveData(settings));
                assertTrue(negative.getWiredData().contains("\"furniSource\":201"));
                assertTrue(negative.evaluate(victimContext));
            }
        }
    }
}
