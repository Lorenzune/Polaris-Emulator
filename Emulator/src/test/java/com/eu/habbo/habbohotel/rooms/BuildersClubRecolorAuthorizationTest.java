package com.eu.habbo.habbohotel.rooms;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.eu.habbo.habbohotel.catalog.CatalogItem;
import com.eu.habbo.habbohotel.catalog.CatalogManager;
import com.eu.habbo.habbohotel.catalog.CatalogPage;
import com.eu.habbo.habbohotel.catalog.CatalogPageType;
import com.eu.habbo.habbohotel.items.FurnitureType;
import com.eu.habbo.habbohotel.items.Item;
import com.eu.habbo.habbohotel.users.Habbo;
import com.eu.habbo.habbohotel.users.HabboInfo;
import com.eu.habbo.habbohotel.users.HabboItem;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import java.util.Set;
import org.junit.jupiter.api.Test;

class BuildersClubRecolorAuthorizationTest {
    @Test
    void hiddenDisabledOrStaffVariantsAreNotRecolorTargets() {
        var manager = mock(CatalogManager.class);
        var page = mock(CatalogPage.class);
        var offer = mock(CatalogItem.class);
        var item = mock(Item.class);
        var pages = new Int2ObjectOpenHashMap<CatalogPage>();
        var offers = new Int2ObjectOpenHashMap<CatalogItem>();
        pages.put(1, page);
        offers.put(72, offer);
        when(manager.getCatalogPagesMap(CatalogPageType.BUILDER)).thenReturn(pages);
        when(page.getCatalogItems()).thenReturn(offers);
        when(page.getRank()).thenReturn(1);
        when(page.isVisible()).thenReturn(true);
        when(page.isEnabled()).thenReturn(true);
        when(offer.getAmount()).thenReturn(1);
        when(offer.getBaseItems()).thenReturn(Set.of(item));
        when(item.getId()).thenReturn(123);
        assertTrue(BuildersClubRecolorService.targetAllowedInCatalog(manager, 123, 1));
        when(page.getRank()).thenReturn(3);
        assertFalse(BuildersClubRecolorService.targetAllowedInCatalog(manager, 123, 1));
        when(page.getRank()).thenReturn(1);
        when(page.isVisible()).thenReturn(false);
        assertFalse(BuildersClubRecolorService.targetAllowedInCatalog(manager, 123, 1));
        when(page.isVisible()).thenReturn(true);
        when(page.isEnabled()).thenReturn(false);
        assertFalse(BuildersClubRecolorService.targetAllowedInCatalog(manager, 123, 1));
    }

    @Test
    void malformedScopeAndMissingRightsCannotTouchItems() {
        Habbo habbo = mock(Habbo.class);
        HabboInfo info = mock(HabboInfo.class);
        Room room = mock(Room.class);
        when(habbo.getHabboInfo()).thenReturn(info);
        when(info.getCurrentRoom()).thenReturn(room);
        when(info.getId()).thenReturn(991100);
        when(room.getId()).thenReturn(991101);
        when(room.isLoaded()).thenReturn(true);
        assertEquals(
                BuildersClubRecolorService.Result.NOT_RECOLORABLE,
                BuildersClubRecolorService.recolor(habbo, 72, 13, 2));
        try (var rights = mockStatic(BuildersClubRoomSupport.class)) {
            rights.when(() -> BuildersClubRoomSupport.canPlaceInRoom(habbo, room))
                    .thenReturn(false);
            assertEquals(
                    BuildersClubRecolorService.Result.NO_RIGHTS, BuildersClubRecolorService.recolor(habbo, 72, 13, 0));
        }
        verify(room, never()).getHabboItem(72);
    }

    @Test
    void limitedFurnitureIsRejectedBeforeAnyDatabaseAccess() {
        Habbo habbo = mock(Habbo.class);
        HabboInfo info = mock(HabboInfo.class);
        Room room = mock(Room.class);
        HabboItem item = mock(HabboItem.class);
        Item base = mock(Item.class);
        when(habbo.getHabboInfo()).thenReturn(info);
        when(info.getCurrentRoom()).thenReturn(room);
        when(info.getId()).thenReturn(991200);
        when(room.getId()).thenReturn(991201);
        when(room.isLoaded()).thenReturn(true);
        when(room.getHabboItem(72)).thenReturn(item);
        when(item.getBaseItem()).thenReturn(base);
        when(base.getType()).thenReturn(FurnitureType.FLOOR);
        when(item.isLimited()).thenReturn(true);
        try (var rights = mockStatic(BuildersClubRoomSupport.class)) {
            rights.when(() -> BuildersClubRoomSupport.canPlaceInRoom(habbo, room))
                    .thenReturn(true);
            assertEquals(
                    BuildersClubRecolorService.Result.NOT_RECOLORABLE,
                    BuildersClubRecolorService.recolor(habbo, 72, 13, 0));
        }
        verify(item, never()).setBaseItem(org.mockito.ArgumentMatchers.any());
    }
}
