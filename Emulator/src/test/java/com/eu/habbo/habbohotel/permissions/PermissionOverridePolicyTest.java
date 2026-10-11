package com.eu.habbo.habbohotel.permissions;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.eu.habbo.habbohotel.users.Habbo;
import com.eu.habbo.habbohotel.users.HabboInfo;
import java.util.List;
import org.junit.jupiter.api.Test;

class PermissionOverridePolicyTest {
    @Test
    void roomScopeCannotBeDelegatedAsGlobal() {
        Habbo actor = mock(Habbo.class);
        HabboInfo info = mock(HabboInfo.class);
        PermissionsManager manager = mock(PermissionsManager.class);
        when(actor.getHabboInfo()).thenReturn(info);
        when(info.getId()).thenReturn(99);
        when(manager.hasBasePermission(actor, "acc_test", true)).thenReturn(true);
        var overrides = new UserPermissionOverrides(id -> List.of(), () -> 1000L);
        when(manager.getOverrides()).thenReturn(overrides);
        when(info.getRank()).thenReturn(mock(Rank.class));
        assertThrows(
                SecurityException.class,
                () -> PermissionOverridePolicy.authorizeGrant(
                        actor, manager, "acc_test", PermissionSetting.ALLOWED, 0, 1000));
        assertDoesNotThrow(() -> PermissionOverridePolicy.authorizeGrant(
                actor, manager, "acc_test", PermissionSetting.ROOM_OWNER, 0, 1000));
    }

    @Test
    void temporaryEditorCannotGrantPermanentOrLongerPrivileges() {
        Habbo actor = mock(Habbo.class);
        HabboInfo info = mock(HabboInfo.class);
        PermissionsManager manager = mock(PermissionsManager.class);
        when(actor.getHabboInfo()).thenReturn(info);
        when(info.getId()).thenReturn(99);
        when(info.getRank()).thenReturn(mock(Rank.class));
        when(manager.hasBasePermission(actor, "acc_test", false)).thenReturn(true);
        var overrides = new UserPermissionOverrides(
                id -> List.of(new UserPermissionOverrides.UserOverride(
                        "cmd_perm_edit", PermissionSetting.ALLOWED, 2000, "", 1, "")),
                () -> 1000L);
        when(manager.getOverrides()).thenReturn(overrides);
        assertThrows(
                SecurityException.class,
                () -> PermissionOverridePolicy.authorizeGrant(
                        actor, manager, "acc_test", PermissionSetting.ALLOWED, 0, 1000));
        assertThrows(
                SecurityException.class,
                () -> PermissionOverridePolicy.authorizeGrant(
                        actor, manager, "acc_test", PermissionSetting.ALLOWED, 2001, 1000));
        assertDoesNotThrow(() -> PermissionOverridePolicy.authorizeGrant(
                actor, manager, "acc_test", PermissionSetting.ALLOWED, 2000, 1000));
    }

    @Test
    void freshlyReadDenialWinsOverOldCachedGrant() {
        Habbo actor = mock(Habbo.class);
        PermissionsManager manager = mock(PermissionsManager.class);
        when(manager.hasBasePermission(actor, "acc_test", false)).thenReturn(true);
        var denial =
                new UserPermissionOverrides.UserOverride("acc_test", PermissionSetting.DISALLOWED, 0, "revoked", 1, "");
        var verified = new UserPermissionOverrides.Snapshot(true, java.util.Map.of("acc_test", denial), 1000, 0);
        assertThrows(
                SecurityException.class,
                () -> PermissionOverridePolicy.authorizeGrant(
                        actor, manager, "acc_test", PermissionSetting.ALLOWED, 0, 1000, verified));
        org.mockito.Mockito.verify(manager, org.mockito.Mockito.never()).getOverrides();
    }
}
