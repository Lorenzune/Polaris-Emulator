package com.eu.habbo.habbohotel.items.interactions.wired.triggers;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.eu.habbo.habbohotel.items.Item;
import com.eu.habbo.habbohotel.rooms.Room;
import com.eu.habbo.habbohotel.wired.core.WiredEvent;
import java.sql.ResultSet;
import org.junit.jupiter.api.Test;

class WiredTriggerVariableChangedTest {
    @Test
    void persistedOriginMaskFiltersInspectionAndRemoteChangesIndependently() throws Exception {
        var trigger = new WiredTriggerVariableChanged(1, 2, mock(Item.class), "", 0, 0);
        var room = mock(Room.class);
        var stored = mock(ResultSet.class);
        when(stored.getString("wired_data")).thenReturn("""
            {"variableToken":"custom:33","targetType":0,"valueChangedEnabled":true,
             "increasedEnabled":true,"originMask":6}
            """);
        trigger.loadWiredData(stored, room);
        for (int origin : new int[] {1, 2, 4, 8}) {
            var event = WiredEvent.builder(WiredEvent.Type.VARIABLE_CHANGED, room)
                    .variableTargetType(0)
                    .variableDefinitionItemId(33)
                    .variableChangeKind(WiredEvent.VariableChangeKind.INCREASED)
                    .variableChangeOrigin(origin)
                    .build();
            if (origin == 2 || origin == 4) assertTrue(trigger.matches(trigger, event));
            else assertFalse(trigger.matches(trigger, event));
        }
    }

    @Test
    void disabledCreationAndDeletionCannotFallThroughToValueChange() throws Exception {
        var trigger = new WiredTriggerVariableChanged(1, 2, mock(Item.class), "", 0, 0);
        var room = mock(Room.class);
        var stored = mock(ResultSet.class);
        when(stored.getString("wired_data")).thenReturn("""
            {"variableToken":"custom:33","variableItemId":33,"targetType":0,
             "createdEnabled":false,"deletedEnabled":false,"valueChangedEnabled":true,
             "increasedEnabled":true,"decreasedEnabled":true,"unchangedEnabled":false}
            """);
        trigger.loadWiredData(stored, room);
        assertFalse(trigger.matches(trigger, event(room, true, false, WiredEvent.VariableChangeKind.INCREASED)));
        assertFalse(trigger.matches(trigger, event(room, false, true, WiredEvent.VariableChangeKind.DECREASED)));
        assertTrue(trigger.matches(trigger, event(room, false, false, WiredEvent.VariableChangeKind.INCREASED)));
        assertTrue(trigger.matches(trigger, event(room, false, false, WiredEvent.VariableChangeKind.DECREASED)));
        assertFalse(trigger.matches(trigger, event(room, false, false, WiredEvent.VariableChangeKind.UNCHANGED)));
    }

    private static WiredEvent event(Room room, boolean created, boolean deleted, WiredEvent.VariableChangeKind kind) {
        return WiredEvent.builder(WiredEvent.Type.VARIABLE_CHANGED, room)
                .variableTargetType(0)
                .variableDefinitionItemId(33)
                .variableCreated(created)
                .variableDeleted(deleted)
                .variableChangeKind(kind)
                .build();
    }
}
