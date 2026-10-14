package com.eu.habbo.habbohotel.items;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.RETURNS_DEEP_STUBS;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.eu.habbo.Emulator;
import com.eu.habbo.database.Database;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import org.junit.jupiter.api.Test;

class ItemDefinitionRefreshTest {
    @Test
    void singleDefinitionRefreshPreservesPlacedItemReferencesAndLeavesOtherDefinitionsAlone() throws Exception {
        Database database = mock(Database.class, RETURNS_DEEP_STUBS);
        Connection connection = mock(Connection.class);
        PreparedStatement query = mock(PreparedStatement.class);
        ResultSet row = mock(ResultSet.class);
        when(database.getDataSource().getConnection()).thenReturn(connection);
        when(connection.prepareStatement(anyString())).thenReturn(query);
        when(query.executeQuery()).thenReturn(row);
        when(row.next()).thenReturn(true, false);
        Item existing = mock(Item.class);
        Item unrelated = mock(Item.class);
        try (var emulator = mockStatic(Emulator.class)) {
            emulator.when(Emulator::getDatabase).thenReturn(database);
            ItemManager manager = new ItemManager();
            manager.getItems().put(7, existing);
            manager.getItems().put(8, unrelated);

            manager.reloadItem(7);

            assertSame(existing, manager.getItem(7));
            assertSame(unrelated, manager.getItem(8));
            verify(existing).update(row);
            verify(unrelated, never()).update(row);
            verify(query).setInt(1, 7);
            verify(connection, never()).createStatement();

            manager.reloadItem(7);
            assertFalse(manager.getItems().containsKey(7));
            assertSame(unrelated, manager.getItem(8));
        }
    }
}
