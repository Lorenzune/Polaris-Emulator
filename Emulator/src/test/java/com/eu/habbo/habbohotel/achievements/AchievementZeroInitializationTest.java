package com.eu.habbo.habbohotel.achievements;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.RETURNS_DEEP_STUBS;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.eu.habbo.Emulator;
import com.eu.habbo.database.Database;
import com.eu.habbo.habbohotel.users.Habbo;
import com.eu.habbo.plugin.PluginManager;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.HashMap;
import org.junit.jupiter.api.Test;

class AchievementZeroInitializationTest {
    @Test
    void registrationDurationCommitsAZeroRowAndInitializesLiveProgress() throws Exception {
        var achievement = new Achievement(123, "RegistrationDuration", "other", (short) 1, "", 0);
        achievement.addLevel(new AchievementLevel(1, 0, 0, 1, 1));
        Habbo habbo = mock(Habbo.class, RETURNS_DEEP_STUBS);
        when(habbo.isOnline()).thenReturn(true);
        when(habbo.getHabboInfo().getId()).thenReturn(7);
        when(habbo.getHabboStats().getAchievementProgress()).thenReturn(new HashMap<>());
        when(habbo.getHabboStats().getAchievementProgress(achievement)).thenReturn(-1);
        Database database = mock(Database.class, RETURNS_DEEP_STUBS);
        Connection connection = mock(Connection.class);
        PreparedStatement statement = mock(PreparedStatement.class);
        ResultSet row = mock(ResultSet.class);
        when(database.getDataSource().getConnection()).thenReturn(connection);
        when(connection.prepareStatement(anyString())).thenReturn(statement);
        when(statement.executeQuery()).thenReturn(row);
        when(row.next()).thenReturn(true);
        when(row.getInt(1)).thenReturn(0);
        try (var emulator = mockStatic(Emulator.class)) {
            emulator.when(Emulator::getDatabase).thenReturn(database);
            emulator.when(Emulator::getPluginManager).thenReturn(mock(PluginManager.class));
            AchievementManager.progressAchievement(habbo, achievement, 0);
        }
        verify(statement).setInt(3, 0);
        verify(statement).executeUpdate();
        verify(connection).commit();
        verify(habbo.getHabboStats()).setProgress(achievement, 0);
    }
}
