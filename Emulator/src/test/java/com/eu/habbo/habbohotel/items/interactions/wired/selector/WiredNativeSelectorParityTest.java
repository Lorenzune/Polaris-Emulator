package com.eu.habbo.habbohotel.items.interactions.wired.selector;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.RETURNS_DEEP_STUBS;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.times;
import static org.mockito.ArgumentMatchers.anyInt;

import com.eu.habbo.habbohotel.items.Item;
import com.eu.habbo.habbohotel.items.interactions.wired.WiredSettings;
import com.eu.habbo.habbohotel.rooms.Room;
import com.eu.habbo.habbohotel.users.HabboItem;
import com.eu.habbo.habbohotel.wired.core.WiredContext;
import com.eu.habbo.habbohotel.wired.core.WiredEvent;
import com.eu.habbo.habbohotel.wired.core.WiredServices;
import com.eu.habbo.habbohotel.wired.core.WiredState;
import java.sql.ResultSet;
import java.util.Arrays;
import java.util.Set;
import org.junit.jupiter.api.Test;

class WiredNativeSelectorParityTest {
    static WiredSettings nativeSettings(
            int[] ints, String text, int[] items, int source, boolean filter, boolean invert) {
        var settings = new WiredSettings(ints, text, items, 0);
        settings.setNativeVariableFormat(true);
        settings.setFurniSourceTypes(new int[] {source});
        settings.setFilterExisting(filter);
        settings.setInvert(invert);
        return settings;
    }

    static WiredContext context(Room room) {
        return new WiredContext(
                WiredEvent.builder(WiredEvent.Type.VARIABLE_CHANGED, room).build(),
                null,
                mock(WiredServices.class),
                new WiredState(100));
    }

    @Test
    void typeSourceSurvivesSaveReloadAndFiltersExistingTargets() throws Exception {
        var room = mock(Room.class, RETURNS_DEEP_STUBS);
        var item = mock(HabboItem.class, RETURNS_DEEP_STUBS);
        var different = mock(HabboItem.class, RETURNS_DEEP_STUBS);
        when(item.getBaseItem().getId()).thenReturn(10);
        when(different.getBaseItem().getId()).thenReturn(11);
        when(room.getHabboItem(9)).thenReturn(item);
        when(room.getFloorItems()).thenReturn(Set.of(item, different));
        var selector = new WiredEffectFurniByType(1, 2, mock(Item.class), "", 0, 0);
        selector.saveData(nativeSettings(new int[] {0}, "", new int[] {9}, 100, true, false), null);
        var stored = mock(ResultSet.class);
        when(stored.getString("wired_data")).thenReturn(selector.getWiredData());
        var reloaded = new WiredEffectFurniByType(2, 2, mock(Item.class), "", 0, 0);
        reloaded.loadWiredData(stored, room);
        var ctx = context(room);
        ctx.targets().setItems(Set.of(different));
        reloaded.execute(ctx);
        assertTrue(ctx.targets().items().isEmpty());
        assertTrue(ctx.targets().isItemsModifiedBySelector());
        ctx.targets().setItems(Set.of(item, different));
        reloaded.execute(ctx);
        assertEquals(Set.of(item), ctx.targets().items());
    }

    @Test
    void spiralMaskRetainsSignedBitsAndTheOuterRadius() throws Exception {
        int[] mask = new int[14];
        Arrays.fill(mask, -1);
        var offsets = WiredNativeSelectorSettings.spiralOffsets(mask);
        assertEquals(441, offsets.size());
        assertArrayEquals(new int[] {0, 0}, offsets.getFirst());
        assertArrayEquals(new int[] {1, 0}, offsets.get(1));
        assertArrayEquals(new int[] {1, -1}, offsets.get(2));
        assertArrayEquals(new int[] {10, 10}, offsets.getLast());
        var room = mock(Room.class, RETURNS_DEEP_STUBS);
        var actorItem = mock(HabboItem.class);
        when(room.getHabboItem(9)).thenReturn(actorItem);
        var edge = mock(HabboItem.class);
        when(room.getItemsAt(10, 10)).thenReturn(Set.of(edge));
        var selector = new WiredEffectFurniNeighborhood(1, 2, mock(Item.class), "", 0, 0);
        int[] ints = new int[17];
        System.arraycopy(mask, 0, ints, 3, 14);
        selector.saveData(nativeSettings(ints, "", new int[] {9}, 100, false, false), null);
        var stored = mock(ResultSet.class);
        when(stored.getString("wired_data")).thenReturn(selector.getWiredData());
        var reloaded = new WiredEffectFurniNeighborhood(2, 2, mock(Item.class), "", 0, 0);
        reloaded.loadWiredData(stored, room);
        var ctx = context(room);
        reloaded.execute(ctx);
        assertEquals(Set.of(edge), ctx.targets().items());
    }

    @Test
    void ordinaryNeighborhoodOnlyReadsItsSelectedTiles() throws Exception {
        var room = mock(Room.class, RETURNS_DEEP_STUBS);
        var source = mock(HabboItem.class);
        when(room.getHabboItem(9)).thenReturn(source);
        var target = mock(HabboItem.class);
        when(room.getItemsAt(0, 0)).thenReturn(Set.of(target));
        var selector = new WiredEffectFurniNeighborhood(1, 2, mock(Item.class), "", 0, 0);
        int[] mask = new int[17];
        mask[3] = 1;
        selector.saveData(nativeSettings(mask, "", new int[] {9}, 100, false, false), null);
        var ctx = context(room);
        selector.execute(ctx);
        assertEquals(Set.of(target), ctx.targets().items());
        verify(room, times(1)).getItemsAt(anyInt(), anyInt());
    }

    @Test
    void invertedNeighborhoodStillReadsTheFullComplement() throws Exception {
        var room = mock(Room.class, RETURNS_DEEP_STUBS);
        var source = mock(HabboItem.class);
        when(room.getHabboItem(9)).thenReturn(source);
        var center = mock(HabboItem.class);
        var outer = mock(HabboItem.class);
        when(room.getItemsAt(0, 0)).thenReturn(Set.of(center));
        when(room.getItemsAt(10, 10)).thenReturn(Set.of(outer));
        var selector = new WiredEffectFurniNeighborhood(1, 2, mock(Item.class), "", 0, 0);
        int[] mask = new int[17];
        mask[3] = 1;
        selector.saveData(nativeSettings(mask, "", new int[] {9}, 100, false, true), null);
        var ctx = context(room);
        selector.execute(ctx);
        assertEquals(Set.of(outer), ctx.targets().items());
    }

    @Test
    void namesAndAltitudeUseNativeTextAndUnits() {
        var names = new WiredEffectUsersByName(1, 2, mock(Item.class), "", 0, 0);
        var decoded = WiredNativeSelectorSettings.normalize(
                names, nativeSettings(new int[0], "Alice\tBob", new int[0], 100, true, false));
        assertEquals("Alice\nBob", decoded.getStringParam());
        assertArrayEquals(new int[] {1, 0}, decoded.getIntParams());
        var altitude = new WiredEffectFurniAltitude(2, 2, mock(Item.class), "", 0, 0);
        decoded = WiredNativeSelectorSettings.normalize(
                altitude, nativeSettings(new int[] {125, 2}, "", new int[0], 100, false, true));
        assertEquals("1.25", decoded.getStringParam());
        assertArrayEquals(new int[] {2, 0, 1}, decoded.getIntParams());
    }
}
