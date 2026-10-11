package com.eu.habbo.habbohotel.wired.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

import com.eu.habbo.habbohotel.items.Item;
import com.eu.habbo.habbohotel.items.interactions.InteractionDefault;
import com.eu.habbo.habbohotel.rooms.Room;
import com.eu.habbo.habbohotel.rooms.RoomLayout;
import com.eu.habbo.habbohotel.rooms.RoomUnit;
import com.eu.habbo.habbohotel.users.HabboItem;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class WiredFurnitureStateEventsTest {
    @Test
    void actualChangesEmitOncePreserveTheClickActorAndSuppressRecursiveUpdates() throws Exception {
        Room room = mock(Room.class);
        when(room.isLoaded()).thenReturn(true);
        when(room.getLayout()).thenReturn(mock(RoomLayout.class));
        HabboItem item = new InteractionDefault(10, 1, mock(Item.class), "0", 0, 0);
        when(room.getHabboItem(10)).thenReturn(item);
        RoomUnit actor = mock(RoomUnit.class);
        List<WiredEvent> events = new ArrayList<>();
        try (var manager = mockStatic(WiredManager.class)) {
            manager.when(WiredManager::isEnabled).thenReturn(true);
            manager.when(() -> WiredManager.handleEvent(any())).thenAnswer(invocation -> {
                WiredEvent event = invocation.getArgument(0);
                events.add(event);
                if (event.getType() == WiredEvent.Type.FURNI_STATE_UPDATED) {
                    item.setExtradata("2");
                    WiredFurnitureStateEvents.stateUpdated(room, item);
                }
                return true;
            });
            assertFalse(item.consumeWiredStateChange());
            WiredFurnitureStateEvents.use(room, actor, item, () -> {
                item.setExtradata("1");
                WiredFurnitureStateEvents.stateUpdated(room, item);
            });
            assertEquals(
                    List.of(WiredEvent.Type.FURNI_STATE_UPDATED, WiredEvent.Type.FURNI_USED),
                    events.stream().map(WiredEvent::getType).toList());
            assertTrue(events.stream().allMatch(event -> event.getActor().orElse(null) == actor));
            WiredFurnitureStateEvents.stateUpdated(room, item);
            assertEquals(2, events.size());
            events.clear();
            item.setExtradata("2");
            WiredFurnitureStateEvents.stateUpdated(room, item);
            assertTrue(events.isEmpty());
            item.setExtradata("3");
            WiredFurnitureStateEvents.stateUpdated(room, item);
            assertEquals(1, events.size());
            assertTrue(events.getFirst().getActor().isEmpty());
        }
    }

    @Test
    void failedUseRestoresActorScopeAndDetachedItemsDoNotEmitStateEvents() throws Exception {
        Room room = mock(Room.class);
        when(room.isLoaded()).thenReturn(true);
        when(room.getLayout()).thenReturn(mock(RoomLayout.class));
        HabboItem item = new InteractionDefault(10, 1, mock(Item.class), "0", 0, 0);
        List<WiredEvent> events = new ArrayList<>();
        try (var manager = mockStatic(WiredManager.class)) {
            manager.when(WiredManager::isEnabled).thenReturn(true);
            manager.when(() -> WiredManager.handleEvent(any())).thenAnswer(invocation -> {
                events.add(invocation.getArgument(0));
                return true;
            });
            assertThrows(
                    IllegalStateException.class,
                    () -> WiredFurnitureStateEvents.use(room, mock(RoomUnit.class), item, () -> {
                        throw new IllegalStateException("failed interaction");
                    }));
            item.setExtradata("1");
            WiredFurnitureStateEvents.stateUpdated(room, item);
            assertTrue(events.isEmpty());
            when(room.getHabboItem(10)).thenReturn(item);
            item.setExtradata("2");
            WiredFurnitureStateEvents.stateUpdated(room, item);
            assertEquals(1, events.size());
            assertTrue(events.getFirst().getActor().isEmpty());
        }
    }

    @Test
    void useTriggerCanChangeTheSameItemAndEmitASeparateStateEvent() throws Exception {
        Room room = mock(Room.class);
        when(room.isLoaded()).thenReturn(true);
        when(room.getLayout()).thenReturn(mock(RoomLayout.class));
        HabboItem item = new InteractionDefault(10, 1, mock(Item.class), "0", 0, 0);
        when(room.getHabboItem(10)).thenReturn(item);
        List<WiredEvent.Type> types = new ArrayList<>();
        try (var manager = mockStatic(WiredManager.class)) {
            manager.when(WiredManager::isEnabled).thenReturn(true);
            manager.when(() -> WiredManager.handleEvent(any())).thenAnswer(invocation -> {
                WiredEvent event = invocation.getArgument(0);
                types.add(event.getType());
                if (event.getType() == WiredEvent.Type.FURNI_USED) {
                    item.setExtradata("1");
                    WiredFurnitureStateEvents.stateUpdated(room, item);
                }
                return true;
            });
            WiredFurnitureStateEvents.use(room, mock(RoomUnit.class), item, () -> {});
            assertEquals(List.of(WiredEvent.Type.FURNI_USED, WiredEvent.Type.FURNI_STATE_UPDATED), types);
        }
    }
}
