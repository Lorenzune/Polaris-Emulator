package com.eu.habbo.habbohotel.wired.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.RETURNS_DEEP_STUBS;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.eu.habbo.habbohotel.items.Item;
import com.eu.habbo.habbohotel.items.interactions.InteractionDefault;
import com.eu.habbo.habbohotel.items.interactions.InteractionWiredTrigger;
import com.eu.habbo.habbohotel.items.interactions.wired.triggers.WiredTriggerVariableChanged;
import com.eu.habbo.habbohotel.rooms.Room;
import com.eu.habbo.habbohotel.rooms.RoomTile;
import com.eu.habbo.habbohotel.rooms.RoomTileState;
import com.eu.habbo.habbohotel.rooms.RoomUnit;
import com.eu.habbo.habbohotel.rooms.RoomUnitStatus;
import com.eu.habbo.habbohotel.rooms.RoomUserRotation;
import com.eu.habbo.habbohotel.users.HabboItem;
import com.eu.habbo.habbohotel.wired.WiredTriggerType;
import java.sql.ResultSet;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;

class WiredVariablePollingCostTest {
    @Test
    void manyTriggersShareEntitySnapshotsAndRetainAllDistinctChangeEvents() throws Exception {
        var room = mock(Room.class, RETURNS_DEEP_STUBS);
        Set<RoomUnit> units = new LinkedHashSet<>();
        for (int index = 0; index < 100; index++) {
            var unit = new RoomUnit();
            unit.setId(index);
            unit.setCurrentLocation(tile(index % 10, index / 10));
            units.add(unit);
        }
        Set<HabboItem> furniture = new LinkedHashSet<>();
        Item base = mock(Item.class);
        for (int index = 0; index < 1000; index++) {
            furniture.add(new InteractionDefault(1000 + index, 1, base, "0", 0, 0));
        }
        Set<InteractionWiredTrigger> triggers = new LinkedHashSet<>();
        for (int index = 0; index < 20; index++) {
            triggers.add(trigger(4 * index + 1, 0, "@position_x", room));
            triggers.add(trigger(4 * index + 2, 0, "@altitude", room));
            triggers.add(trigger(4 * index + 3, 1, "@position_x", room));
            triggers.add(trigger(4 * index + 4, 1, "@altitude", room));
        }
        when(room.getRoomSpecialTypes().getTriggers(WiredTriggerType.VARIABLE_CHANGED))
                .thenReturn(triggers);
        // Model the real methods: every call allocates and fills a fresh collection.
        when(room.getRoomUnits()).thenAnswer(ignored -> new HashSet<>(units));
        when(room.getFloorItems()).thenAnswer(ignored -> new HashSet<>(furniture));
        var events = new ArrayList<WiredEvent>();
        var monitor = new WiredInternalVariableMonitor();
        try (var manager = mockStatic(WiredManager.class)) {
            manager.when(WiredManager::getGson).thenReturn(new com.google.gson.Gson());
            manager.when(() -> WiredManager.handleEvent(any(WiredEvent.class))).thenAnswer(call -> {
                events.add(call.getArgument(0));
                return true;
            });
            monitor.poll(room);
            assertTrue(events.isEmpty(), "First observation must not fire a change");
            RoomUnit moved = units.iterator().next();
            HabboItem raised = furniture.iterator().next();
            moved.setCurrentLocation(tile(5, 0));
            raised.setZ(1.25);
            monitor.poll(room);
            assertEquals(2, events.size(), "Duplicate watchers must not duplicate changes");
            WiredEvent movement = events.stream()
                    .filter(event -> event.getActor().orElse(null) == moved)
                    .findFirst()
                    .orElseThrow();
            assertEquals("internal:@position_x", movement.getVariableToken());
            assertEquals(WiredEvent.VariableChangeKind.INCREASED, movement.getVariableChangeKind());
            WiredEvent elevation = events.stream()
                    .filter(event -> event.getSourceItem().orElse(null) == raised)
                    .findFirst()
                    .orElseThrow();
            assertEquals("internal:@altitude", elevation.getVariableToken());
            int cycles = 1000;
            for (int cycle = 1; cycle <= cycles; cycle++) {
                moved.setCurrentLocation(tile(5 + cycle, 0));
                raised.setZ(1.25 + cycle / 100.0);
                monitor.poll(room);
                assertEquals(2 + 2 * cycle, events.size(), "Lost or duplicated change at cycle " + cycle);
            }
            monitor.poll(room);
            assertEquals(2 + 2 * cycles, events.size(), "Unchanged state must not fire again");
            // A removed subscription forgets its observations; reinstalling it starts cleanly.
            triggers.clear();
            monitor.poll(room);
            moved.setCurrentLocation(tile(6, 0));
            triggers.add(trigger(100, 0, "@position_x", room));
            monitor.poll(room);
            assertEquals(2 + 2 * cycles, events.size());
        }
        verify(room, times(1004)).getRoomUnits();
        verify(room, times(1003)).getFloorItems();
        verify(room, never()).getHabbo(any(RoomUnit.class));
        verify(room, never()).getBot(any(RoomUnit.class));
        verify(room, never()).getPet(any(RoomUnit.class));
    }

    @Test
    void unitOwnedPropertiesKeepTheirValuesAndPresenceWithoutEntitySearches() {
        Room room = mock(Room.class);
        RoomUnit unit = new RoomUnit();
        unit.setId(17);
        unit.setCurrentLocation(tile(3, 4));
        unit.setZ(1.25);
        unit.setWiredOpacity(73);
        unit.setBodyRotation(RoomUserRotation.EAST);
        unit.setStatus(RoomUnitStatus.SIGN, "12");
        Map<String, Integer> values = Map.ofEntries(
                Map.entry("@index", 17),
                Map.entry("@position_x", 3),
                Map.entry("@position_y", 4),
                Map.entry("@altitude", 125),
                Map.entry("@opacity", 73),
                Map.entry("@direction", 2),
                Map.entry("@level", 0),
                Map.entry("@sign", 12),
                Map.entry("@effect_id", 0),
                Map.entry("@handitem_id", 0),
                Map.entry("@dance", 0),
                Map.entry("@is_idle", 0),
                Map.entry("@is_frozen", 0));
        values.forEach(
                (key, value) -> assertEquals(value, WiredInternalVariableSupport.readUserValue(room, unit, key), key));
        for (String key :
                Set.of("@index", "@position_x", "@position_y", "@altitude", "@opacity", "@direction", "@level")) {
            assertTrue(WiredInternalVariableSupport.hasUserValue(room, unit, key), key);
        }
        assertTrue(WiredInternalVariableSupport.hasUserValue(room, unit, "@sign"));
        for (String key : Set.of(
                "@effect_id", "@handitem_id", "@dance", "@is_idle", "@team_score", "@team_color", "@team_type")) {
            assertFalse(WiredInternalVariableSupport.hasUserValue(room, unit, key), key);
        }
        assertNull(WiredInternalVariableSupport.readUserValue(null, unit, "@position_x"));
        assertNull(WiredInternalVariableSupport.readUserValue(room, null, "@position_x"));
        assertFalse(WiredInternalVariableSupport.hasUserValue(null, unit, "@position_x"));
        verify(room, never()).getHabbo(any(RoomUnit.class));
        verify(room, never()).getBot(any(RoomUnit.class));
        verify(room, never()).getPet(any(RoomUnit.class));
    }

    @Test
    void accountAndEntityDependentPropertiesKeepTheirExistingMissingValueRules() {
        Room room = mock(Room.class, RETURNS_DEEP_STUBS);
        RoomUnit unit = new RoomUnit();
        when(room.getHabbo(unit)).thenReturn(null);
        when(room.getBot(unit)).thenReturn(null);
        when(room.getPet(unit)).thenReturn(null);
        assertTrue(WiredInternalVariableSupport.hasUserValue(room, unit, "@achievement_score"));
        assertNull(WiredInternalVariableSupport.readUserValue(room, unit, "@achievement_score"));
        assertFalse(WiredInternalVariableSupport.hasUserValue(room, unit, "@gender"));
        assertEquals(-1, WiredInternalVariableSupport.readUserValue(room, unit, "@gender"));
        assertFalse(WiredInternalVariableSupport.hasUserValue(room, unit, "@is_owner"));
        assertEquals(0, WiredInternalVariableSupport.readUserValue(room, unit, "@is_owner"));
        var habbo = mock(com.eu.habbo.habbohotel.users.Habbo.class, RETURNS_DEEP_STUBS);
        when(room.getHabbo(unit)).thenReturn(habbo);
        when(habbo.getHabboStats().getAchievementScore()).thenReturn(123);
        when(habbo.getHabboInfo().getId()).thenReturn(42);
        when(room.isOwner(habbo)).thenReturn(true);
        assertEquals(123, WiredInternalVariableSupport.readUserValue(room, unit, "@achievement_score"));
        assertEquals(42, WiredInternalVariableSupport.readUserValue(room, unit, "@user_id"));
        assertEquals(1, WiredInternalVariableSupport.readUserValue(room, unit, "@type"));
        assertTrue(WiredInternalVariableSupport.hasUserValue(room, unit, "@is_owner"));
        assertEquals(1, WiredInternalVariableSupport.readUserValue(room, unit, "@is_owner"));
    }

    private static RoomTile tile(int x, int y) {
        return new RoomTile((short) x, (short) y, (short) 0, RoomTileState.OPEN, true);
    }

    private static WiredTriggerVariableChanged trigger(int id, int target, String key, Room room) throws Exception {
        var trigger = new WiredTriggerVariableChanged(id, 1, mock(Item.class), "", 0, 0);
        ResultSet stored = mock(ResultSet.class);
        when(stored.getString("wired_data"))
                .thenReturn("{\"variableToken\":\"internal:" + key + "\",\"targetType\":" + target + "}");
        trigger.loadWiredData(stored, room);
        return trigger;
    }
}
