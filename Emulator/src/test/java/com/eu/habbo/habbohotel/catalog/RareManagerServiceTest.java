package com.eu.habbo.habbohotel.catalog;

import com.eu.habbo.habbohotel.permissions.Permission;
import com.eu.habbo.habbohotel.users.Habbo;
import com.google.gson.JsonObject;
import javax.sql.DataSource;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class RareManagerServiceTest {
    @Test void everyManagerActionRejectsMissingRankPermissionBeforeAccessingTheDatabase() throws Exception {
        var source=mock(DataSource.class);var actor=mock(Habbo.class);
        var service=new RareManagerService(source);
        for(String action:java.util.List.of("admin-categories","admin-page","admin-stats","admin-save-page","admin-save-item","admin-save-price","admin-save-release","admin-save-reward"))
            assertThrows(SecurityException.class,()->service.execute(actor,action,new JsonObject()));
        verifyNoInteractions(source);
    }
    @Test void grantingOtherStaffToolsDoesNotGrantRareManagement() {
        var actor=mock(Habbo.class);when(actor.hasPermission(Permission.ACC_HOUSEKEEPING)).thenReturn(true);
        when(actor.hasPermission(Permission.ACC_CATALOGFURNI)).thenReturn(true);
        assertThrows(SecurityException.class,()->RareManagerService.requirePermission(actor));
        when(actor.hasPermission(Permission.ACC_RARE_MANAGER)).thenReturn(true);
        assertDoesNotThrow(()->RareManagerService.requirePermission(actor));
        assertThrows(SecurityException.class,()->RareManagerService.requirePermission(null));
    }
    @Test void collectionBundleParsingDeduplicatesBasesWithoutMultiplyingOfferQuantities() {
        assertEquals(java.util.List.of(12,13,14),RareCatalogCollections.baseIds("12:5;13;12;14:2"));
        assertEquals(java.util.List.of(),RareCatalogCollections.baseIds(""));
        assertThrows(IllegalArgumentException.class,()->RareCatalogCollections.baseIds("12;broken"));
    }
}
