package com.eu.habbo.messages.incoming.housekeeping;

import com.eu.habbo.habbohotel.permissions.PermissionsManager;
import com.eu.habbo.habbohotel.users.Habbo;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class HousekeepingPermissionSyncTest {
    @Test void reloadsMissingDefinitionsOnlyForAnAuthorizedOperator() {
        var permissions=mock(PermissionsManager.class);var actor=mock(Habbo.class);
        when(actor.hasPermission("cmd_update_permissions")).thenReturn(true);
        assertTrue(HousekeepingPermissionSync.needsReload(permissions,actor));
    }
    @Test void aDefinedPermissionIsNeverReplacedByPanelAccess() {
        var permissions=mock(PermissionsManager.class);var actor=mock(Habbo.class);
        when(permissions.isPermissionDefined(HousekeepingAreas.ROOMS)).thenReturn(true);
        when(actor.hasPermission("cmd_update_permissions")).thenReturn(true);
        assertFalse(HousekeepingPermissionSync.needsReload(permissions,actor));
    }
    @Test void ordinaryPanelAccessDoesNotAuthorizeAReload() {
        var permissions=mock(PermissionsManager.class);var actor=mock(Habbo.class);
        when(actor.hasPermission("acc_housekeeping")).thenReturn(true);
        assertFalse(HousekeepingPermissionSync.needsReload(permissions,actor));
    }
}
