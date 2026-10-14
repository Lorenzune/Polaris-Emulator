package com.eu.habbo.habbohotel.users.levels;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.sql.CallableStatement;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import org.junit.jupiter.api.Test;

class AvatarLevelReconcilerTest {
    @Test
    void freshProjectionDeliveryDoesNotReadSourcesOrRewriteCounters() throws Exception {
        Connection connection = mock(Connection.class);
        CallableStatement lock = mock(CallableStatement.class);
        PreparedStatement due = mock(PreparedStatement.class);
        ResultSet row = mock(ResultSet.class);
        when(connection.prepareCall(anyString())).thenReturn(lock);
        when(connection.prepareStatement(anyString())).thenReturn(due);
        when(due.executeQuery()).thenReturn(row);
        when(row.next()).thenReturn(true);
        when(row.getBoolean(1)).thenReturn(false);

        AvatarLevelReconciler.reconcile(connection, 7, false);

        var order = inOrder(lock, due);
        order.verify(lock).execute();
        order.verify(due).executeQuery();
        verify(connection, never()).prepareStatement("DELETE FROM users_avatar_badge_counts WHERE user_id = ?");
        verify(connection, never()).prepareStatement("DELETE FROM users_avatar_furni_counts WHERE user_id = ?");
        verify(connection, never()).prepareStatement("DELETE FROM users_avatar_achievement_progress WHERE user_id = ?");
        verify(due, never()).executeUpdate();
        verify(due, never()).executeBatch();
        verify(connection, org.mockito.Mockito.times(1)).prepareStatement(anyString());
    }

    @Test
    void expiredProjectionAndExplicitRepairStillRebuildFromSources() throws Exception {
        for (boolean force : new boolean[] {false, true}) {
            Connection connection = mock(Connection.class);
            CallableStatement lock = mock(CallableStatement.class);
            PreparedStatement statement = mock(PreparedStatement.class);
            ResultSet rows = mock(ResultSet.class);
            when(connection.prepareCall(anyString())).thenReturn(lock);
            when(connection.prepareStatement(anyString())).thenReturn(statement);
            when(statement.executeQuery()).thenReturn(rows);
            // The due row is followed by empty source sets.
            when(rows.next()).thenReturn(!force, false);
            when(rows.getBoolean(1)).thenReturn(true);

            AvatarLevelReconciler.reconcile(connection, 7, force);

            verify(connection).prepareStatement("DELETE FROM users_avatar_badge_counts WHERE user_id = ?");
            verify(connection).prepareStatement("DELETE FROM users_avatar_furni_counts WHERE user_id = ?");
            verify(connection).prepareStatement("DELETE FROM users_avatar_achievement_progress WHERE user_id = ?");
        }
    }
}
