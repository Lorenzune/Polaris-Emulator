package com.eu.habbo.habbohotel.users.infostand;

import com.eu.habbo.Emulator;
import com.eu.habbo.habbohotel.permissions.Permission;
import com.eu.habbo.habbohotel.users.Habbo;
import com.eu.habbo.habbohotel.users.HabboInfo;
import com.eu.habbo.habbohotel.users.HabboStats;
import com.eu.habbo.messages.outgoing.generic.alerts.BubbleAlertComposer;
import com.eu.habbo.messages.outgoing.generic.alerts.BubbleAlertKeys;
import com.eu.habbo.messages.outgoing.users.UserCurrencyComposer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Collections;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.Map;

public class InfostandBackgroundManager {
    private static final Logger LOGGER = LoggerFactory.getLogger(InfostandBackgroundManager.class);

    public enum Category {
        BACKGROUND("background"),
        STAND("stand"),
        OVERLAY("overlay"),
        CARD("card"),
        BORDER("border"),
        POSE("pose"),
        SLOT("slot");

        public final String dbValue;

        Category(String dbValue) {
            this.dbValue = dbValue;
        }

        public static Category fromDbValue(String value) {
            if (value == null) return null;
            if (value.equalsIgnoreCase("slots") || value.equalsIgnoreCase("badge_slots") || value.equalsIgnoreCase("badge_slot")) return SLOT;
            for (Category category : values()) {
                if (category.dbValue.equalsIgnoreCase(value)) return category;
            }
            return null;
        }
    }

    private final Map<Category, Map<Integer, Entry>> entries = new EnumMap<>(Category.class);
    private boolean enforce = false;

    public InfostandBackgroundManager() {
        for (Category category : Category.values()) {
            this.entries.put(category, Collections.emptyMap());
        }

        this.reload();
    }

    public void reload() {
        Map<Category, Map<Integer, Entry>> next = new EnumMap<>(Category.class);
        for (Category category : Category.values()) {
            next.put(category, new HashMap<>());
        }

        int loaded = 0;
        try (Connection connection = Emulator.getDatabase().getDataSource().getConnection()) {
            // First try custom_profile_cosmetics_catalog
            boolean hasCosmeticsTable = true;
            try (PreparedStatement statement = connection.prepareStatement(
                    "SELECT id, category, display_name, cost_credits, cost_points, points_type, min_rank, is_hc_only, is_free, enabled " +
                    "FROM custom_profile_cosmetics_catalog");
                 ResultSet set = statement.executeQuery()) {
                while (set.next()) {
                    Category category = Category.fromDbValue(set.getString("category"));
                    if (category == null) continue;

                    int id = set.getInt("id");
                    String displayName = set.getString("display_name");
                    int costCredits = set.getInt("cost_credits");
                    int costPoints = set.getInt("cost_points");
                    int pointsType = set.getInt("points_type");
                    int minRank = set.getInt("min_rank");
                    boolean isHcOnly = set.getBoolean("is_hc_only");
                    boolean isFree = set.getBoolean("is_free");
                    boolean enabled = set.getBoolean("enabled");

                    next.get(category).put(id, new Entry(minRank, isHcOnly, false, isFree, enabled, displayName, costCredits, costPoints, pointsType));
                    loaded++;
                }
            } catch (SQLException e) {
                hasCosmeticsTable = false;
            }

            // If empty or table didn't exist, fallback to legacy infostand_backgrounds
            if (loaded == 0) {
                try (PreparedStatement statement = connection.prepareStatement(
                        "SELECT id, category, min_rank, is_hc_only, is_ambassador_only FROM infostand_backgrounds");
                     ResultSet set = statement.executeQuery()) {
                    while (set.next()) {
                        Category category = Category.fromDbValue(set.getString("category"));
                        if (category == null) continue;

                        int id = set.getInt("id");
                        int minRank = set.getInt("min_rank");
                        boolean isHcOnly = set.getBoolean("is_hc_only");
                        boolean isAmbassadorOnly = set.getBoolean("is_ambassador_only");

                        next.get(category).put(id, new Entry(minRank, isHcOnly, isAmbassadorOnly, true, true, "Item #" + id, 0, 0, 0));
                        loaded++;
                    }
                } catch (SQLException ignored) {
                }
            }
        } catch (SQLException e) {
            this.enforce = false;
            for (Category category : Category.values()) {
                this.entries.put(category, Collections.emptyMap());
            }
            LOGGER.error("InfostandBackgroundManager -> Failed to load cosmetics catalog, server-side validation disabled.", e);
            return;
        }

        for (Category category : Category.values()) {
            this.entries.put(category, next.get(category));
        }

        this.enforce = loaded > 0;

        if (this.enforce) {
            LOGGER.info(summary(
                    this.entries.get(Category.BACKGROUND).size(),
                    this.entries.get(Category.STAND).size(),
                    this.entries.get(Category.OVERLAY).size(),
                    this.entries.get(Category.CARD).size(),
                    this.entries.get(Category.BORDER).size()));
        } else {
            LOGGER.info("InfostandBackgroundManager -> catalog is empty, server-side validation disabled.");
        }
    }

    public static String summary(int backgrounds, int stands, int overlays, int cards, int borders) {
        int total = backgrounds + stands + overlays + cards + borders;
        return String.format("Infostand Background Manager -> Loaded! (%d assets)", total);
    }

    public Entry getEntry(Category category, int id) {
        Map<Integer, Entry> categoryEntries = this.entries.get(category);
        return categoryEntries != null ? categoryEntries.get(id) : null;
    }

    public boolean canUse(Habbo habbo, Category category, int id) {
        if (id == 0) return true;
        if (!this.enforce) return true;
        if (habbo == null) return false;

        Entry entry = getEntry(category, id);
        if (entry == null || !entry.enabled) return false;

        HabboInfo info = habbo.getHabboInfo();
        int rankId = (info != null && info.getRank() != null) ? info.getRank().getId() : 0;
        HabboStats stats = habbo.getHabboStats();
        boolean hasClub = stats != null && stats.hasActiveClub();

        if (entry.isHcOnly && !hasClub) return false;
        if (entry.isAmbassadorOnly && !habbo.hasPermission(Permission.ACC_AMBASSADOR)) return false;
        if (rankId < entry.minRank) return false;

        if (entry.isFree) return true;

        if (habbo.getInventory() != null && habbo.getInventory().getProfileCosmeticsComponent() != null) {
            return habbo.getInventory().getProfileCosmeticsComponent().hasCosmetic(category.dbValue, id);
        }

        return false;
    }

    public boolean tryPurchaseAndEquip(Habbo habbo, Category category, int id) {
        if (id == 0) return true;
        if (!this.enforce) return true;
        if (habbo == null) return false;

        if (canUse(habbo, category, id)) {
            return true;
        }

        Entry entry = getEntry(category, id);
        if (entry == null || !entry.enabled) return false;

        HabboInfo info = habbo.getHabboInfo();
        int rankId = (info != null && info.getRank() != null) ? info.getRank().getId() : 0;
        HabboStats stats = habbo.getHabboStats();
        boolean hasClub = stats != null && stats.hasActiveClub();

        if (entry.isHcOnly && !hasClub) {
            if (habbo.getClient() != null) {
                habbo.getClient().sendResponse(new BubbleAlertComposer(BubbleAlertKeys.FURNITURE_PLACEMENT_ERROR.key, "Questo elemento richiede l'abbonamento Habbo Club!"));
            }
            return false;
        }

        if (rankId < entry.minRank) {
            if (habbo.getClient() != null) {
                habbo.getClient().sendResponse(new BubbleAlertComposer(BubbleAlertKeys.FURNITURE_PLACEMENT_ERROR.key, "Non hai il rank minimo per questo elemento!"));
            }
            return false;
        }

        // Check currency balance
        if (entry.costCredits > 0 && info.getCredits() < entry.costCredits) {
            if (habbo.getClient() != null) {
                habbo.getClient().sendResponse(new BubbleAlertComposer(BubbleAlertKeys.FURNITURE_PLACEMENT_ERROR.key, "Non hai abbastanza Crediti!"));
            }
            return false;
        }

        if (entry.costPoints > 0) {
            int userPoints = info.getCurrencyAmount(entry.pointsType);
            if (userPoints < entry.costPoints) {
                String currencyName = (entry.pointsType == 5) ? "Diamonds" : "Duckets";
                if (habbo.getClient() != null) {
                    habbo.getClient().sendResponse(new BubbleAlertComposer(BubbleAlertKeys.FURNITURE_PLACEMENT_ERROR.key, "Non hai abbastanza " + currencyName + "!"));
                }
                return false;
            }
        }

        // Deduct currency
        if (entry.costCredits > 0) {
            habbo.giveCredits(-entry.costCredits);
        }
        if (entry.costPoints > 0) {
            habbo.givePoints(entry.pointsType, -entry.costPoints);
        }

        // Add to user inventory
        if (habbo.getInventory() != null && habbo.getInventory().getProfileCosmeticsComponent() != null) {
            habbo.getInventory().getProfileCosmeticsComponent().addCosmetic(category.dbValue, id);
        }

        if (habbo.getClient() != null) {
            habbo.getClient().sendResponse(new UserCurrencyComposer(habbo));
            habbo.getClient().sendResponse(new BubbleAlertComposer(BubbleAlertKeys.FURNITURE_PLACEMENT_ERROR.key, "Hai acquistato: " + entry.displayName + "!"));
        }

        return true;
    }

    public static final class Entry {
        public final int minRank;
        public final boolean isHcOnly;
        public final boolean isAmbassadorOnly;
        public final boolean isFree;
        public final boolean enabled;
        public final String displayName;
        public final int costCredits;
        public final int costPoints;
        public final int pointsType;

        public Entry(int minRank, boolean isHcOnly, boolean isAmbassadorOnly, boolean isFree, boolean enabled, String displayName, int costCredits, int costPoints, int pointsType) {
            this.minRank = minRank;
            this.isHcOnly = isHcOnly;
            this.isAmbassadorOnly = isAmbassadorOnly;
            this.isFree = isFree;
            this.enabled = enabled;
            this.displayName = displayName;
            this.costCredits = costCredits;
            this.costPoints = costPoints;
            this.pointsType = pointsType;
        }

        public Entry(int minRank, boolean isHcOnly, boolean isAmbassadorOnly) {
            this(minRank, isHcOnly, isAmbassadorOnly, true, true, "Item", 0, 0, 0);
        }
    }
}
