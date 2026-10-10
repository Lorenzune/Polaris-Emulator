package com.eu.habbo.habbohotel.catalog;

import com.eu.habbo.messages.ServerMessage;
import java.util.ArrayList;
import java.util.List;

/** Normal-tree access to existing BC offers; never creates a purchasable normal offer. */
public final class BuildersBlocksCatalogAccessPage extends CatalogPage {
    public static final String WIRE_LAYOUT = "builders_club_blocks";
    private static final int ROOT_ID = 1_800_000_000;
    public static final int RECOLORABLE_SOURCE_PAGE_ID = 20_004_677;
    private final int sourcePageId;
    private final CatalogPage source;

    private BuildersBlocksCatalogAccessPage(int id, int parentId, CatalogPage source) {
        this.id = id;
        this.parentId = parentId;
        this.source = source;
        this.sourcePageId = source == null ? -1 : source.getId();
        this.catalogPageType = CatalogPageType.NORMAL;
        this.layout = WIRE_LAYOUT;
        this.rank = source == null ? 1 : source.getRank();
        this.caption = source == null ? "Building Blocks" : "Blocchi ricolorabili";
        this.pageName = "builders_blocks_access_" + this.sourcePageId;
        this.iconColor = source == null ? 1 : source.getIconColor();
        this.iconImage = source == null ? 35 : source.getIconImage();
        this.orderNum = source == null ? 999 : source.getOrderNum();
        this.visible = source == null || source.isVisible();
        this.enabled = source == null || source.isEnabled();
        this.headerImage = source == null ? "block_header" : source.getHeaderImage();
        this.teaserImage = source == null ? "teaser_blocks" : source.getTeaserImage();
        this.specialImage = source == null ? "" : source.getSpecialImage();
        this.textOne =
                source == null ? "Posa i blocchi direttamente nella stanza con Builders Club." : source.getTextOne();
        this.textDetails = source == null ? "" : source.getTextDetails();
        this.textTeaser = source == null ? "" : source.getTextTeaser();
        refreshOffers();
    }

    @Override
    public int getRank() {
        return source == null ? super.getRank() : source.getRank();
    }

    @Override
    public boolean isVisible() {
        return source == null ? super.isVisible() : source.isVisible();
    }

    @Override
    public boolean isEnabled() {
        return source == null ? super.isEnabled() : source.isEnabled();
    }

    void refreshOffers() {
        if (source == null) return;
        var items = this.getCatalogItems();
        synchronized (items) {
            items.clear();
            for (CatalogItem offer : source.getCatalogItems().values()) {
                if (isRecolorableOffer(offer)) items.put(offer.getId(), offer);
            }
        }
    }

    public int getSourcePageId() {
        return this.sourcePageId;
    }

    public CatalogPageType getSourcePageType() {
        return source == null ? CatalogPageType.NORMAL : source.getCatalogPageType();
    }

    public static boolean isRecolorableBlock(com.eu.habbo.habbohotel.items.Item item) {
        return item != null
                && item.getType() == com.eu.habbo.habbohotel.items.FurnitureType.FLOOR
                && item.getName() != null
                && item.getName().startsWith("bc_")
                && item.getName().endsWith("_recolorable")
                && !item.getName().contains("*")
                && item.getInteractionType() != null
                && item.getInteractionType().getType()
                        == com.eu.habbo.habbohotel.items.interactions.InteractionDefault.class;
    }

    public static boolean isRecolorableOffer(CatalogItem offer) {
        return offer != null
                && !offer.isLimited()
                && offer.getAmount() == 1
                && offer.getBaseItems().size() == 1
                && isRecolorableBlock(offer.getBaseItems().iterator().next());
    }

    /** Called after both catalogs have loaded; a reload replaces all aliases rather than duplicating them. */
    static void install(CatalogManager manager) {
        var normal = manager.getCatalogPagesMap(CatalogPageType.NORMAL);
        var builder = manager.getCatalogPagesMap(CatalogPageType.BUILDER);
        synchronized (normal) {
            CatalogPage root = normal.get(-1);
            if (root == null || normal.containsKey(ROOT_ID)) return;
            var category = new BuildersBlocksCatalogAccessPage(ROOT_ID, -1, null);
            List<BuildersBlocksCatalogAccessPage> aliases = new ArrayList<>();
            CatalogPage restored = normal.get(RECOLORABLE_SOURCE_PAGE_ID);
            if (restored != null && restored.isVisible() && restored.isEnabled()) {
                var alias = new BuildersBlocksCatalogAccessPage(ROOT_ID + 1 + restored.getId(), ROOT_ID, restored);
                if (!alias.getCatalogItems().isEmpty() && !normal.containsKey(alias.getId())) aliases.add(alias);
            }
            synchronized (builder) {
                if (aliases.isEmpty()) {
                    for (CatalogPage source : builder.values()) {
                        if (source == null || source.getId() <= 0 || !source.isVisible() || !source.isEnabled())
                            continue;
                        long aliasId = ROOT_ID + 1L + source.getId();
                        if (aliasId > Integer.MAX_VALUE || normal.containsKey((int) aliasId)) continue;
                        var alias = new BuildersBlocksCatalogAccessPage((int) aliasId, ROOT_ID, source);
                        if (!alias.getCatalogItems().isEmpty()) aliases.add(alias);
                    }
                }
            }
            if (aliases.isEmpty()) return;
            normal.put(category.getId(), category);
            root.addChildPage(category);
            for (var alias : aliases) {
                normal.put(alias.getId(), alias);
                category.addChildPage(alias);
            }
        }
    }

    @Override
    public void serialize(ServerMessage message) {
        message.appendString(WIRE_LAYOUT);
        message.appendInt(3);
        message.appendString(this.getHeaderImage());
        message.appendString(this.getTeaserImage());
        message.appendString(this.getSpecialImage());
        message.appendInt(3);
        message.appendString(this.getTextOne());
        message.appendString(this.getTextDetails());
        message.appendString(this.getTextTeaser());
    }
}
