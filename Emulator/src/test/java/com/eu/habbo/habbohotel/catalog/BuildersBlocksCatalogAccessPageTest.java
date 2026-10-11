package com.eu.habbo.habbohotel.catalog;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.eu.habbo.habbohotel.items.FurnitureType;
import com.eu.habbo.habbohotel.items.Item;
import com.eu.habbo.habbohotel.items.ItemInteraction;
import com.eu.habbo.habbohotel.items.interactions.InteractionDefault;
import java.util.Set;
import org.junit.jupiter.api.Test;

class BuildersBlocksCatalogAccessPageTest {
    @Test
    void normalAccessKeepsPaidOfferLookupSeparateAndRefreshesSourceRestrictions() throws Exception {
        CatalogManager manager = CatalogReadFixture.manager();
        var root =
                CatalogReadFixture.page(-1, -2, 0, true, true, false, 0, "root", "default_3x3", CatalogPageType.NORMAL);
        var paid =
                CatalogReadFixture.page(10, -1, 0, true, true, false, 1, "paid", "default_3x3", CatalogPageType.NORMAL);
        var source = CatalogReadFixture.page(
                20004677, -1, 0, true, true, false, 1, "da habbo", "default_3x3", CatalogPageType.NORMAL);
        CatalogReadFixture.attach(manager, CatalogPageType.NORMAL, root);
        CatalogReadFixture.attach(manager, CatalogPageType.NORMAL, paid);
        CatalogReadFixture.attach(manager, CatalogPageType.NORMAL, source);
        var normalItem = CatalogReadFixture.item(72, 10, 0);
        var builderItem = mock(CatalogItem.class);
        var base = mock(Item.class);
        var interaction = new ItemInteraction("default", InteractionDefault.class);
        when(base.getName()).thenReturn("bc_cone_recolorable");
        when(base.getType()).thenReturn(FurnitureType.FLOOR);
        when(base.getInteractionType()).thenReturn(interaction);
        when(builderItem.getId()).thenReturn(73);
        when(builderItem.getAmount()).thenReturn(1);
        when(builderItem.getBaseItems()).thenReturn(Set.of(base));
        paid.addItem(normalItem);
        source.addItem(builderItem);
        BuildersBlocksCatalogAccessPage.install(manager);
        BuildersBlocksCatalogAccessPage.install(manager);
        manager.markCatalogChanged();
        var alias = (BuildersBlocksCatalogAccessPage) manager.getCatalogPage(1820004678, CatalogPageType.NORMAL);
        assertNotNull(alias);
        assertEquals(20004677, alias.getSourcePageId());
        assertEquals(CatalogPageType.NORMAL, alias.getSourcePageType());
        assertSame(builderItem, alias.getCatalogItem(73));
        assertSame(normalItem, manager.getCatalogItem(72, CatalogPageType.NORMAL));
        assertSame(builderItem, manager.getCatalogItem(73, CatalogPageType.NORMAL));
        assertEquals(5, manager.getCatalogPagesMap(CatalogPageType.NORMAL).size());
        when(base.getName()).thenReturn("bc_cone*13");
        manager.markCatalogChanged();
        assertTrue(manager.getEffectivePageItems(alias).isEmpty());
        when(base.getName()).thenReturn("bc_cone_recolorable");
        manager.markCatalogChanged();
        source.setRank(5);
        source.setEnabled(false);
        source.getCatalogItems().clear();
        manager.markCatalogChanged();
        assertEquals(5, alias.getRank());
        assertFalse(alias.isEnabled());
        assertTrue(manager.getEffectivePageItems(alias).isEmpty());
    }
}
