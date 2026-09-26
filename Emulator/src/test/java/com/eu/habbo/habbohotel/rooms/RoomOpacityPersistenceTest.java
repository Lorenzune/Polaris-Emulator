package com.eu.habbo.habbohotel.rooms;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.startsWith;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.Map;
import java.util.List;
import javax.sql.DataSource;
import org.junit.jupiter.api.Test;

class RoomOpacityPersistenceTest {

    @Test
    void loadsPersistedVisualStateForRoom() throws Exception {
        DataSource dataSource = mock(DataSource.class);
        Connection connection = mock(Connection.class);
        PreparedStatement query = mock(PreparedStatement.class);
        ResultSet resultSet = mock(ResultSet.class);
        when(dataSource.getConnection()).thenReturn(connection);
        when(connection.prepareStatement(startsWith("SELECT id AS furni_id, wired_opacity AS opacity"))).thenReturn(query);
        when(query.executeQuery()).thenReturn(resultSet);
        when(resultSet.next()).thenReturn(true, false);
        when(resultSet.getInt("furni_id")).thenReturn(6103);
        when(resultSet.getInt("opacity")).thenReturn(42);
        when(resultSet.getInt("scaling")).thenReturn(185);
        when(resultSet.getString("custom_color")).thenReturn("{\"v\":1,\"layers\":{\"0\":{\"mode\":\"solid\",\"color\":\"#aaaaaa\"}}}");

        RoomOpacityPersistence persistence = new RoomOpacityPersistence(61, dataSource::getConnection, Runnable::run);

        assertEquals(Map.of(6103, new RoomOpacityPersistence.PersistedState(42, 200,
                "{\"v\":1,\"layers\":{\"0\":{\"mode\":\"solid\",\"color\":\"#aaaaaa\"}}}")), persistence.load());
        verify(query).setInt(1, 61);
    }

    @Test
    void writesOpacityAndScalingToItemsInBatchesIncludingDefaults() throws Exception {
        DataSource dataSource = mock(DataSource.class);
        Connection connection = mock(Connection.class);
        PreparedStatement update = mock(PreparedStatement.class);
        when(dataSource.getConnection()).thenReturn(connection);
        when(connection.prepareStatement(startsWith("UPDATE items SET wired_opacity"))).thenReturn(update);

        RoomOpacityPersistence persistence = new RoomOpacityPersistence(61, dataSource::getConnection, Runnable::run);
        String customColor = "{\"v\":1,\"layers\":{\"0\":{\"mode\":\"solid\",\"color\":\"#abcdef\"}}}";
        persistence.save(List.of(
                new WiredOpacityState(6101, false, 37, true, 45, customColor),
                new WiredOpacityState(6102, false, 100, false)));

        verify(update, timeout(2_000)).executeBatch();
        verify(update).setInt(1, 37);
        verify(update).setInt(2, 50);
        verify(update).setString(3, customColor);
        verify(update).setInt(4, 6101);
        verify(update).setString(3, null);
        verify(update).setInt(4, 6102);
        verify(update).setInt(1, 100);
        verify(update).setInt(2, 100);
    }
}
