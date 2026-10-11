package com.eu.habbo.habbohotel.items.interactions.wired.conditions;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.RETURNS_DEEP_STUBS;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

import com.eu.habbo.Emulator;
import com.eu.habbo.habbohotel.GameEnvironment;
import com.eu.habbo.habbohotel.items.Item;
import com.eu.habbo.habbohotel.items.interactions.wired.WiredSettings;
import com.eu.habbo.habbohotel.rooms.Room;
import com.eu.habbo.habbohotel.rooms.RoomUnit;
import com.eu.habbo.habbohotel.users.HabboItem;
import com.eu.habbo.habbohotel.wired.core.WiredContext;
import com.eu.habbo.habbohotel.wired.core.WiredEvent;
import com.eu.habbo.habbohotel.wired.core.WiredServices;
import com.eu.habbo.habbohotel.wired.core.WiredState;
import java.sql.ResultSet;
import java.util.Set;
import org.junit.jupiter.api.Test;

class WiredSelectionQuantityNativeTest {
    private WiredSettings settings(int users, int amount, int comparison) {
        var settings = new WiredSettings(new int[] {users, amount, comparison}, "", new int[0], 0);
        settings.setNativeVariableFormat(true);
        settings.setFurniSourceTypes(new int[] {200});
        settings.setUserSourceTypes(new int[] {200});
        return settings;
    }

    @Test
    void importedFurniQuantityUsesComparisonAndGroupInSwfOrderAfterReload() throws Exception {
        var room = mock(Room.class);
        var environment = mock(GameEnvironment.class, RETURNS_DEEP_STUBS);
        when(environment.getRoomManager().getRoom(266)).thenReturn(room);
        try (var emulator = mockStatic(Emulator.class)) {
            emulator.when(Emulator::getGameEnvironment).thenReturn(environment);
            var condition = new WiredConditionSelectionQuantity(1, 16, mock(Item.class), "", 0, 0);
            condition.setRoomId(266);
            assertTrue(condition.saveData(settings(0, 3, 1)));
            var stored = mock(ResultSet.class);
            String payload = condition.getWiredData();
            when(stored.getString("wired_data")).thenReturn(payload);
            var reloaded = new WiredConditionSelectionQuantity(2, 16, mock(Item.class), "", 0, 0);
            reloaded.loadWiredData(stored, room);
            var context = new WiredContext(
                    WiredEvent.builder(WiredEvent.Type.SIGNAL_RECEIVED, room).build(),
                    null,
                    mock(WiredServices.class),
                    new WiredState(100));
            context.targets().setItems(Set.of(mock(HabboItem.class), mock(HabboItem.class), mock(HabboItem.class)));
            assertTrue(reloaded.evaluate(context));
            context.targets().setItems(Set.of());
            assertFalse(reloaded.evaluate(context));
            assertTrue(reloaded.saveData(settings(0, 0, 1)));
            assertTrue(reloaded.evaluate(context));
        }
    }

    @Test
    void userFlagChoosesUsersAndLegacyFourFieldsRemainCompatible() {
        var room = mock(Room.class);
        var condition = new WiredConditionSelectionQuantity(1, 16, mock(Item.class), "", 0, 0);
        var context = new WiredContext(
                WiredEvent.builder(WiredEvent.Type.USER_CLICKS_FURNI, room).build(),
                null,
                mock(WiredServices.class),
                new WiredState(100));
        var user = mock(RoomUnit.class);
        when(user.getId()).thenReturn(7);
        context.targets().setUsers(Set.of(user));
        assertTrue(condition.saveData(settings(1, 1, 1)));
        assertTrue(condition.evaluate(context));
        assertTrue(condition.saveData(new WiredSettings(new int[] {1, 1, 0, 200}, "", new int[0], 0)));
        assertTrue(condition.evaluate(context));
        assertFalse(condition.saveData(settings(2, 1, 1)));
    }
}
