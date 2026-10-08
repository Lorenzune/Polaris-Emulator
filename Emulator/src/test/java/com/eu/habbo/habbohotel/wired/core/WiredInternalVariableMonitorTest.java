package com.eu.habbo.habbohotel.wired.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.RETURNS_DEEP_STUBS;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

import com.eu.habbo.habbohotel.items.Item;
import com.eu.habbo.habbohotel.items.interactions.wired.triggers.WiredTriggerVariableChanged;
import com.eu.habbo.habbohotel.rooms.Room;
import com.eu.habbo.habbohotel.rooms.RoomUnit;
import com.eu.habbo.habbohotel.wired.WiredTriggerType;
import java.sql.ResultSet;
import java.util.ArrayList;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

class WiredInternalVariableMonitorTest {
    @Test
    void internalChangesRetainOriginAndAreNotDuplicatedByTheNextPoll() throws Exception {
        var room = mock(Room.class, RETURNS_DEEP_STUBS);
        var unit = mock(RoomUnit.class);
        var trigger = new WiredTriggerVariableChanged(1, 2, mock(Item.class), "", 0, 0);
        var stored = mock(ResultSet.class);
        when(stored.getString("wired_data")).thenReturn("""
            {"variableToken":"internal:@position_x","targetType":0,"valueChangedEnabled":true,"increasedEnabled":true}
            """);
        trigger.loadWiredData(stored, room);
        when(room.getRoomSpecialTypes().getTriggers(WiredTriggerType.VARIABLE_CHANGED))
                .thenReturn(Set.of(trigger));
        when(room.getRoomUnits()).thenReturn(Set.of(unit));
        var value = new AtomicInteger(2);
        var events = new ArrayList<WiredEvent>();
        var monitor = new WiredInternalVariableMonitor();
        try (var internal = mockStatic(WiredInternalVariableSupport.class);
                var manager = mockStatic(WiredManager.class)) {
            internal.when(() -> WiredInternalVariableSupport.normalizeKey("@position_x"))
                    .thenReturn("@position_x");
            internal.when(() -> WiredInternalVariableSupport.hasUserValue(room, unit, "@position_x"))
                    .thenReturn(true);
            internal.when(() -> WiredInternalVariableSupport.readUserValue(room, unit, "@position_x"))
                    .thenAnswer(invocation -> value.get());
            manager.when(() -> WiredManager.handleEvent(any(WiredEvent.class))).thenAnswer(invocation -> {
                events.add(invocation.getArgument(0));
                return true;
            });
            monitor.poll(room);
            assertEquals(0, events.size());
            value.set(3);
            monitor.poll(room);
            assertEquals(1, events.size());
            assertEquals("internal:@position_x", events.getFirst().getVariableToken());
            assertEquals(1, events.getFirst().getVariableChangeOrigin());
            try (var inspect = WiredVariableChangeOrigin.begin(4)) {
                value.set(4);
                monitor.changed(room, 0, unit, null, "@position_x", 3, 4);
            }
            monitor.poll(room);
            assertEquals(2, events.size());
            assertEquals(4, events.getLast().getVariableChangeOrigin());
            monitor.changed(room, 0, unit, null, "@position_x", 4, 4);
            assertEquals(
                    WiredEvent.VariableChangeKind.UNCHANGED, events.getLast().getVariableChangeKind());
        }
    }
}
