package com.eu.habbo.habbohotel.items.editor;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.List;
import javax.sql.DataSource;
import org.junit.jupiter.api.Test;

class FurniEditorRepositoryTest {

    @Test
    void spriteLookupUsesTheProvidedDataSource() throws Exception {
        DataSource dataSource = mock(DataSource.class);
        Connection connection = mock(Connection.class);
        PreparedStatement statement = mock(PreparedStatement.class);
        ResultSet resultSet = mock(ResultSet.class);
        when(dataSource.getConnection()).thenReturn(connection);
        when(connection.prepareStatement(anyString())).thenReturn(statement);
        when(statement.executeQuery()).thenReturn(resultSet);
        when(resultSet.next()).thenReturn(true);
        when(resultSet.getInt("id")).thenReturn(42);

        int itemId =
                new FurniEditorRepository(dataSource).findItemIdBySprite(91).orElseThrow();

        assertEquals(42, itemId);
        verify(statement).setInt(1, 91);
        verify(connection).close();
    }

    @Test
    void spriteUpdateIsRestrictedToTheReportedDatabaseRow() throws Exception {
        DataSource dataSource = mock(DataSource.class);
        Connection connection = mock(Connection.class);
        PreparedStatement statement = mock(PreparedStatement.class);
        when(dataSource.getConnection()).thenReturn(connection);
        when(connection.prepareStatement("UPDATE items_base SET width = ? WHERE id = ? AND sprite_id = ? AND LOWER(item_name) = LOWER(?)"))
                .thenReturn(statement);
        when(statement.executeUpdate()).thenReturn(1);

        int updated = new FurniEditorRepository(dataSource)
                .updateItemByIdAndSprite(42, 91, "chair_rare", "width = ?", java.util.List.of(2));

        assertEquals(1, updated);
        verify(statement).setInt(1, 2);
        verify(statement).setInt(2, 42);
        verify(statement).setInt(3, 91);
        verify(statement).setString(4, "chair_rare");
        verify(connection).close();
    }

    @Test
    void spriteUpdateRejectsMissingClassnameWithoutOpeningAConnection() throws Exception {
        DataSource dataSource = mock(DataSource.class);

        int updated = new FurniEditorRepository(dataSource)
                .updateItemByIdAndSprite(42, 91, " ", "width = ?", java.util.List.of(2));

        assertEquals(0, updated);
        verify(dataSource, never()).getConnection();
    }

    @Test
    void localizedFurnidataSearchStaysInsideSearchAndTypeFilters() throws Exception {
        DataSource dataSource = mock(DataSource.class);
        Connection connection = mock(Connection.class);
        PreparedStatement count = mock(PreparedStatement.class);
        PreparedStatement page = mock(PreparedStatement.class);
        ResultSet countResult = mock(ResultSet.class);
        ResultSet pageResult = mock(ResultSet.class);
        when(dataSource.getConnection()).thenReturn(connection);
        when(connection.prepareStatement(anyString())).thenReturn(count, page);
        when(count.executeQuery()).thenReturn(countResult);
        when(page.executeQuery()).thenReturn(pageResult);
        when(countResult.next()).thenReturn(true);
        when(countResult.getInt(1)).thenReturn(1);
        when(pageResult.next()).thenReturn(false);

        var result = new FurniEditorRepository(dataSource).search(new FurniEditorRepository.SearchRequest(
                "rare", "s", "", "", 20, 0, List.of("chair_rare")));

        verify(connection).prepareStatement(
                "SELECT COUNT(*) FROM items_base WHERE 1=1 AND (item_name LIKE ? OR public_name LIKE ? OR LOWER(item_name) IN (?)) AND type = ?");
        verify(count).setString(1, "%rare%");
        verify(count).setString(2, "%rare%");
        verify(count).setString(3, "chair_rare");
        verify(count).setString(4, "s");
        assertEquals(1, result.total());
        assertEquals(List.of(), result.items());
    }

    @Test
    void deletionStopsBeforeMutationWhenPlacedItemsExist() throws Exception {
        DataSource dataSource = mock(DataSource.class);
        Connection connection = mock(Connection.class);
        PreparedStatement exists = mock(PreparedStatement.class);
        PreparedStatement count = mock(PreparedStatement.class);
        ResultSet existsResult = mock(ResultSet.class);
        ResultSet countResult = mock(ResultSet.class);
        when(dataSource.getConnection()).thenReturn(connection);
        when(connection.prepareStatement("SELECT id FROM items_base WHERE id = ?"))
                .thenReturn(exists);
        when(connection.prepareStatement("SELECT COUNT(*) FROM items WHERE item_id = ?"))
                .thenReturn(count);
        when(exists.executeQuery()).thenReturn(existsResult);
        when(count.executeQuery()).thenReturn(countResult);
        when(existsResult.next()).thenReturn(true);
        when(countResult.next()).thenReturn(true);
        when(countResult.getInt(1)).thenReturn(3);

        var result = new FurniEditorRepository(dataSource).deleteItem(12);

        assertAll(
                () -> assertEquals(FurniEditorRepository.DeleteStatus.IN_USE, result.status()),
                () -> assertEquals(3, result.referenceCount()));
        verify(connection, never()).prepareStatement("DELETE FROM items_base WHERE id = ?");
    }
}
