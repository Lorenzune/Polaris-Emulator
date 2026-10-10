package com.eu.habbo.messages.incoming.catalog;

import com.eu.habbo.habbohotel.catalog.CatalogItem;
import com.eu.habbo.habbohotel.catalog.CatalogManager;
import com.eu.habbo.habbohotel.catalog.CatalogPage;
import com.eu.habbo.habbohotel.catalog.CatalogPageLayouts;
import com.eu.habbo.habbohotel.catalog.CatalogPageType;
import com.eu.habbo.habbohotel.catalog.ClubOffer;
import java.util.Objects;
import java.util.function.Predicate;

final class CatalogPurchasePageResolver {

    interface CatalogLookup {
        CatalogItem findItem(int itemId);

        CatalogPage findPage(int pageId);

        default CatalogItem findBuildersClubItem(int itemId) {
            return null;
        }

        default CatalogPage findBuildersClubPage(int pageId) {
            return null;
        }

        default boolean isBuildersClubOffer(int offerId) {
            return false;
        }
    }

    private final CatalogLookup catalog;

    CatalogPurchasePageResolver(CatalogManager catalogManager) {
        Objects.requireNonNull(catalogManager);
        this.catalog = new CatalogLookup() {
            @Override
            public CatalogItem findItem(int itemId) {
                return catalogManager.getCatalogItem(itemId);
            }

            @Override
            public CatalogPage findPage(int pageId) {
                return catalogManager.getCatalogPage(pageId);
            }

            @Override
            public CatalogItem findBuildersClubItem(int itemId) {
                return catalogManager.getCatalogItem(itemId, CatalogPageType.BUILDER);
            }

            @Override
            public CatalogPage findBuildersClubPage(int pageId) {
                return catalogManager.getCatalogPage(pageId, CatalogPageType.BUILDER);
            }

            @Override
            public boolean isBuildersClubOffer(int offerId) {
                ClubOffer offer = catalogManager.clubOffers.get(offerId);
                return offer != null && (offer.isBuildersClubSubscription() || offer.isBuildersClubAddon());
            }
        };
    }

    CatalogPurchasePageResolver(CatalogLookup catalog) {
        this.catalog = Objects.requireNonNull(catalog);
    }

    CatalogPage resolve(CatalogPurchaseCommand command, Predicate<CatalogPage> canAccess) {
        CatalogPage page;
        if (command.pageId() == -12345678 || command.pageId() == -1) {
            CatalogItem searchedItem = this.catalog.findItem(command.itemId());
            boolean buildersClubItem = false;

            if (searchedItem == null) {
                searchedItem = this.catalog.findBuildersClubItem(command.itemId());
                buildersClubItem = searchedItem != null;
            }

            if (searchedItem == null || searchedItem.getOfferId() <= 0) {
                return null;
            }

            page = buildersClubItem
                    ? this.catalog.findBuildersClubPage(searchedItem.getPageId())
                    : this.catalog.findPage(searchedItem.getPageId());
        } else {
            CatalogPage normalPage = this.catalog.findPage(command.pageId());
            CatalogPage buildersClubPage = this.catalog.findBuildersClubPage(command.pageId());

            // Builders' Club offers may be rendered from the normal catalog
            // tree. Only use the separate Builder catalog when it actually
            // owns the requested offer; page ids may overlap with normal pages.
            // If the normal page does not exist, retain support for a standalone
            // Builder subscription page with no catalog-item row.
            page = containsOffer(buildersClubPage, command.itemId())
                            || (normalPage == null && this.catalog.isBuildersClubOffer(command.itemId()))
                    ? buildersClubPage
                    : normalPage;
        }

        if (page == null
                || page instanceof com.eu.habbo.habbohotel.catalog.BuildersBlocksCatalogAccessPage
                || page.getLayout() != null && page.getLayout().equalsIgnoreCase(CatalogPageLayouts.club_gift.name())) {
            return null;
        }
        return canAccess.test(page) ? page : null;
    }

    private static boolean containsOffer(CatalogPage page, int offerId) {
        if (page == null) {
            return false;
        }

        if (page.getCatalogItem(offerId) != null) {
            return true;
        }

        var catalogItems = page.getCatalogItems();
        if (catalogItems == null) {
            return false;
        }

        return catalogItems.values().stream().anyMatch(item -> item != null && item.getOfferId() == offerId);
    }
}
