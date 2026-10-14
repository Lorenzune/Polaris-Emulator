package com.eu.habbo.habbohotel.users.levels;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.RETURNS_DEEP_STUBS;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.eu.habbo.core.ConfigurationManager;
import com.eu.habbo.habbohotel.GameEnvironment;
import java.sql.CallableStatement;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.HashMap;
import javax.sql.DataSource;
import org.junit.jupiter.api.Test;

class AvatarLevelInitializationTest {
    @Test
    void definitionReloadInitializesAllCachedComponentsBeforeCommitting() throws Exception {
        DataSource database = mock(DataSource.class);
        Connection connection = mock(Connection.class);
        Statement statement = mock(Statement.class);
        PreparedStatement prepared = mock(PreparedStatement.class);
        CallableStatement initialize = mock(CallableStatement.class);
        ResultSet rows = mock(ResultSet.class);
        GameEnvironment environment = mock(GameEnvironment.class, RETURNS_DEEP_STUBS);
        when(environment.getAchievementManager().getAchievements()).thenReturn(new HashMap<>());
        when(database.getConnection()).thenReturn(connection);
        when(connection.createStatement()).thenReturn(statement);
        when(connection.prepareStatement(anyString())).thenReturn(prepared);
        when(statement.executeQuery(anyString())).thenReturn(rows);
        when(connection.prepareCall("{CALL avatar_levels_initialize_components()}"))
                .thenReturn(initialize);
        AvatarLevelManager manager = new AvatarLevelManager(database, environment, mock(ConfigurationManager.class));

        manager.initializeDefinitions();

        var order = inOrder(prepared, initialize, connection);
        order.verify(prepared).executeUpdate();
        order.verify(initialize).execute();
        order.verify(connection).commit();
    }
}
