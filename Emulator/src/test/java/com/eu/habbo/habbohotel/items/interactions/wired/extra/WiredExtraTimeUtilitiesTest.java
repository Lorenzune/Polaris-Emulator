package com.eu.habbo.habbohotel.items.interactions.wired.extra;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.anyInt;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.eu.habbo.habbohotel.items.Item;
import com.eu.habbo.habbohotel.items.interactions.wired.WiredSettings;
import org.junit.jupiter.api.Test;

class WiredExtraTimeUtilitiesTest {
    @Test
    void maskAndSourceRoundTrip() throws Exception {
        var item = new WiredExtraTimeUtilities(1, 2, mock(Item.class), "", 0, 0);
        int mask = (1 << 2) | (1 << 26);
        assertTrue(item.saveData(new WiredSettings(new int[] {mask, 2}, "", new int[0], 0), null));
        var json = WiredExtraPayloadGuard.fromJson(item.getWiredData(), WiredExtraTimeUtilities.JsonData.class);
        assertEquals(mask, json.subvariableMask);
        assertEquals(2, json.sourceMode);
        var set = mock(java.sql.ResultSet.class);
        when(set.getString("wired_data")).thenReturn(item.getWiredData());
        var other = new WiredExtraTimeUtilities(1, 2, mock(Item.class), "", 0, 0);
        other.loadWiredData(set, null);
        assertEquals(item.getWiredData(), other.getWiredData());
    }

    @Test
    void legacyAndBounds() {
        var item = new WiredExtraTimeUtilities(1, 2, mock(Item.class), "", 0, 0);
        item.saveData(new WiredSettings(new int[] {3}, "", new int[0], 0), null);
        assertEquals(3, item.getTimeUnit());
        assertNull(WiredExtraPayloadGuard.fromJson(item.getWiredData(), WiredExtraTimeUtilities.JsonData.class)
                .subvariableMask);
        item.saveData(new WiredSettings(new int[] {-1, 99}, "", new int[0], 0), null);
        var json = WiredExtraPayloadGuard.fromJson(item.getWiredData(), WiredExtraTimeUtilities.JsonData.class);
        assertEquals(0x07f007fe, json.subvariableMask);
        assertEquals(2, json.sourceMode);
    }

    @Test
    void calendarAndElapsedUnitsUseUtcAndHandleBoundaries() {
        var item = new WiredExtraTimeUtilities(1, 2, mock(Item.class), "", 0, 0);
        item.saveData(new WiredSettings(new int[] {-1, 0}, "", new int[0], 0), null);
        long millis = java.time.Instant.parse("2024-02-29T23:59:58.123Z").toEpochMilli();
        int[] ids = {1, 2, 3, 4, 5, 6, 7, 8, 9, 10};
        int[] expected = {123, 58, 59, 23, 4, 29, 60, 9, 2, 2024};
        for (int n = 0; n < ids.length; n++) assertEquals(expected[n], item.deriveTime(ids[n], millis));
        assertEquals(649, item.deriveTime(26, millis));
        assertEquals(86400, item.deriveTime(21, 86400000));
        assertEquals(1440, item.deriveTime(22, 86400000));
        assertEquals(24, item.deriveTime(23, 86400000));
        assertEquals(1, item.deriveTime(24, 86400000));
        assertEquals(1, item.deriveTime(25, 604800000));
        assertEquals(999, item.deriveTime(1, -1));
        assertEquals(-1, item.deriveTime(21, -1));
        assertEquals(Integer.MAX_VALUE, item.deriveTime(20, millis));
        assertNull(item.deriveTime(11, millis));
    }

    @Test
    void timestampSourcesUseAssignmentMetadataRatherThanValue() {
        var item = new WiredExtraTimeUtilities(1, 2, mock(Item.class), "", 0, 0);
        for (int mode = 0; mode <= 2; mode++) {
            item.saveData(new WiredSettings(new int[] {1 << 21, mode}, "", new int[0], 0), null);
            assertEquals(
                    mode == 0 ? 3 : mode == 1 ? 100 : 200,
                    com.eu.habbo.habbohotel.wired.core.WiredVariableLevelSystemSupport.getDerivedValue(
                            item, 21, 3, 100, 200));
        }
        assertNull(com.eu.habbo.habbohotel.wired.core.WiredVariableLevelSystemSupport.getDerivedValue(
                item, 21, 3, 100, 0));
    }

    @Test
    void allSelectedTimeIndicesResolveWithoutAliasingAcrossTargets() {
        var room = mock(com.eu.habbo.habbohotel.rooms.Room.class);
        var types = mock(com.eu.habbo.habbohotel.rooms.RoomSpecialTypes.class);
        when(room.getRoomSpecialTypes()).thenReturn(types);
        var time = new WiredExtraTimeUtilities(1, 2, mock(Item.class), "", 0, 0);
        time.saveData(new WiredSettings(new int[] {-1, 2}, "", new int[0], 0), null);
        when(types.getExtras(anyInt(), anyInt())).thenReturn(java.util.Set.of(time));
        var bases = new com.eu.habbo.habbohotel.items.interactions.InteractionWiredExtra[] {
            mock(WiredExtraUserVariable.class), mock(WiredExtraFurniVariable.class), mock(WiredExtraRoomVariable.class)
        };
        int[] targets = {0, 1, 3};
        var ids = new java.util.HashSet<Integer>();
        for (int n = 0; n < 3; n++) {
            when(bases[n].getId()).thenReturn(123);
            when(types.getExtra(123)).thenReturn(bases[n]);
            var base =
                    new com.eu.habbo.habbohotel.rooms.WiredVariableDefinitionInfo(123, "clock", true, 0, false, false);
            var definitions = com.eu.habbo.habbohotel.wired.core.WiredVariableLevelSystemSupport.getDerivedDefinitions(
                    room, targets[n], bases[n], base);
            assertEquals(17, definitions.size());
            for (var definition : definitions) {
                assertTrue(ids.add(definition.getItemId()));
                var resolved =
                        com.eu.habbo.habbohotel.wired.core.WiredVariableLevelSystemSupport.resolveDerivedDefinition(
                                room, targets[n], definition.getItemId());
                assertNotNull(resolved);
                assertEquals(123, resolved.getBaseDefinitionItemId());
                assertTrue(time.hasSubvariable(resolved.getSubvariableType()));
            }
        }
    }

    @Test
    @SuppressWarnings("unchecked")
    void roomUserAndFurniManagersReadConfiguredTimestampSources() throws Exception {
        var room = mock(com.eu.habbo.habbohotel.rooms.Room.class);
        var types = mock(com.eu.habbo.habbohotel.rooms.RoomSpecialTypes.class);
        when(room.getRoomSpecialTypes()).thenReturn(types);
        var time = new WiredExtraTimeUtilities(1, 2, mock(Item.class), "", 0, 0);
        when(types.getExtras(anyInt(), anyInt())).thenReturn(java.util.Set.of(time));
        Class<?>[] managers = {
            com.eu.habbo.habbohotel.rooms.RoomUserVariableManager.class,
            com.eu.habbo.habbohotel.rooms.RoomFurniVariableManager.class,
            com.eu.habbo.habbohotel.rooms.RoomVariableManager.class
        };
        String[] fields = {"activeAssignmentsByUserId", "activeAssignmentsByFurniId", "activeAssignmentsByDefinitionId"
        };
        var bases = new com.eu.habbo.habbohotel.items.interactions.InteractionWiredExtra[] {
            mock(WiredExtraUserVariable.class), mock(WiredExtraFurniVariable.class), mock(WiredExtraRoomVariable.class)
        };
        int[] targets = {0, 1, 3};
        for (int n = 0; n < 3; n++) {
            when(bases[n].getId()).thenReturn(123);
            when(types.getExtra(123)).thenReturn(bases[n]);
            Object manager = managers[n]
                    .getConstructor(com.eu.habbo.habbohotel.rooms.Room.class)
                    .newInstance(room);
            if (n > 0) {
                var loaded =
                        managers[n].getDeclaredField(n == 1 ? "permanentAssignmentsLoaded" : "persistentValuesLoaded");
                loaded.setAccessible(true);
                loaded.set(manager, true);
            }
            Class<?> assignment = Class.forName(managers[n].getName() + "$VariableAssignment");
            var ctor = assignment.getDeclaredConstructor(n == 2 ? int.class : Integer.class, int.class, int.class);
            ctor.setAccessible(true);
            Object value = ctor.newInstance(3000, 100, 200);
            var field = managers[n].getDeclaredField(fields[n]);
            field.setAccessible(true);
            var map = (java.util.Map<Integer, Object>) field.get(manager);
            if (n == 2) map.put(123, value);
            else {
                var nested = new java.util.concurrent.ConcurrentHashMap<Integer, Object>();
                nested.put(123, value);
                map.put(7, nested);
            }
            for (int mode = 1; mode <= 2; mode++) {
                time.saveData(new WiredSettings(new int[] {1 << 21, mode}, "", new int[0], 0), null);
                var base = new com.eu.habbo.habbohotel.rooms.WiredVariableDefinitionInfo(
                        123, "clock", true, 0, false, false);
                int id = com.eu.habbo.habbohotel.wired.core.WiredVariableLevelSystemSupport.getDerivedDefinitions(
                                room, targets[n], bases[n], base)
                        .getFirst()
                        .getItemId();
                Object actual = n == 2
                        ? managers[n].getMethod("getCurrentValue", int.class).invoke(manager, id)
                        : managers[n]
                                .getMethod("getCurrentValue", int.class, int.class)
                                .invoke(manager, 7, id);
                assertEquals(mode == 1 ? 100 : 200, actual);
            }
        }
    }

    @Test
    void largeLegacyRoomVariableIdsRemainResolvable() {
        var room = mock(com.eu.habbo.habbohotel.rooms.Room.class);
        var types = mock(com.eu.habbo.habbohotel.rooms.RoomSpecialTypes.class);
        when(room.getRoomSpecialTypes()).thenReturn(types);
        var base = mock(WiredExtraRoomVariable.class);
        when(base.getId()).thenReturn(7000000);
        when(base.hasValue()).thenReturn(true);
        when(types.getExtra(7000000)).thenReturn(base);
        var levels = mock(WiredExtraVariableLevelUpSystem.class);
        when(levels.hasSubvariable(0)).thenReturn(true);
        when(types.getExtras(anyInt(), anyInt())).thenReturn(java.util.Set.of(levels));
        var resolved = com.eu.habbo.habbohotel.wired.core.WiredVariableLevelSystemSupport.resolveDerivedDefinition(
                room, 3, 900000000 + 7000000 * 16 + 1);
        assertNotNull(resolved);
        assertEquals(7000000, resolved.getBaseDefinitionItemId());
    }
}
