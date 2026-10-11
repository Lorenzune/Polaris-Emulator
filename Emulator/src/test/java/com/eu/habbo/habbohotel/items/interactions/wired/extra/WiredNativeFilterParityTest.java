package com.eu.habbo.habbohotel.items.interactions.wired.extra;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.RETURNS_DEEP_STUBS;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

import com.eu.habbo.Emulator;
import com.eu.habbo.habbohotel.GameEnvironment;
import com.eu.habbo.habbohotel.items.Item;
import com.eu.habbo.habbohotel.items.interactions.wired.WiredNativeVariableSettings;
import com.eu.habbo.habbohotel.items.interactions.wired.WiredSettings;
import com.eu.habbo.habbohotel.rooms.Room;
import com.eu.habbo.habbohotel.rooms.WiredVariableDefinitionInfo;
import com.eu.habbo.habbohotel.users.HabboItem;
import com.eu.habbo.habbohotel.wired.core.WiredContext;
import com.eu.habbo.habbohotel.wired.core.WiredEvent;
import com.eu.habbo.habbohotel.wired.core.WiredServices;
import com.eu.habbo.habbohotel.wired.core.WiredState;
import com.eu.habbo.messages.incoming.wired.WiredSaveException;
import java.sql.ResultSet;
import java.util.List;
import org.junit.jupiter.api.Test;

class WiredNativeFilterParityTest {
    static WiredSettings settings(int[] ints, String... tokens) {
        var settings = new WiredSettings(ints, "", new int[0], 0);
        settings.setNativeVariableFormat(true);
        settings.setVariableTokens(tokens);
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
    void nativeFieldsAndSelectedReferenceSourceAreTranslatedWithoutAmbiguity() {
        var item = new WiredExtraFilterFurniByVariable(1, 2, mock(Item.class), "", 0, 0);
        var settings = settings(new int[] {3, 4, 1, 0}, "custom:8", "custom:9");
        settings.setFurniSourceTypes(new int[] {100});
        assertArrayEquals(
                new int[] {4, 1, 3, 1, 0, 101},
                WiredNativeVariableSettings.normalize(item, settings).getIntParams());
        assertEquals(
                "custom:8\tcustom:9",
                WiredNativeVariableSettings.normalize(item, settings).getStringParam());
        assertThrows(
                IllegalArgumentException.class,
                () -> WiredNativeVariableSettings.normalize(item, settings(new int[] {0, 0, 0, 1}, "custom:8", "n")));
    }

    @Test
    void legacyCountJsonAndNumericAmountsRemainReadable() throws Exception {
        var extra = new WiredExtraFilterFurni(1, 2, mock(Item.class), "", 0, 0);
        var rows = mock(ResultSet.class);
        for (String data : List.of("{\"amount\":7}", "7")) {
            when(rows.getString("wired_data")).thenReturn(data);
            extra.loadWiredData(rows, null);
            assertEquals(7, extra.getAmount());
            assertEquals(7, extra.resolveAmount(null, null));
        }
        extra.saveData(new WiredSettings(new int[] {0}, "", new int[0], 0), null);
        assertEquals(0, extra.resolveAmount(null, null));
    }

    @Test
    void dynamicRoomQuantitySurvivesReloadAndMissingOrNegativeValuesSelectNothing() throws Exception {
        var environment = mock(GameEnvironment.class, RETURNS_DEEP_STUBS);
        var room = mock(Room.class, RETURNS_DEEP_STUBS);
        when(environment.getRoomManager().getRoom(10)).thenReturn(room);
        when(room.getRoomVariableManager().getDefinitionInfo(9))
                .thenReturn(new WiredVariableDefinitionInfo(9, "limit", true, 0, false, false));
        try (var emulator = mockStatic(Emulator.class)) {
            emulator.when(Emulator::getGameEnvironment).thenReturn(environment);
            var extra = new WiredExtraFilterUser(1, 2, mock(Item.class), "", 0, 0);
            extra.setRoomId(10);
            extra.saveData(settings(new int[] {1, 1, -10}, "custom:9"), null);
            var rows = mock(ResultSet.class);
            String saved = extra.getWiredData();
            when(rows.getString("wired_data")).thenReturn(saved);
            var loaded = new WiredExtraFilterUser(2, 2, mock(Item.class), "", 0, 0);
            loaded.setRoomId(10);
            loaded.loadWiredData(rows, room);
            when(room.getRoomVariableManager().getCurrentValue(9)).thenReturn(2, 15000, -1);
            assertEquals(2, loaded.resolveAmount(context(room), room));
            assertEquals(15000, loaded.resolveAmount(context(room), room));
            assertEquals(0, loaded.resolveAmount(context(room), room));
            when(room.getRoomVariableManager().getDefinitionInfo(9)).thenReturn(null);
            assertEquals(0, loaded.resolveAmount(context(room), room));
        }
    }

    @Test
    void unsupportedSortCapabilitiesCannotBeSavedAndCreationOnlyVariablesCanBeSorted() throws Exception {
        var environment = mock(GameEnvironment.class, RETURNS_DEEP_STUBS);
        var room = mock(Room.class, RETURNS_DEEP_STUBS);
        when(environment.getRoomManager().getRoom(10)).thenReturn(room);
        when(room.getFurniVariableManager().getDefinitionInfo(8))
                .thenReturn(new WiredVariableDefinitionInfo(8, "exists", false, 0, false, false)
                        .withCapabilities(true, true, false));
        var a = mock(HabboItem.class);
        var b = mock(HabboItem.class);
        when(a.getId()).thenReturn(11);
        when(b.getId()).thenReturn(12);
        when(room.getFurniVariableManager().hasVariable(11, 8)).thenReturn(true);
        when(room.getFurniVariableManager().hasVariable(12, 8)).thenReturn(true);
        when(room.getFurniVariableManager().getCreatedAt(11, 8)).thenReturn(10);
        when(room.getFurniVariableManager().getCreatedAt(12, 8)).thenReturn(20);
        try (var emulator = mockStatic(Emulator.class)) {
            emulator.when(Emulator::getGameEnvironment).thenReturn(environment);
            var extra = new WiredExtraFilterFurniByVariable(1, 2, mock(Item.class), "", 0, 0);
            extra.setRoomId(10);
            assertThrows(
                    WiredSaveException.class,
                    () -> extra.saveData(settings(new int[] {1, 0, 0, 1}, "custom:8", "n"), null));
            assertThrows(
                    WiredSaveException.class,
                    () -> extra.saveData(settings(new int[] {1, 4, 0, 1}, "custom:8", "n"), null));
            extra.saveData(settings(new int[] {1, 3, 0, 1}, "custom:8", "n"), null);
            assertEquals(List.of(b), extra.filterItems(room, context(room), List.of(a, b)));
            assertEquals(List.of(), extra.filterItems(room, context(room), List.of()));
        }
    }
}
