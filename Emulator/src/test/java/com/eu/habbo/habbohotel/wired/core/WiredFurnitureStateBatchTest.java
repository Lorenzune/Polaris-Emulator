package com.eu.habbo.habbohotel.wired.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

import com.eu.habbo.habbohotel.rooms.Room;
import com.eu.habbo.habbohotel.rooms.RoomLayout;
import com.eu.habbo.habbohotel.users.HabboItem;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;

class WiredFurnitureStateBatchTest {
    @Test
    void selfReenqueuingStateEventsAreBoundedAndReleaseTheScope() {
        var room = mock(Room.class);
        when(room.getLayout()).thenReturn(mock(RoomLayout.class));
        var item = mock(HabboItem.class);
        when(item.getExtradata()).thenReturn("1");
        var count = new java.util.concurrent.atomic.AtomicInteger();
        try (var manager = mockStatic(WiredManager.class)) {
            manager.when(WiredManager::isEnabled).thenReturn(true);
            manager.when(() -> WiredManager.handleEvent(any(WiredEvent.class))).thenAnswer(i -> {
                count.incrementAndGet();
                WiredFurnitureStateBatch.defer(room, null, item);
                return true;
            });
            try (var batch = WiredFurnitureStateBatch.begin()) {
                WiredFurnitureStateBatch.defer(room, null, item);
            }
            assertEquals(2048, count.get());
            assertFalse(WiredFurnitureStateBatch.defer(room, null, item));
        }
    }

    @Test
    void decimalTransitionsKeepTheirStatesAndObserveTheCompletedDirectionControl() {
        var room = mock(Room.class);
        when(room.getLayout()).thenReturn(mock(RoomLayout.class));
        var digit = mock(HabboItem.class);
        var value = new AtomicReference<>("0");
        when(digit.getExtradata()).thenAnswer(i -> value.get());
        var subtracting = new AtomicBoolean();
        List<String> delivered = new ArrayList<>();
        try (var manager = mockStatic(WiredManager.class)) {
            manager.when(WiredManager::isEnabled).thenReturn(true);
            manager.when(() -> WiredManager.handleEvent(any(WiredEvent.class))).thenAnswer(i -> {
                assertTrue(subtracting.get(), "A sibling direction effect must finish before decimal carry");
                delivered.add(((WiredEvent) i.getArgument(0)).getText().orElseThrow());
                return true;
            });
            try (var batch = WiredFurnitureStateBatch.begin()) {
                assertTrue(WiredFurnitureStateBatch.defer(room, null, digit));
                try (var nested = WiredFurnitureStateBatch.begin()) {
                    value.set("9");
                    assertTrue(WiredFurnitureStateBatch.defer(room, null, digit));
                }
                assertTrue(delivered.isEmpty());
                subtracting.set(true);
            }
            assertEquals(List.of("0", "9"), delivered);
            assertFalse(WiredFurnitureStateBatch.defer(room, null, digit));
        }
    }
}
