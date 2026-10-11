package com.eu.habbo.habbohotel.items.interactions.wired.extra;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.RETURNS_DEEP_STUBS;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.eu.habbo.Emulator;
import com.eu.habbo.database.Database;
import com.eu.habbo.habbohotel.GameEnvironment;
import com.eu.habbo.habbohotel.rooms.Room;
import com.eu.habbo.habbohotel.users.Habbo;
import com.eu.habbo.habbohotel.wired.core.WiredVariableChangeOrigin;
import java.sql.ResultSet;
import org.junit.jupiter.api.Test;

class WiredSharedVariableRuntimeTest {
    @Test
    void loadedSourceRoomRunsItsVariableManagerWithTheRemoteOrigin() throws Exception {
        var environment = mock(GameEnvironment.class, RETURNS_DEEP_STUBS);
        var database = mock(Database.class);
        var source = mock(Room.class, RETURNS_DEEP_STUBS);
        when(source.isLoaded()).thenReturn(true);
        var definition = mock(WiredExtraUserVariable.class);
        when(definition.isSharedAvailability()).thenReturn(true);
        when(definition.getVariableName()).thenReturn("score");
        when(source.getHabboItem(33)).thenReturn(definition);
        when(source.getHabbo(9)).thenReturn(mock(Habbo.class));
        when(environment.getRoomManager().getRoom(20)).thenReturn(source);
        var reference = mock(WiredExtraVariableReference.class);
        when(reference.isUserReference()).thenReturn(true);
        when(reference.hasValue()).thenReturn(true);
        when(reference.getRoomId()).thenReturn(10);
        when(reference.getSourceRoomId()).thenReturn(20);
        when(reference.getSourceVariableItemId()).thenReturn(33);
        var sourceManager = source.getUserVariableManager();
        doAnswer(invocation -> {
                    assertEquals(2, WiredVariableChangeOrigin.current());
                    return true;
                })
                .when(sourceManager)
                .updateVariableValue(9, 33, 7);
        try (var emulator = mockStatic(Emulator.class)) {
            emulator.when(Emulator::getGameEnvironment).thenReturn(environment);
            emulator.when(Emulator::getDatabase).thenReturn(database);
            assertTrue(WiredVariableReferenceSupport.updateSharedUserVariable(reference, 9, 7));
            assertEquals(1, WiredVariableChangeOrigin.current());
            verify(source.getUserVariableManager(), times(1)).updateVariableValue(9, 33, 7);
            org.mockito.Mockito.verifyNoInteractions(database);
        }
    }

    @Test
    void loadedDefinitionEditsAndRemovalImmediatelyRevokeAccessWithoutSql() {
        var environment = mock(GameEnvironment.class, RETURNS_DEEP_STUBS);
        var database = mock(Database.class);
        var source = mock(Room.class, RETURNS_DEEP_STUBS);
        when(source.isLoaded()).thenReturn(true);
        when(environment.getRoomManager().getRoom(20)).thenReturn(source);
        var definition = mock(WiredExtraRoomVariable.class);
        when(definition.getVariableName()).thenReturn("score");
        when(definition.isSharedAvailability()).thenReturn(true);
        when(source.getHabboItem(33)).thenReturn(definition);
        var reference = mock(WiredExtraVariableReference.class);
        when(reference.isRoomReference()).thenReturn(true);
        when(reference.getSourceTargetType()).thenReturn(WiredVariableReferenceSupport.TARGET_ROOM);
        when(reference.getSourceRoomId()).thenReturn(20);
        when(reference.getSourceVariableItemId()).thenReturn(33);
        when(source.getRoomVariableManager().updateVariableValue(33, 7)).thenReturn(true);
        try (var emulator = mockStatic(Emulator.class)) {
            emulator.when(Emulator::getGameEnvironment).thenReturn(environment);
            emulator.when(Emulator::getDatabase).thenReturn(database);
            assertTrue(WiredVariableReferenceSupport.updateSharedRoomVariable(reference, 7));
            when(definition.isSharedAvailability()).thenReturn(false);
            org.junit.jupiter.api.Assertions.assertFalse(
                    WiredVariableReferenceSupport.updateSharedRoomVariable(reference, 7));
            when(definition.isSharedAvailability()).thenReturn(true);
            when(definition.getVariableName()).thenReturn(" ");
            org.junit.jupiter.api.Assertions.assertFalse(
                    WiredVariableReferenceSupport.updateSharedRoomVariable(reference, 7));
            when(source.getHabboItem(33)).thenReturn(null);
            org.junit.jupiter.api.Assertions.assertFalse(
                    WiredVariableReferenceSupport.updateSharedRoomVariable(reference, 7));
            verify(source.getRoomVariableManager(), times(1)).updateVariableValue(33, 7);
            org.mockito.Mockito.verifyNoInteractions(database);
        }
    }

    @Test
    void unloadedSourceStillValidatesAgainstDatabaseBeforeDeleting() throws Exception {
        var environment = mock(GameEnvironment.class, RETURNS_DEEP_STUBS);
        var database = mock(Database.class, RETURNS_DEEP_STUBS);
        var rows = mock(ResultSet.class);
        when(database.getDataSource()
                        .getConnection()
                        .prepareStatement(anyString())
                        .executeQuery())
                .thenReturn(rows);
        when(rows.next()).thenReturn(false);
        var reference = mock(WiredExtraVariableReference.class);
        when(reference.isUserReference()).thenReturn(true);
        when(reference.getSourceRoomId()).thenReturn(20);
        when(reference.getSourceVariableItemId()).thenReturn(33);
        try (var emulator = mockStatic(Emulator.class)) {
            emulator.when(Emulator::getGameEnvironment).thenReturn(environment);
            emulator.when(Emulator::getDatabase).thenReturn(database);
            org.junit.jupiter.api.Assertions.assertFalse(
                    WiredVariableReferenceSupport.removeSharedUserVariable(reference, 9));
            verify(rows).next();
        }
    }
}
