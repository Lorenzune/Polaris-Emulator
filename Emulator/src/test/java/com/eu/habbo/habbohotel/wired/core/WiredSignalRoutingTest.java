package com.eu.habbo.habbohotel.wired.core;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.eu.habbo.habbohotel.rooms.Room;
import com.eu.habbo.habbohotel.users.HabboItem;
import com.eu.habbo.habbohotel.items.interactions.wired.triggers.WiredTriggerReceiveSignal;
import com.eu.habbo.habbohotel.wired.api.IWiredTrigger;
import com.eu.habbo.habbohotel.wired.api.WiredStack;
import java.sql.ResultSet;
import java.util.*;
import java.util.concurrent.atomic.*;
import org.junit.jupiter.api.Test;

class WiredSignalRoutingTest {
    @Test void staticAntennasAreIndexedWhileDynamicAndPluginReceiversKeepOriginalOrder() throws Exception {
        Room room = room();
        WiredStack ten = receiver(room, 1, 100, true, 0, 10);
        WiredStack dynamic = receiver(room, 2, 200, true, 0);
        WiredStack twenty = receiver(room, 3, 100, true, 0, 20);
        WiredStack plugin = new WiredStack(mock(HabboItem.class), mock(IWiredTrigger.class), List.of(), List.of());
        WiredStack both = receiver(room, 4, 100, true, 0, 10, 20);
        WiredStack empty = receiver(room, 5, 100, true, 0);
        WiredStack legacy = receiver(room, 6, 100, false, 10);
        AtomicInteger lookups = new AtomicInteger();
        WiredStackRepository repository = new WiredStackRepository((ignored, type) -> {
            assertEquals(WiredEvent.Type.SIGNAL_RECEIVED, type); lookups.incrementAndGet();
            return List.of(ten, dynamic, twenty, plugin, both, empty, legacy);
        });
        List<WiredStack> result = repository.getSignalStacks(room, 10);
        assertEquals(List.of(ten, dynamic, plugin, both, legacy), result);
        assertSame(result, repository.getSignalStacks(room, 10));
        assertEquals(List.of(dynamic, twenty, plugin, both), repository.getSignalStacks(room, 20));
        for (int channel = 100; channel < 1000; channel++)
            assertEquals(List.of(dynamic, plugin), repository.getSignalStacks(room, channel));
        assertEquals(1, lookups.get());
        assertThrows(UnsupportedOperationException.class, () -> result.add(empty));
    }

    @Test void oneHundredStaticReceiversReduceCandidatesToTheSingleMatchingAntenna() throws Exception {
        Room room = room();
        List<WiredStack> stacks = new ArrayList<>();
        for (int i = 0; i < 100; i++) stacks.add(receiver(room, i + 1, 100, true, 0, 1000 + i));
        WiredStackRepository repository = new WiredStackRepository((ignored, type) -> stacks);
        for (int i = 0; i < 100; i++) assertEquals(List.of(stacks.get(i)), repository.getSignalStacks(room, 1000 + i));
    }

    @Test void mutationLifecycleAndExplicitInvalidationRebuildRouting() throws Exception {
        Room room = room();
        AtomicLong generation = new AtomicLong(1), lifecycle = new AtomicLong(1);
        when(room.getWiredCacheGeneration()).thenAnswer(call -> generation.get());
        when(room.getLifecycleGeneration()).thenAnswer(call -> lifecycle.get());
        WiredStack old = receiver(room, 1, 100, true, 0, 10);
        WiredStack replacement = receiver(room, 2, 100, true, 0, 20);
        AtomicReference<List<WiredStack>> stacks = new AtomicReference<>(List.of(old));
        AtomicInteger lookups = new AtomicInteger();
        WiredStackRepository repository = new WiredStackRepository((ignored, type) -> { lookups.incrementAndGet(); return stacks.get(); });
        assertEquals(List.of(old), repository.getSignalStacks(room, 10));
        stacks.set(List.of(replacement)); generation.incrementAndGet();
        assertEquals(List.of(), repository.getSignalStacks(room, 10));
        assertEquals(List.of(replacement), repository.getSignalStacks(room, 20));
        lifecycle.incrementAndGet(); repository.getSignalStacks(room, 20);
        repository.clearRoomSourceStackCache(room.getId()); repository.getSignalStacks(room, 20);
        repository.clearAllSourceStackCache(); repository.getSignalStacks(room, 20);
        assertEquals(5, lookups.get());
    }

    @Test void invalidationDuringBuildCannotPublishStaleRouting() throws Exception {
        Room room = room();
        WiredStack old = receiver(room, 1, 100, true, 0, 10);
        WiredStack replacement = receiver(room, 2, 100, true, 0, 20);
        AtomicInteger lookups = new AtomicInteger();
        AtomicReference<WiredStackRepository> owner = new AtomicReference<>();
        WiredStackRepository repository = new WiredStackRepository((ignored, type) -> {
            if (lookups.incrementAndGet() == 1) { owner.get().clearAllSourceStackCache(); return List.of(old); }
            return List.of(replacement);
        });
        owner.set(repository);
        assertEquals(List.of(), repository.getSignalStacks(room, 10));
        assertEquals(List.of(replacement), repository.getSignalStacks(room, 20));
        assertEquals(2, lookups.get());
    }

    private static Room room() {
        Room room = mock(Room.class);
        when(room.getId()).thenReturn(42);
        when(room.getLifecycleGeneration()).thenReturn(1L);
        when(room.getWiredCacheGeneration()).thenReturn(1L);
        return room;
    }
    private static WiredStack receiver(Room room, int id, int source, boolean nativeSignal, int channel, int... antennas) throws Exception {
        WiredTriggerReceiveSignal receiver = new WiredTriggerReceiveSignal(id, 16, null, "", 0, 0);
        for (int antenna : antennas) {
            HabboItem item = mock(HabboItem.class); when(item.getId()).thenReturn(antenna);
            when(room.getHabboItem(antenna)).thenReturn(item);
        }
        ResultSet row = mock(ResultSet.class);
        when(row.getString("wired_data")).thenReturn("{\"nativeSignal\":" + nativeSignal
                + ",\"furniSource\":" + source + ",\"channel\":" + channel + ",\"itemIds\":" + Arrays.toString(antennas) + "}");
        receiver.loadWiredData(row, room);
        return new WiredStack(receiver, receiver, List.of(), List.of());
    }
}
