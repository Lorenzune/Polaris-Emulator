package com.eu.habbo.habbohotel.permissions;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.anyString;
import static org.mockito.Mockito.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.eu.habbo.Emulator;
import com.eu.habbo.database.Database;
import com.eu.habbo.habbohotel.users.Habbo;
import com.eu.habbo.habbohotel.users.HabboInfo;
import com.eu.habbo.plugin.HabboPlugin;
import com.eu.habbo.plugin.PluginManager;
import com.zaxxer.hikari.HikariDataSource;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import java.sql.SQLException;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

class PermissionOverrideResolutionTest {
    @Test
    void personalGrantEnablesOnlyTheSelectedUserWhenRankSevenDeniesIt() throws Exception {
        try (var fixture =
                new Fixture(List.of(row(PermissionSetting.ALLOWED, 0)), PermissionSetting.DISALLOWED, false, 7)) {
            Habbo other = mock(Habbo.class);
            HabboInfo otherInfo = mock(HabboInfo.class);
            when(otherInfo.getId()).thenReturn(43);
            Rank sameRank = fixture.habbo.getHabboInfo().getRank();
            when(otherInfo.getRank()).thenReturn(sameRank);
            when(other.getHabboInfo()).thenReturn(otherInfo);
            assertTrue(fixture.manager.hasPermission(fixture.habbo, "acc_test"));
            assertFalse(fixture.manager.hasPermission(other, "acc_test"));
            assertFalse(sameRank.hasPermission("acc_test", false));
            assertEquals(
                    1, fixture.manager.getEffectivePermissions(fixture.habbo).get("acc_test"));
        }
    }

    @Test
    void twoUsersOfRankSevenHaveIndependentPermissionsAndInheritanceRestoresOnlyOne() throws Exception {
        var overrides = new java.util.ArrayList<UserPermissionOverrides.UserOverride>();
        overrides.add(row(PermissionSetting.DISALLOWED, 0));
        try (var fixture = new Fixture(overrides, PermissionSetting.ALLOWED, false, 7)) {
            Habbo other = mock(Habbo.class);
            HabboInfo otherInfo = mock(HabboInfo.class);
            when(otherInfo.getId()).thenReturn(43);
            Rank sameRank = fixture.habbo.getHabboInfo().getRank();
            when(otherInfo.getRank()).thenReturn(sameRank);
            when(other.getHabboInfo()).thenReturn(otherInfo);
            assertFalse(fixture.manager.hasPermission(fixture.habbo, "acc_test"));
            assertTrue(fixture.manager.hasPermission(other, "acc_test"));
            assertEquals(7, sameRank.getId());
            assertTrue(sameRank.hasPermission("acc_test", false));
            overrides.clear();
            fixture.manager.getOverrides().invalidate(42);
            assertTrue(fixture.manager.hasPermission(fixture.habbo, "acc_test"));
            assertTrue(fixture.manager.hasPermission(other, "acc_test"));
        }
    }

    private UserPermissionOverrides.UserOverride row(PermissionSetting setting, long expiry) {
        return new UserPermissionOverrides.UserOverride("acc_test", setting, expiry, "", 99, "actor");
    }

    @Test
    void explicitDenyBeatsRankAndPluginOnServerAndClientMap() throws Exception {
        try (var fixture =
                new Fixture(List.of(row(PermissionSetting.DISALLOWED, 0)), PermissionSetting.ALLOWED, true)) {
            assertFalse(fixture.manager.hasPermission(fixture.habbo, "acc_test", true));
            assertFalse(fixture.manager.getEffectivePermissions(fixture.habbo).containsKey("acc_test"));
            verify(fixture.plugin, never()).hasPermission(fixture.habbo, "acc_test");
        }
    }

    @Test
    void conditionalGrantRequiresRoomRightsEvenWhenAPluginWouldAllowGlobally() throws Exception {
        try (var fixture =
                new Fixture(List.of(row(PermissionSetting.ROOM_OWNER, 0)), PermissionSetting.DISALLOWED, true)) {
            assertFalse(fixture.manager.hasPermission(fixture.habbo, "acc_test", false));
            assertTrue(fixture.manager.hasPermission(fixture.habbo, "acc_test", true));
            assertEquals(
                    2, fixture.manager.getEffectivePermissions(fixture.habbo).get("acc_test"));
        }
    }

    @Test
    void absentOverridePreservesExistingRankAndPluginRulesIncludingCustomPluginKeys() throws Exception {
        try (var fixture = new Fixture(List.of(), PermissionSetting.DISALLOWED, true)) {
            assertTrue(fixture.manager.hasPermission(fixture.habbo, "acc_test"));
            assertTrue(fixture.manager.hasPermission(fixture.habbo, "acc_custom_plugin"));
            assertEquals(
                    1, fixture.manager.getEffectivePermissions(fixture.habbo).get("acc_test"));
        }
    }

    @Test
    void expiredGrantNoLongerAuthorizesAndExpiredDenialRestoresTheRank() throws Exception {
        try (var fixture = new Fixture(
                List.of(row(PermissionSetting.ALLOWED, System.currentTimeMillis() - 1)),
                PermissionSetting.DISALLOWED,
                false)) {
            assertFalse(fixture.manager.hasPermission(fixture.habbo, "acc_test"));
            assertTrue(fixture.manager.getEffectivePermissions(fixture.habbo).isEmpty());
        }
        try (var fixture = new Fixture(
                List.of(row(PermissionSetting.DISALLOWED, System.currentTimeMillis() - 1)),
                PermissionSetting.ALLOWED,
                false)) {
            assertTrue(fixture.manager.hasPermission(fixture.habbo, "acc_test"));
        }
    }

    @Test
    void failedSnapshotDeniesRankAndPluginAndDoesNotAdvertiseGrants() throws Exception {
        try (var fixture = new Fixture(null, PermissionSetting.ALLOWED, true)) {
            assertFalse(fixture.manager.hasPermission(fixture.habbo, "acc_test"));
            assertTrue(fixture.manager.getEffectivePermissions(fixture.habbo).isEmpty());
        }
    }

    @Test
    void externallyInsertedUnknownGrantCannotIntroduceNewCapabilities() throws Exception {
        var unknown =
                new UserPermissionOverrides.UserOverride("acc_unknown", PermissionSetting.ALLOWED, 0, "", 99, "actor");
        try (var fixture = new Fixture(List.of(unknown), PermissionSetting.DISALLOWED, false)) {
            assertFalse(fixture.manager.hasPermission(fixture.habbo, "acc_unknown"));
            assertFalse(fixture.manager.getEffectivePermissions(fixture.habbo).containsKey("acc_unknown"));
        }
    }

    private static final class Fixture implements AutoCloseable {
        final org.mockito.MockedStatic<Emulator> emulator = mockStatic(Emulator.class);
        final Habbo habbo = mock(Habbo.class);
        final HabboPlugin plugin = mock(HabboPlugin.class);
        final PermissionsManager manager;

        Fixture(List<UserPermissionOverrides.UserOverride> rows, PermissionSetting base, boolean pluginGrant)
                throws Exception {
            this(rows, base, pluginGrant, 1);
        }

        Fixture(
                List<UserPermissionOverrides.UserOverride> rows,
                PermissionSetting base,
                boolean pluginGrant,
                int rankId)
                throws Exception {
            Database database = mock(Database.class);
            HikariDataSource source = mock(HikariDataSource.class);
            when(database.getDataSource()).thenReturn(source);
            when(source.getConnection()).thenThrow(new SQLException("isolated fixture"));
            emulator.when(Emulator::getDatabase).thenReturn(database);
            PluginManager plugins = mock(PluginManager.class);
            when(plugins.getPlugins()).thenReturn(Set.of(plugin));
            when(plugin.hasPermission(eq(habbo), anyString())).thenReturn(pluginGrant);
            emulator.when(Emulator::getPluginManager).thenReturn(plugins);
            var service = new UserPermissionOverrides(
                    id -> {
                        if (rows == null) throw new IllegalStateException("database unavailable");
                        return id == 42 ? rows : List.of();
                    },
                    System::currentTimeMillis);
            manager = new PermissionsManager(service);
            Rank rank = new Rank(rankId);
            rank.loadNormalizedMetadata(mock(java.sql.ResultSet.class));
            rank.setPermission("acc_test", base);
            var loaded = new Int2ObjectOpenHashMap<Rank>();
            loaded.put(rankId, rank);
            var publish = PermissionsManager.class.getDeclaredMethod(
                    "publish", it.unimi.dsi.fastutil.ints.Int2ObjectMap.class);
            publish.setAccessible(true);
            publish.invoke(manager, loaded);
            HabboInfo info = mock(HabboInfo.class);
            when(info.getId()).thenReturn(42);
            when(info.getRank()).thenReturn(rank);
            when(habbo.getHabboInfo()).thenReturn(info);
        }

        public void close() {
            emulator.close();
        }
    }
}
