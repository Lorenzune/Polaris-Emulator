package com.eu.habbo.habbohotel.items.interactions.wired.extra;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
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
import com.eu.habbo.habbohotel.wired.core.WiredContext;
import com.eu.habbo.habbohotel.wired.core.WiredContextVariableScope;
import com.eu.habbo.habbohotel.wired.core.WiredContextVariableSupport;
import com.eu.habbo.messages.incoming.wired.WiredSaveException;
import java.sql.ResultSet;
import org.junit.jupiter.api.Test;

class WiredExtraVariableEchoParityTest {
    @Test
    void contextAliasReadsAndWritesTheOriginalValue() throws Exception {
        var room = mock(Room.class, RETURNS_DEEP_STUBS);
        var source = mock(WiredExtraContextVariable.class);
        when(source.getId()).thenReturn(33);
        when(source.getVariableName()).thenReturn("score.value");
        when(source.hasValue()).thenReturn(true);
        when(room.getRoomSpecialTypes().getExtra(33)).thenReturn(source);
        var echo = new WiredExtraVariableEcho(44, 2, mock(Item.class), "", 0, 0);
        var stored = mock(ResultSet.class);
        when(stored.getString("wired_data")).thenReturn("""
            {"variableName":"alias","sourceTargetType":2,"sourceVariableToken":"custom:33","sourceVariableItemId":33}
            """);
        echo.loadWiredData(stored, room);
        when(room.getRoomSpecialTypes().getExtra(44)).thenReturn(echo);
        var ctx = mock(WiredContext.class);
        var scope = new WiredContextVariableScope();
        when(ctx.room()).thenReturn(room);
        when(ctx.contextVariables()).thenReturn(scope);
        assertTrue(scope.assignValue(33, 7, false));
        assertEquals(7, WiredContextVariableSupport.getCurrentValue(ctx, 44));
        assertTrue(WiredContextVariableSupport.updateVariableValue(ctx, room, 44, 9));
        assertEquals(9, scope.getValue(33));
        assertFalse(echo.createDefinitionInfo(room).canCreateAndDelete());
    }

    @Test
    void circularReferenceIsRejectedBeforeResolvingDefinitions() {
        var room = mock(Room.class, RETURNS_DEEP_STUBS);
        var environment = mock(GameEnvironment.class, RETURNS_DEEP_STUBS);
        when(environment.getRoomManager().getRoom(0)).thenReturn(room);
        var echo = new WiredExtraVariableEcho(44, 2, mock(Item.class), "", 0, 0);
        var settings = new WiredSettings(new int[0], """
            {"variableName":"alias","sourceTargetType":2,"sourceVariableToken":"custom:44"}
            """, new int[0], 0);
        try (var emulator = mockStatic(Emulator.class)) {
            emulator.when(Emulator::getGameEnvironment).thenReturn(environment);
            assertThrows(WiredSaveException.class, () -> echo.saveData(settings, null));
        }
    }
}
