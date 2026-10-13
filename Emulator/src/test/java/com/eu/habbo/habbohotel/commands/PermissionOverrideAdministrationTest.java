package com.eu.habbo.habbohotel.commands;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.eu.habbo.Emulator;
import com.eu.habbo.WiredPlatform;
import com.eu.habbo.database.Database;
import com.eu.habbo.habbohotel.GameEnvironment;
import com.eu.habbo.habbohotel.permissions.PermissionSetting;
import com.eu.habbo.habbohotel.permissions.PermissionsManager;
import com.eu.habbo.habbohotel.permissions.Rank;
import com.eu.habbo.habbohotel.permissions.UserPermissionOverrides;
import com.eu.habbo.habbohotel.users.Habbo;
import com.eu.habbo.habbohotel.users.HabboInfo;
import com.zaxxer.hikari.HikariDataSource;
import java.sql.Connection;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class PermissionOverrideAdministrationTest {
    private static final String CAPABILITY = "acc_hk_user_permissions";

    @Test
    void housekeepingGrantRechecksItsOwnCapabilityInsideTransaction() throws Exception {
        run(false, PermissionSetting.ALLOWED);
    }

    @Test
    void revocationBetweenRequestAndTransactionRejectsMutation() throws Exception {
        run(true, PermissionSetting.ALLOWED);
    }

    @Test
    void explicitDenialDoesNotRequirePossessingTheDeniedPermission() throws Exception {
        run(false, PermissionSetting.DISALLOWED);
    }

    private void run(boolean revoked, PermissionSetting setting) throws Exception {
        Habbo actor = mock(Habbo.class);
        HabboInfo actorInfo = mock(HabboInfo.class), target = mock(HabboInfo.class);
        Rank rank = mock(Rank.class);
        when(rank.getId()).thenReturn(7);
        when(actor.getHabboInfo()).thenReturn(actorInfo);
        when(actorInfo.getId()).thenReturn(99);
        when(actorInfo.getUsername()).thenReturn("Admin");
        when(actorInfo.getRank()).thenReturn(rank);
        when(target.getId()).thenReturn(42);
        when(target.getRank()).thenReturn(rank);
        when(actor.hasPermission(CAPABILITY)).thenReturn(true);
        PermissionsManager manager = mock(PermissionsManager.class);
        when(manager.isPermissionDefined("acc_test")).thenReturn(true);
        when(manager.getRank(7)).thenReturn(rank);
        when(manager.getAllRanks()).thenReturn(List.of(rank));
        when(manager.hasBasePermission(actor, "acc_test", false)).thenReturn(setting == PermissionSetting.ALLOWED);
        UserPermissionOverrides store = mock(UserPermissionOverrides.class);
        when(manager.getOverrides()).thenReturn(store);
        Connection db = mock(Connection.class);
        var fresh = new UserPermissionOverrides.Snapshot(true, Map.of(), 1000, 0);
        when(store.readForAdministration(db, 99)).thenReturn(fresh);
        when(manager.hasPermission(actor, CAPABILITY, false, fresh)).thenReturn(!revoked);
        when(store.change(eq(db), eq(42), eq("acc_test"), eq(setting), eq(0L), any(), any()))
                .thenAnswer(call -> {
                    UserPermissionOverrides.MutationGuard guard = call.getArgument(6);
                    guard.authorize(7, 7, null);
                    return true;
                });
        GameEnvironment environment = mock(GameEnvironment.class);
        when(environment.getPermissionsManager()).thenReturn(manager);
        Database database = mock(Database.class);
        HikariDataSource source = mock(HikariDataSource.class);
        when(database.getDataSource()).thenReturn(source);
        when(source.getConnection()).thenReturn(db);
        try (var platform = mockStatic(WiredPlatform.class);
                var emulator = mockStatic(Emulator.class)) {
            platform.when(WiredPlatform::gameEnvironment).thenReturn(environment);
            platform.when(WiredPlatform::database).thenReturn(database);
            emulator.when(Emulator::getGameEnvironment).thenReturn(environment);
            if (revoked)
                assertThrows(
                        SecurityException.class,
                        () -> PermissionOverrideAdministration.change(
                                actor, target, "acc_test", setting, 0, "housekeeping", "", CAPABILITY));
            else
                assertTrue(PermissionOverrideAdministration.change(
                        actor, target, "acc_test", setting, 0, "housekeeping", "", CAPABILITY));
            verify(store).readForAdministration(db, 99);
            verify(manager).hasPermission(actor, CAPABILITY, false, fresh);
            verify(actor, never()).hasPermission("cmd_perm_edit");
        }
    }
}
