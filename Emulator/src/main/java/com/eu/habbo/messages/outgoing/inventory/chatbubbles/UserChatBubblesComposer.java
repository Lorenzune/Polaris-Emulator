package com.eu.habbo.messages.outgoing.inventory.chatbubbles;

import com.eu.habbo.Emulator;
import com.eu.habbo.habbohotel.permissions.Permission;
import com.eu.habbo.habbohotel.users.Habbo;
import com.eu.habbo.messages.ServerMessage;
import com.eu.habbo.messages.outgoing.MessageComposer;
import com.eu.habbo.messages.outgoing.Outgoing;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

public class UserChatBubblesComposer extends MessageComposer {
    private static final Logger LOGGER = LoggerFactory.getLogger(UserChatBubblesComposer.class);

    private final Habbo habbo;

    public UserChatBubblesComposer(Habbo habbo) {
        this.habbo = habbo;
    }

    @Override
    protected ServerMessage composeInternal() {
        this.response.init(Outgoing.UserChatBubblesComposer);

        if (this.habbo == null) {
            this.response.appendInt(0);
            this.response.appendInt(0);
            return this.response;
        }

        Set<Integer> ownedBubbles = this.habbo.getInventory().getChatBubblesComponent() != null
                ? this.habbo.getInventory().getChatBubblesComponent().getOwnedBubbles()
                : Set.of();

        int activeBubbleId = this.habbo.getHabboStats().chatColor != null
                ? this.habbo.getHabboStats().chatColor.getType()
                : 0;

        boolean hasAnyChatColor = this.habbo.hasPermission(Permission.ACC_ANYCHATCOLOR);
        boolean hasHc = this.habbo.getHabboStats().hasActiveClub();
        int userRank = this.habbo.getHabboInfo().getRank().getId();

        // 1. Categories
        List<BubbleCategoryData> categories = new ArrayList<>();
        try (Connection connection = Emulator.getDatabase().getDataSource().getConnection();
             PreparedStatement statement = connection.prepareStatement(
                     "SELECT id, name, badge FROM custom_bubble_category WHERE enabled = 1 ORDER BY sort_order ASC, id ASC")) {
            try (ResultSet set = statement.executeQuery()) {
                while (set.next()) {
                    categories.add(new BubbleCategoryData(
                            set.getInt("id"),
                            set.getString("name"),
                            set.getString("badge") != null ? set.getString("badge") : ""));
                }
            }
        } catch (SQLException e) {
            LOGGER.error("Caught SQL exception loading custom_bubble_category", e);
        }

        this.response.appendInt(categories.size());
        for (BubbleCategoryData cat : categories) {
            this.response.appendInt(cat.id);
            this.response.appendString(cat.name);
            this.response.appendString(cat.badge);
        }

        // 2. Chat Bubbles Catalog
        List<BubbleCatalogData> bubbles = new ArrayList<>();
        try (Connection connection = Emulator.getDatabase().getDataSource().getConnection();
             PreparedStatement statement = connection.prepareStatement(
                     "SELECT type, name, category_id, permission, min_rank, is_system, is_free, is_hc_only, is_purchasable, " +
                             "price_credits, price_duckets, price_diamonds " +
                             "FROM chat_bubbles WHERE enabled = 1 AND is_system = 0 ORDER BY category_id ASC, type ASC")) {
            try (ResultSet set = statement.executeQuery()) {
                while (set.next()) {
                    int type = set.getInt("type");
                    String name = set.getString("name");
                    int categoryId = set.getInt("category_id");
                    String permission = set.getString("permission") != null ? set.getString("permission") : "";
                    int minRank = set.getInt("min_rank");
                    boolean isFree = set.getBoolean("is_free");
                    boolean isHcOnly = set.getBoolean("is_hc_only");
                    boolean isPurchasable = set.getBoolean("is_purchasable");
                    int priceCredits = set.getInt("price_credits");
                    int priceDuckets = set.getInt("price_duckets");
                    int priceDiamonds = set.getInt("price_diamonds");

                    // Evaluate if user owns / has access to this bubble
                    boolean isOwned = type == 0
                            || isFree
                            || hasAnyChatColor
                            || ownedBubbles.contains(type)
                            || (minRank > 0 && userRank >= minRank)
                            || (!permission.isEmpty() && this.habbo.hasPermission(permission))
                            || (isHcOnly && hasHc);

                    boolean isActive = (activeBubbleId == type);

                    bubbles.add(new BubbleCatalogData(
                            type,
                            name != null ? name : "Bubble #" + type,
                            categoryId,
                            isFree,
                            isHcOnly,
                            isPurchasable,
                            priceCredits,
                            priceDuckets,
                            priceDiamonds,
                            minRank,
                            permission,
                            isOwned,
                            isActive));
                }
            }
        } catch (SQLException e) {
            LOGGER.error("Caught SQL exception loading chat_bubbles catalog", e);
        }

        this.response.appendInt(bubbles.size());
        for (BubbleCatalogData b : bubbles) {
            this.response.appendInt(b.type);
            this.response.appendString(b.name);
            this.response.appendInt(b.categoryId);
            this.response.appendBoolean(b.isFree);
            this.response.appendBoolean(b.isHcOnly);
            this.response.appendBoolean(b.isPurchasable);
            this.response.appendInt(b.priceCredits);
            this.response.appendInt(b.priceDuckets);
            this.response.appendInt(b.priceDiamonds);
            this.response.appendInt(b.minRank);
            this.response.appendString(b.permission);
            this.response.appendBoolean(b.isOwned);
            this.response.appendBoolean(b.isActive);
        }

        return this.response;
    }

    private static class BubbleCategoryData {
        final int id;
        final String name;
        final String badge;

        BubbleCategoryData(int id, String name, String badge) {
            this.id = id;
            this.name = name;
            this.badge = badge;
        }
    }

    private static class BubbleCatalogData {
        final int type;
        final String name;
        final int categoryId;
        final boolean isFree;
        final boolean isHcOnly;
        final boolean isPurchasable;
        final int priceCredits;
        final int priceDuckets;
        final int priceDiamonds;
        final int minRank;
        final String permission;
        final boolean isOwned;
        final boolean isActive;

        BubbleCatalogData(int type, String name, int categoryId, boolean isFree, boolean isHcOnly,
                          boolean isPurchasable, int priceCredits, int priceDuckets, int priceDiamonds,
                          int minRank, String permission, boolean isOwned, boolean isActive) {
            this.type = type;
            this.name = name;
            this.categoryId = categoryId;
            this.isFree = isFree;
            this.isHcOnly = isHcOnly;
            this.isPurchasable = isPurchasable;
            this.priceCredits = priceCredits;
            this.priceDuckets = priceDuckets;
            this.priceDiamonds = priceDiamonds;
            this.minRank = minRank;
            this.permission = permission;
            this.isOwned = isOwned;
            this.isActive = isActive;
        }
    }
}
