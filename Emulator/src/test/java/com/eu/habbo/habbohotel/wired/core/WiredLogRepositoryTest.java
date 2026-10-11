package com.eu.habbo.habbohotel.wired.core;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import javax.sql.DataSource;
import org.junit.jupiter.api.Test;

class WiredLogRepositoryTest {
    @Test
    void failedCommitRollsBackAndDoesNotAcknowledgeVisibleHistory() throws Exception {
        DataSource database = mock(DataSource.class);
        Connection connection = mock(Connection.class);
        PreparedStatement statement = mock(PreparedStatement.class);
        when(database.getConnection()).thenReturn(connection);
        when(connection.prepareStatement(anyString())).thenReturn(statement);
        when(statement.executeQuery()).thenReturn(mock(ResultSet.class));
        WiredRoomDiagnostics diagnostics = new WiredRoomDiagnostics(1000, 100, 1, 50, 150, 70, 5)
                .attachRepository(7, new WiredLogRepository(database));
        doThrow(new SQLException("commit failed")).when(connection).commit();
        assertThrows(
                IllegalStateException.class,
                () -> diagnostics.recordWiredLog("not committed", 42, WiredRoomDiagnostics.Severity.INFO));
        assertTrue(diagnostics
                .snapshot(0, 10, 0, System.currentTimeMillis())
                .getHistory()
                .isEmpty());
        verify(connection).rollback();
    }
}
