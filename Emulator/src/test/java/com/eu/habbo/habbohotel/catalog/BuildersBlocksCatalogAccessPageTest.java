package com.eu.habbo.habbohotel.catalog;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

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
                100, -1, 0, true, true, false, 1, "blocks", "default_3x3_color_grouping", CatalogPageType.BUILDER);
        CatalogReadFixture.attach(manager, CatalogPageType.NORMAL, root);
        CatalogReadFixture.attach(manager, CatalogPageType.NORMAL, paid);
        CatalogReadFixture.attach(manager, CatalogPageType.BUILDER, source);
        var normalItem = CatalogReadFixture.item(72, 10, 0);
        var builderItem = CatalogReadFixture.item(72, 100, 0);
        paid.addItem(normalItem);
        source.addItem(builderItem);
        BuildersBlocksCatalogAccessPage.install(manager);
        BuildersBlocksCatalogAccessPage.install(manager);
        manager.markCatalogChanged();
        var alias = (BuildersBlocksCatalogAccessPage) manager.getCatalogPage(1800000101, CatalogPageType.NORMAL);
        assertNotNull(alias);
        assertEquals(100, alias.getSourcePageId());
        assertSame(builderItem, alias.getCatalogItem(72));
        assertSame(normalItem, manager.getCatalogItem(72, CatalogPageType.NORMAL));
        assertSame(builderItem, manager.getCatalogItem(72, CatalogPageType.BUILDER));
        assertEquals(4, manager.getCatalogPagesMap(CatalogPageType.NORMAL).size());
        source.setRank(5);
        source.setEnabled(false);
        source.getCatalogItems().clear();
        manager.markCatalogChanged();
        assertEquals(5, alias.getRank());
        assertFalse(alias.isEnabled());
        assertTrue(manager.getEffectivePageItems(alias).isEmpty());
    }
}
