package com.eu.habbo.habbohotel.rooms;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.eu.habbo.habbohotel.items.FurnitureType;
import com.eu.habbo.habbohotel.items.Item;
import com.eu.habbo.habbohotel.users.HabboItem;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

class WiredHighlightRuntimeTest {
    @Test
    void roomScopeSnapshotsRemovalAndUserLifecycleAreIsolated() {
        Room room = mock(Room.class);
        when(room.getId()).thenReturn(54);
        RoomWiredRuntime runtime = new RoomWiredRuntime(
                room,
                () -> {
                    throw new java.sql.SQLException("unused");
                },
                Runnable::run);
        when(room.getWiredRuntime()).thenReturn(runtime);
        HabboItem floor = mock(HabboItem.class);
        Item base = mock(Item.class);
        when(floor.getRoomId()).thenReturn(54);
        when(floor.getBaseItem()).thenReturn(base);
        when(base.getType()).thenReturn(FurnitureType.FLOOR);
        when(room.getHabboItem(12)).thenReturn(floor);
        RoomUnit actor = new RoomUnit();
        actor.setId(0);
        actor.setRoom(room);
        when(room.getRoomUnits()).thenReturn(Set.of(actor));
        var itemState = new WiredHighlightState(10, 12, 1, 0xffd700);
        var userState = new WiredHighlightState(100, 0, 4, 0xff5500);
        runtime.applyHighlights(List.of(itemState, userState, new WiredHighlightState(10, 99, 1, 0)));
        assertEquals(2, runtime.highlightSnapshot().size());
        runtime.applyHighlights(List.of(new WiredHighlightState(10, 12, 0, 0)));
        assertEquals(List.of(userState), runtime.highlightSnapshot());
        actor.setRoom(null);
        assertEquals(List.of(), runtime.highlightSnapshot());
        runtime.applyHighlights(List.of(itemState));
        when(room.getHabboItem(12)).thenReturn(null);
        assertEquals(List.of(), runtime.highlightSnapshot());
    }

    @Test
    void limitsRoomStateAndAllowsExplicitRemovalToFreeCapacity() {
        Room room = mock(Room.class);
        when(room.getId()).thenReturn(54);
        RoomWiredRuntime runtime = new RoomWiredRuntime(
                room,
                () -> {
                    throw new java.sql.SQLException("unused");
                },
                Runnable::run);
        Item base = mock(Item.class);
        when(base.getType()).thenReturn(FurnitureType.FLOOR);
        java.util.List<WiredHighlightState> requested = new java.util.ArrayList<>();
        for (int id = 1; id <= 513; id++) {
            HabboItem item = mock(HabboItem.class);
            when(item.getRoomId()).thenReturn(54);
            when(item.getBaseItem()).thenReturn(base);
            when(room.getHabboItem(id)).thenReturn(item);
            requested.add(new WiredHighlightState(10, id, 1, 0xffd700));
        }
        runtime.applyHighlights(requested);
        assertEquals(512, runtime.highlightSnapshot().size());
        runtime.applyHighlights(
                List.of(new WiredHighlightState(10, 1, 0, 0), new WiredHighlightState(10, 513, 1, 0xffd700)));
        assertEquals(512, runtime.highlightSnapshot().size());
        org.junit.jupiter.api.Assertions.assertTrue(
                runtime.highlightSnapshot().stream().anyMatch(state -> state.id() == 513));
    }

    @Test
    void rejectsInvalidCategoriesColorsAndStyles() {
        assertThrows(IllegalArgumentException.class, () -> new WiredHighlightState(10, 0, 1, 0));
        assertThrows(IllegalArgumentException.class, () -> new WiredHighlightState(100, 1, 25, 0));
        assertThrows(IllegalArgumentException.class, () -> new WiredHighlightState(100, 1, 1, 0x1000000));
        assertThrows(IllegalArgumentException.class, () -> new WiredHighlightState(500, 1, 1, 0));
    }
}
