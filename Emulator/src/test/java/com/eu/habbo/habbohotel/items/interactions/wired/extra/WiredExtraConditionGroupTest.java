package com.eu.habbo.habbohotel.items.interactions.wired.extra;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.RETURNS_DEEP_STUBS;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

import com.eu.habbo.Emulator;
import com.eu.habbo.core.ConfigurationManager;
import com.eu.habbo.habbohotel.GameEnvironment;
import com.eu.habbo.habbohotel.items.Item;
import com.eu.habbo.habbohotel.items.ItemInteraction;
import com.eu.habbo.habbohotel.items.interactions.wired.WiredSettings;
import com.eu.habbo.habbohotel.rooms.Room;
import com.eu.habbo.habbohotel.wired.core.WiredContext;
import java.sql.ResultSet;
import org.junit.jupiter.api.Test;

class WiredExtraConditionGroupTest {
    @Test
    void nativeComparisonKeepsEnvelopeSelectionAndSurvivesReloadAndLegacyCardSave() throws Exception {
        Room room = mock(Room.class);
        when(room.getId()).thenReturn(266);
        GameEnvironment environment = mock(GameEnvironment.class, RETURNS_DEEP_STUBS);
        when(environment.getRoomManager().getRoom(266)).thenReturn(room);
        ConfigurationManager configuration = mock(ConfigurationManager.class);
        when(configuration.getInt("hotel.wired.furni.selection.count")).thenReturn(50);
        Item base = mock(Item.class);
        when(base.getInteractionType()).thenReturn(new ItemInteraction("wf_xtra_or_eval", WiredExtraOrEval.class));
        var child = new WiredExtraOrEval(10, 16, base, "", 0, 0);
        child.setRoomId(266);
        when(room.getHabboItem(10)).thenReturn(child);
        var group = new WiredExtraOrEval(20, 16, base, "", 0, 0);
        group.setRoomId(266);
        WiredSettings settings = new WiredSettings(new int[] {-1, 1, 250}, "", new int[] {10}, 0);
        settings.setNativeVariableFormat(true);
        settings.setFurniSourceTypes(new int[] {100});
        try (var emulator = mockStatic(Emulator.class)) {
            emulator.when(Emulator::getGameEnvironment).thenReturn(environment);
            emulator.when(Emulator::getConfig).thenReturn(configuration);
            assertTrue(group.saveData(settings, null));
            assertEquals(WiredExtraOrEval.MODE_EXACTLY, group.getEvaluationMode());
            assertEquals(250, group.getCompareValue());
            assertEquals(100, group.getFurniSource());
            String json = group.getWiredData();
            ResultSet row = mock(ResultSet.class);
            when(row.getString("wired_data")).thenReturn(json);
            group.loadWiredData(row, room);
            assertEquals(json, group.getWiredData());
            WiredContext context = mock(WiredContext.class);
            when(context.room()).thenReturn(room);
            assertEquals(java.util.List.of(child), group.resolveConditions(context));
            assertTrue(group.hasReferencedConditions());
            assertTrue(group.saveData(new WiredSettings(new int[] {5, 100, 250}, "", new int[] {10}, 0), null));
            assertEquals(java.util.List.of(child), group.resolveConditions(context));
            assertTrue(group.hasReferencedConditions());
        }
    }
}
