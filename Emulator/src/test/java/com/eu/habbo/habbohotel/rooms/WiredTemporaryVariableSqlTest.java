package com.eu.habbo.habbohotel.rooms;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.eu.habbo.habbohotel.items.interactions.wired.extra.*;
import com.eu.habbo.habbohotel.users.*;
import com.eu.habbo.habbohotel.wired.core.*;
import java.sql.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class WiredTemporaryVariableSqlTest {
    @Test
    void obsoleteFurniRowsAreStillDeletedOnceOnRestore() throws Exception {
        Room room = mock(Room.class, RETURNS_DEEP_STUBS);
        when(room.getId()).thenReturn(44);
        WiredExtraFurniVariable definition = mock(WiredExtraFurniVariable.class);
        when(room.getRoomSpecialTypes().getExtra(9)).thenReturn(definition);
        when(room.getHabboItem(7)).thenReturn(mock(HabboItem.class));
        Connection connection = mock(Connection.class);
        PreparedStatement statement = mock(PreparedStatement.class);
        ResultSet rows = mock(ResultSet.class);
        when(connection.prepareStatement(anyString())).thenReturn(statement);
        when(statement.executeQuery()).thenReturn(rows);
        when(rows.next()).thenReturn(true, false);
        when(rows.getInt("furni_id")).thenReturn(7);
        when(rows.getInt("variable_item_id")).thenReturn(9);
        RoomDependencies.ConnectionProvider database = mock(RoomDependencies.ConnectionProvider.class);
        when(database.openConnection()).thenReturn(connection);
        RoomFurniVariableManager manager = new RoomFurniVariableManager(room, database);
        manager.ensurePermanentAssignmentsLoaded();
        verify(connection).prepareStatement("DELETE FROM room_furni_wired_variables WHERE room_id = ? AND furni_id = ? AND variable_item_id = ?");
        verify(statement).executeUpdate();
        clearInvocations(database);
        manager.ensurePermanentAssignmentsLoaded();
        verifyNoInteractions(database);
    }

    @Test
    void obsoleteUserRowsAreStillDeletedOnRestoreAndDefinitionUpdate() throws Exception {
        Room room = mock(Room.class, RETURNS_DEEP_STUBS);
        when(room.getId()).thenReturn(44);
        WiredExtraUserVariable definition = mock(WiredExtraUserVariable.class);
        when(definition.getId()).thenReturn(9);
        when(room.getRoomSpecialTypes().getExtra(9)).thenReturn(definition);
        Habbo habbo = mock(Habbo.class, RETURNS_DEEP_STUBS);
        when(habbo.getHabboInfo().getId()).thenReturn(7);
        RoomUserVariableRepository repository = mock(RoomUserVariableRepository.class);
        when(repository.findByUser(44, 7)).thenReturn(java.util.List.of(new RoomUserVariableRepository.StoredAssignment(9, 8, 101, 102)));
        RoomUserVariableManager manager = spy(new RoomUserVariableManager(room, repository, () -> 101));
        doNothing().when(manager).broadcastSnapshot();
        manager.restorePermanentAssignments(habbo);
        verify(repository).delete(44, 7, 9);
        manager.handleDefinitionUpdated(definition);
        verify(repository).deleteDefinition(44, 9);
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void furniAssignmentAndRemovalKeepEventsButOnlyPermanentValuesWriteSql(boolean permanent) throws Exception {
        Room room = mock(Room.class, RETURNS_DEEP_STUBS);
        when(room.getId()).thenReturn(44);
        WiredExtraFurniVariable definition = mock(WiredExtraFurniVariable.class);
        when(definition.getId()).thenReturn(9);
        when(definition.getVariableName()).thenReturn("score");
        when(definition.hasValue()).thenReturn(true);
        when(definition.isPermanentAvailability()).thenReturn(permanent);
        when(room.getRoomSpecialTypes().getExtra(9)).thenReturn(definition);
        HabboItem furni = mock(HabboItem.class);
        when(furni.getId()).thenReturn(7);
        Connection connection = mock(Connection.class);
        PreparedStatement statement = mock(PreparedStatement.class);
        ResultSet rows = mock(ResultSet.class);
        when(connection.prepareStatement(anyString())).thenReturn(statement);
        when(statement.executeQuery()).thenReturn(rows);
        RoomDependencies.ConnectionProvider database = mock(RoomDependencies.ConnectionProvider.class);
        when(database.openConnection()).thenReturn(connection);
        RoomFurniVariableManager manager = spy(new RoomFurniVariableManager(room, database));
        doNothing().when(manager).broadcastSnapshot();
        manager.ensurePermanentAssignmentsLoaded();
        clearInvocations(database, statement);
        try (var wired = mockStatic(WiredManager.class)) {
            assertTrue(manager.assignVariable(furni, 9, 7, false));
            assertTrue(manager.assignVariable(furni, 9, 7, true));
            assertTrue(manager.updateVariableValue(7, 9, 8));
            assertFalse(manager.updateVariableValue(7, 9, 8));
            assertEquals(8, manager.getCurrentValue(7, 9));
            assertTrue(manager.removeVariable(7, 9));
            assertFalse(manager.hasVariable(7, 9));
            wired.verify(() -> WiredManager.triggerFurniVariableChanged(room, 7, 9, true, false, WiredEvent.VariableChangeKind.NONE));
            wired.verify(() -> WiredManager.triggerFurniVariableChanged(room, 7, 9, false, false, WiredEvent.VariableChangeKind.UNCHANGED), times(2));
            wired.verify(() -> WiredManager.triggerFurniVariableChanged(room, 7, 9, false, false, WiredEvent.VariableChangeKind.INCREASED));
            wired.verify(() -> WiredManager.triggerFurniVariableChanged(room, 7, 9, false, true, WiredEvent.VariableChangeKind.NONE));
        }
        if (permanent) {
            verify(database, times(4)).openConnection();
            verify(statement, times(4)).executeUpdate();
        } else {
            verifyNoInteractions(database, statement);
        }
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void userAssignmentAndRemovalKeepEventsButOnlyPermanentValuesWriteSql(boolean permanent) throws Exception {
        Room room = mock(Room.class, RETURNS_DEEP_STUBS);
        when(room.getId()).thenReturn(44);
        WiredExtraUserVariable definition = mock(WiredExtraUserVariable.class);
        when(definition.getId()).thenReturn(9);
        when(definition.getVariableName()).thenReturn("score");
        when(definition.hasValue()).thenReturn(true);
        when(definition.isPermanentAvailability()).thenReturn(permanent);
        when(room.getRoomSpecialTypes().getExtra(9)).thenReturn(definition);
        Habbo habbo = mock(Habbo.class, RETURNS_DEEP_STUBS);
        when(habbo.getHabboInfo().getId()).thenReturn(7);
        RoomUserVariableRepository repository = mock(RoomUserVariableRepository.class);
        RoomUserVariableManager manager = spy(new RoomUserVariableManager(room, repository, () -> 101));
        doNothing().when(manager).broadcastSnapshot();
        try (var wired = mockStatic(WiredManager.class)) {
            assertTrue(manager.assignVariable(habbo, 9, 7, false));
            assertTrue(manager.assignVariable(habbo, 9, 7, true));
            assertTrue(manager.updateVariableValue(7, 9, 8));
            assertFalse(manager.updateVariableValue(7, 9, 8));
            assertEquals(8, manager.getCurrentValue(7, 9));
            assertTrue(manager.removeVariable(7, 9));
            assertFalse(manager.hasVariable(7, 9));
            wired.verify(() -> WiredManager.triggerUserVariableChanged(room, 7, 9, true, false, WiredEvent.VariableChangeKind.NONE));
            wired.verify(() -> WiredManager.triggerUserVariableChanged(room, 7, 9, false, false, WiredEvent.VariableChangeKind.UNCHANGED), times(2));
            wired.verify(() -> WiredManager.triggerUserVariableChanged(room, 7, 9, false, false, WiredEvent.VariableChangeKind.INCREASED));
            wired.verify(() -> WiredManager.triggerUserVariableChanged(room, 7, 9, false, true, WiredEvent.VariableChangeKind.NONE));
        }
        if (permanent) {
            verify(repository, times(2)).upsert(44, 7, 9, 7, 101, 101);
            verify(repository).upsert(44, 7, 9, 8, 101, 102);
            verify(repository).delete(44, 7, 9);
            verifyNoMoreInteractions(repository);
        } else {
            verifyNoInteractions(repository);
        }
    }
}
