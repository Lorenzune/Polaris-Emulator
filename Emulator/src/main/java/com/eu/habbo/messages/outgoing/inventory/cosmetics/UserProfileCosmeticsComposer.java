package com.eu.habbo.messages.outgoing.inventory.cosmetics;

import com.eu.habbo.Emulator;
import com.eu.habbo.habbohotel.users.Habbo;
import com.eu.habbo.habbohotel.users.HabboInfo;
import com.eu.habbo.messages.ServerMessage;
import com.eu.habbo.messages.outgoing.MessageComposer;
import com.eu.habbo.messages.outgoing.Outgoing;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class UserProfileCosmeticsComposer extends MessageComposer {
    private static final Logger LOGGER = LoggerFactory.getLogger(UserProfileCosmeticsComposer.class);

    private final Habbo habbo;

    public UserProfileCosmeticsComposer(Habbo habbo) {
        this.habbo = habbo;
    }

    @Override
    protected ServerMessage composeInternal() {
        this.response.init(Outgoing.UserProfileCosmeticsComposer);

        if (this.habbo == null || this.habbo.getHabboInfo() == null) {
            this.response.appendInt(0);
            this.response.appendInt(0);
            this.response.appendInt(0);
            this.response.appendBoolean(false);
            this.response.appendInt(2);
            this.response.appendInt(2);
            this.response.appendString("std");
            this.response.appendInt(2);
            this.response.appendInt(2);
            this.response.appendString("std");
            this.response.appendInt(0);
            this.response.appendInt(0);
            return this.response;
        }

        HabboInfo info = this.habbo.getHabboInfo();
        this.response.appendInt(info.getInfostandBg());
        this.response.appendInt(info.getInfostandStand());
        this.response.appendInt(info.getInfostandBorder());
        boolean hasSlotCosmetic = this.habbo.getInventory() != null
                && this.habbo.getInventory().getProfileCosmeticsComponent() != null
                && this.habbo.getInventory().getProfileCosmeticsComponent().hasAnyCosmetic("slot");
        this.response.appendBoolean(hasSlotCosmetic
                && this.habbo.getHabboStats() != null
                && this.habbo.getHabboStats().isBadgeSlotsActive());
        this.response.appendInt(info.getInfostandDirection());
        this.response.appendInt(info.getInfostandHeadDirection());
        this.response.appendString(info.getInfostandAction());
        this.response.appendInt(info.getProfileDirection());
        this.response.appendInt(info.getProfileHeadDirection());
        this.response.appendString(info.getProfileAction());

        Set<String> ownedCosmetics = null;
        if (this.habbo.getInventory() != null && this.habbo.getInventory().getProfileCosmeticsComponent() != null) {
            ownedCosmetics =
                    this.habbo.getInventory().getProfileCosmeticsComponent().getOwnedCosmetics();
        }

        List<CosmeticCategory> categories = new ArrayList<>();
        List<CosmeticItem> items = new ArrayList<>();

        try (Connection connection = Emulator.getDatabase().getDataSource().getConnection()) {
            // 1. Categories
            try (PreparedStatement statement = connection.prepareStatement(
                            "SELECT id, name, category_type, badge, sort_order FROM custom_profile_cosmetic_categories WHERE enabled = 1 AND LOWER(category_type) <> 'card' ORDER BY sort_order ASC, id ASC");
                    ResultSet set = statement.executeQuery()) {
                while (set.next()) {
                    categories.add(new CosmeticCategory(
                            set.getInt("id"),
                            set.getString("name"),
                            set.getString("category_type"),
                            set.getString("badge") != null ? set.getString("badge") : "",
                            set.getInt("sort_order")));
                }
            } catch (SQLException e) {
                LOGGER.warn("custom_profile_cosmetic_categories table query failed", e);
            }

            // 2. Catalog Items
            try (PreparedStatement statement = connection.prepareStatement(
                            "SELECT id, category_id, display_name, category, image_file, cost_credits, cost_points, points_type, is_free, is_hc_only, min_rank, sort_order "
                                    + "FROM custom_profile_cosmetics_catalog WHERE enabled = 1 AND LOWER(category) <> 'card' ORDER BY sort_order ASC, id ASC");
                    ResultSet set = statement.executeQuery()) {
                while (set.next()) {
                    int id = set.getInt("id");
                    int itemId = id;
                    int categoryId = set.getInt("category_id");
                    String name = set.getString("display_name");
                    String type = set.getString("category").toLowerCase();
                    String file = set.getString("image_file");
                    int credits = set.getInt("cost_credits");
                    int points = set.getInt("cost_points");
                    int pointsType = set.getInt("points_type");
                    boolean isFree = set.getBoolean("is_free") || (credits == 0 && points == 0);
                    boolean isHcOnly = set.getBoolean("is_hc_only");
                    int minRank = set.getInt("min_rank");
                    int sortOrder = set.getInt("sort_order");

                    boolean owned = isFree
                            || (itemId == 0)
                            || (ownedCosmetics != null && ownedCosmetics.contains(type + ":" + itemId));
                    boolean active = false;
                    if (type.equals("background") || type.equals("backgrounds")) {
                        active = (info.getInfostandBg() == itemId);
                    } else if (type.equals("stand") || type.equals("stands")) {
                        active = (info.getInfostandStand() == itemId);
                    } else if (type.equals("card") || type.equals("cards")) {
                        active = (info.getInfostandCardBg() == itemId);
                    } else if (type.equals("border") || type.equals("borders")) {
                        active = (info.getInfostandBorder() == itemId);
                    }

                    items.add(new CosmeticItem(
                            id,
                            itemId,
                            categoryId,
                            name,
                            type,
                            file,
                            credits,
                            points,
                            pointsType,
                            isFree,
                            isHcOnly,
                            minRank,
                            sortOrder,
                            owned,
                            active,
                            id));
                }
            } catch (SQLException e) {
                LOGGER.warn("custom_profile_cosmetics_catalog table query failed", e);
            }
        } catch (SQLException e) {
            LOGGER.error("Caught SQL exception in UserProfileCosmeticsComposer", e);
        }

        // Serialize Categories
        this.response.appendInt(categories.size());
        for (CosmeticCategory cat : categories) {
            this.response.appendInt(cat.id);
            this.response.appendString(cat.name);
            this.response.appendString(cat.type);
            this.response.appendString(cat.badge);
            this.response.appendInt(cat.sortOrder);
        }

        // Serialize Items
        this.response.appendInt(items.size());
        for (CosmeticItem item : items) {
            this.response.appendInt(item.id);
            this.response.appendInt(item.itemId);
            this.response.appendInt(item.categoryId);
            this.response.appendString(item.name != null ? item.name : "");
            this.response.appendString(item.type != null ? item.type : "");
            this.response.appendString(item.file != null ? item.file : "");
            this.response.appendInt(item.credits);
            this.response.appendInt(item.points);
            this.response.appendInt(item.pointsType);
            this.response.appendBoolean(item.isFree);
            this.response.appendBoolean(item.isHcOnly);
            this.response.appendInt(item.minRank);
            this.response.appendInt(item.sortOrder);
            this.response.appendBoolean(item.owned);
            this.response.appendBoolean(item.active);
            this.response.appendInt(item.ownedId);
        }

        return this.response;
    }

    private static class CosmeticCategory {
        final int id;
        final String name;
        final String type;
        final String badge;
        final int sortOrder;

        CosmeticCategory(int id, String name, String type, String badge, int sortOrder) {
            this.id = id;
            this.name = name;
            this.type = type;
            this.badge = badge;
            this.sortOrder = sortOrder;
        }
    }

    private static class CosmeticItem {
        final int id;
        final int itemId;
        final int categoryId;
        final String name;
        final String type;
        final String file;
        final int credits;
        final int points;
        final int pointsType;
        final boolean isFree;
        final boolean isHcOnly;
        final int minRank;
        final int sortOrder;
        final boolean owned;
        final boolean active;
        final int ownedId;

        CosmeticItem(
                int id,
                int itemId,
                int categoryId,
                String name,
                String type,
                String file,
                int credits,
                int points,
                int pointsType,
                boolean isFree,
                boolean isHcOnly,
                int minRank,
                int sortOrder,
                boolean owned,
                boolean active,
                int ownedId) {
            this.id = id;
            this.itemId = itemId;
            this.categoryId = categoryId;
            this.name = name;
            this.type = type;
            this.file = file;
            this.credits = credits;
            this.points = points;
            this.pointsType = pointsType;
            this.isFree = isFree;
            this.isHcOnly = isHcOnly;
            this.minRank = minRank;
            this.sortOrder = sortOrder;
            this.owned = owned;
            this.active = active;
            this.ownedId = ownedId;
        }
    }
}
