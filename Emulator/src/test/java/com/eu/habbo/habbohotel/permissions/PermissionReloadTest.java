package com.eu.habbo.habbohotel.permissions;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

import com.eu.habbo.Emulator;
import com.eu.habbo.database.Database;
import com.zaxxer.hikari.HikariDataSource;
import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import org.junit.jupiter.api.Test;

class PermissionReloadTest {
    private Rank rank(int id, PermissionSetting setting) throws Exception {
        ResultSet rows = mock(ResultSet.class);
        when(rows.getInt("id")).thenReturn(id);
        when(rows.getMetaData()).thenReturn(mock(ResultSetMetaData.class));
        Rank rank = new Rank(rows);
        rank.setPermission("acc_supporttool", setting);
        return rank;
    }

    private void publish(PermissionsManager manager, Rank... ranks) throws Exception {
        Int2ObjectMap<Rank> loaded = new Int2ObjectOpenHashMap<>();
        for (Rank rank : ranks) loaded.put(rank.getId(), rank);
        var method = PermissionsManager.class.getDeclaredMethod("publish", Int2ObjectMap.class);
        method.setAccessible(true);
        method.invoke(manager, loaded);
    }

    @Test
    void reloadUpdatesHeldRankRevokesRemovedRankAndPreservesStateOnDatabaseFailure() throws Exception {
        Database database = mock(Database.class);
        HikariDataSource source = mock(HikariDataSource.class);
        when(database.getDataSource()).thenReturn(source);
        when(source.getConnection()).thenThrow(new SQLException("unavailable"));
        try (var emulator = mockStatic(Emulator.class)) {
            emulator.when(Emulator::getDatabase).thenReturn(database);
            PermissionsManager manager = new PermissionsManager();
            Rank held = rank(1, PermissionSetting.ALLOWED);
            Rank removed = rank(2, PermissionSetting.ALLOWED);
            publish(manager, held, removed);
            manager.reload();
            assertSame(held, manager.getRank(1));
            assertTrue(held.hasPermission("acc_supporttool", false));
            publish(manager, rank(1, PermissionSetting.DISALLOWED));
            assertSame(held, manager.getRank(1));
            assertFalse(held.hasPermission("acc_supporttool", false));
            assertFalse(removed.hasPermission("acc_supporttool", false));
        }
    }
}
