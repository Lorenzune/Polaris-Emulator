package com.eu.habbo.habbohotel.wired.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

import com.eu.habbo.habbohotel.items.Item;
import com.eu.habbo.habbohotel.items.interactions.games.InteractionGameUpCounter;
import com.eu.habbo.habbohotel.items.interactions.wired.triggers.WiredTriggerClockCounter;
import com.eu.habbo.habbohotel.rooms.Room;
import java.sql.ResultSet;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class WiredClockCounterDispatchTest {
    @Test
    void selectedClockAtThreeSecondsDispatchesToADifferentWiredTriggerIdWithoutActor() throws Exception {
        var room = mock(Room.class);
        var clock = mock(InteractionGameUpCounter.class);
        when(clock.getId()).thenReturn(10974);
        when(clock.getCurrentTimeInMs()).thenReturn(3000);
        when(room.getHabboItem(10974)).thenReturn(clock);
        var trigger = new WiredTriggerClockCounter(10978, 16, mock(Item.class), "", 0, 0);
        var stored = mock(ResultSet.class);
        when(stored.getString("wired_data"))
                .thenReturn("{\"minutes\":0,\"halfSecondSteps\":6,\"furniSource\":100,\"itemIds\":[10974]}");
        trigger.loadWiredData(stored, room);
        List<WiredEvent> dispatched = new ArrayList<>();
        try (var manager = mockStatic(WiredManager.class)) {
            manager.when(WiredManager::isEnabled).thenReturn(true);
            manager.when(() -> WiredManager.handleEvent(any())).thenAnswer(invocation -> {
                WiredEvent event = invocation.getArgument(0);
                dispatched.add(event);
                return trigger.matches(trigger, event);
            });
            manager.when(() -> WiredManager.triggerClockCounter(room, clock)).thenCallRealMethod();
            assertTrue(WiredManager.triggerClockCounter(room, clock));
            assertEquals(1, dispatched.size());
            assertTrue(dispatched.getFirst().getActor().isEmpty());
            assertSame(clock, dispatched.getFirst().getSourceItem().orElseThrow());
            when(clock.getCurrentTimeInMs()).thenReturn(2500);
            assertFalse(WiredManager.triggerClockCounter(room, clock));
        }
    }
}
