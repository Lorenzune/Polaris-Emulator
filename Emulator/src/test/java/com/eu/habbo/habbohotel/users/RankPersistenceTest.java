package com.eu.habbo.habbohotel.users;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.CALLS_REAL_METHODS;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.eu.habbo.Emulator;
import com.eu.habbo.database.SqlQueries;
import com.eu.habbo.habbohotel.GameEnvironment;
import com.eu.habbo.habbohotel.permissions.PermissionsManager;
import com.eu.habbo.habbohotel.permissions.Rank;
import com.eu.habbo.plugin.PluginManager;
import org.junit.jupiter.api.Test;

class RankPersistenceTest {
    @Test
    void failedDatabaseWriteLeavesOnlineRankUnchanged() throws Exception {
        check(false);
    }

    @Test
    void successfulDatabaseWriteUpdatesRankEvenWithoutLoadedStats() throws Exception {
        check(true);
    }

    private void check(boolean success) throws Exception {
        HabboManager manager = mock(HabboManager.class, CALLS_REAL_METHODS);
        Habbo habbo = mock(Habbo.class);
        HabboInfo info = mock(HabboInfo.class);
        when(habbo.getHabboInfo()).thenReturn(info);
        doReturn(habbo).when(manager).getHabbo(42);
        GameEnvironment environment = mock(GameEnvironment.class);
        PermissionsManager permissions = mock(PermissionsManager.class);
        Rank next = new Rank(2);
        when(environment.getPermissionsManager()).thenReturn(permissions);
        when(permissions.rankExists(2)).thenReturn(true);
        when(permissions.getRank(2)).thenReturn(next);
        try (var emulator = mockStatic(Emulator.class);
                var sql = mockStatic(SqlQueries.class)) {
            emulator.when(Emulator::getGameEnvironment).thenReturn(environment);
            emulator.when(Emulator::getPluginManager).thenReturn(mock(PluginManager.class));
            if (success) {
                sql.when(() -> SqlQueries.update("UPDATE users SET `rank` = ? WHERE id = ? LIMIT 1", 2, 42))
                        .thenAnswer(call -> {
                            verify(info, never()).setRank(next);
                            return 1;
                        });
                manager.setRank(42, 2);
                verify(info).setRank(next);
            } else {
                sql.when(() -> SqlQueries.update("UPDATE users SET `rank` = ? WHERE id = ? LIMIT 1", 2, 42))
                        .thenThrow(new SqlQueries.DataAccessException("unavailable", null));
                assertThrows(SqlQueries.DataAccessException.class, () -> manager.setRank(42, 2));
                verify(info, never()).setRank(next);
            }
        }
    }
}
