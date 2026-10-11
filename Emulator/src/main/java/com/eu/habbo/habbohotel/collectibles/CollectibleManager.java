package com.eu.habbo.habbohotel.collectibles;

import com.eu.habbo.Emulator;
import com.eu.habbo.habbohotel.economy.EconomyLedger;
import com.eu.habbo.habbohotel.economy.EconomyOperation;
import com.eu.habbo.habbohotel.items.FurnitureType;
import com.eu.habbo.habbohotel.users.Habbo;
import com.eu.habbo.habbohotel.users.HabboBadge;
import com.eu.habbo.habbohotel.users.LedgerWalletMutation;
import com.eu.habbo.messages.outgoing.inventory.InventoryBadgesComposer;
import com.eu.habbo.messages.outgoing.inventory.InventoryRefreshComposer;
import com.eu.habbo.messages.outgoing.users.UserCreditsComposer;
import com.eu.habbo.messages.outgoing.users.UserCurrencyComposer;
import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.slf4j.LoggerFactory;

public final class CollectibleManager {
    private static final Gson GSON = new Gson();

    public boolean canEdit(int userId) {
        Habbo user = Emulator.getGameEnvironment().getHabboManager().getHabbo(userId);
        return user != null
                && user.hasPermission(com.eu.habbo.habbohotel.permissions.Permission.ACC_COLLECTIBLES_EDITOR);
    }

    private Connection connection() throws SQLException {
        return Emulator.getDatabase().getDataSource().getConnection();
    }

    private long now(Connection c) throws SQLException {
        try (var s = c.prepareStatement("SELECT UNIX_TIMESTAMP()");
                var rs = s.executeQuery()) {
            rs.next();
            return rs.getLong(1);
        }
    }

    private Set<Integer> ownedItems(Connection c, int userId, boolean lock) throws SQLException {
        var owned = new HashSet<Integer>();
        try (var s = c.prepareStatement("SELECT item_id FROM items WHERE user_id=?" + (lock ? " FOR UPDATE" : ""))) {
            s.setInt(1, userId);
            try (var rs = s.executeQuery()) {
                while (rs.next()) owned.add(rs.getInt(1));
            }
        }
        return owned;
    }

    private Set<Integer> eligibleItems(Connection c, int userId, boolean lock) throws SQLException {
        var eligible = new HashSet<Integer>();
        try (var s = c.prepareStatement(
                "SELECT item_id FROM items WHERE user_id=? AND first_owner_id=? AND ownership_transferred=0"
                        + (lock ? " FOR UPDATE" : ""))) {
            s.setInt(1, userId);
            s.setInt(2, userId);
            try (var rs = s.executeQuery()) {
                while (rs.next()) eligible.add(rs.getInt(1));
            }
        }
        return eligible;
    }

    private boolean ownsBadge(Connection c, int userId, String code) throws SQLException {
        try (var s = c.prepareStatement("SELECT id FROM users_badges WHERE user_id=? AND badge_code=?")) {
            s.setInt(1, userId);
            s.setString(2, code);
            try (var rs = s.executeQuery()) {
                return rs.next();
            }
        }
    }

    private void grantBadge(Connection c, int userId, String code, List<String> badges) throws SQLException {
        if (ownsBadge(c, userId, code)) return;
        try (var s = c.prepareStatement("INSERT INTO users_badges(user_id,badge_code,slot_id) VALUES(?,?,0)")) {
            s.setInt(1, userId);
            s.setString(2, code);
            s.executeUpdate();
        }
        badges.add(code);
    }

    private void refreshBadges(Habbo user, List<String> badges) throws SQLException {
        if (badges.isEmpty()) return;
        try (var c = connection();
                var s = c.prepareStatement("SELECT * FROM users_badges WHERE user_id=?")) {
            s.setInt(1, user.getHabboInfo().getId());
            try (var rs = s.executeQuery()) {
                while (rs.next())
                    if (badges.contains(rs.getString("badge_code")))
                        user.getInventory().getBadgesComponent().addBadge(new HabboBadge(rs, user));
            }
        }
        user.getClient().sendResponse(new InventoryBadgesComposer(user));
    }

    private List<Integer> categoryItems(String json) {
        return Arrays.asList(GSON.fromJson(json, Integer[].class));
    }

    public JsonObject state(int userId) throws SQLException {
        JsonObject result = new JsonObject();
        JsonArray rewards = new JsonArray(),
                owned = new JsonArray(),
                admin = new JsonArray(),
                categories = new JsonArray();
        boolean edit = canEdit(userId);
        long xp = 0, high = 0;
        try (var c = connection()) {
            long now = now(c);
            try (var stmt = c.prepareStatement("SELECT available_at FROM collectible_shop_cooldowns WHERE user_id=?")) {
                stmt.setInt(1, userId);
                try (var rs = stmt.executeQuery()) {
                    result.addProperty("nextPurchaseAt", rs.next() ? rs.getLong(1) : 0);
                }
            }
            try (var s = c.prepareStatement("SELECT xp,highest_xp FROM collectible_profiles WHERE user_id=?")) {
                s.setInt(1, userId);
                try (var rs = s.executeQuery()) {
                    if (rs.next()) {
                        xp = rs.getLong(1);
                        high = rs.getLong(2);
                    }
                }
            }
            var ownedIds = ownedItems(c, userId, false);
            var eligibleIds = eligibleItems(c, userId, false);
            var categoryByReward = new HashMap<Integer, JsonObject>();
            try (var s = c.prepareStatement(
                            "SELECT * FROM collectible_categories WHERE reward_id IN (SELECT id FROM collectible_rewards WHERE deleted=0) ORDER BY id");
                    var rs = s.executeQuery()) {
                while (rs.next()) {
                    var category = new JsonObject();
                    category.addProperty("id", rs.getInt("id"));
                    category.addProperty("title", rs.getString("title"));
                    var ids = categoryItems(rs.getString("item_ids"));
                    var entries = new JsonArray();
                    int count = 0, eligibleCount = 0;
                    for (int id : ids) {
                        var entry = new JsonObject();
                        entry.addProperty("itemId", id);
                        entry.addProperty("owned", ownedIds.contains(id));
                        entry.addProperty("eligible", eligibleIds.contains(id));
                        if (eligibleIds.contains(id)) eligibleCount++;
                        if (ownedIds.contains(id)) count++;
                        var base =
                                Emulator.getGameEnvironment().getItemManager().getItem(id);
                        if (base != null) {
                            entry.addProperty("spriteId", base.getSpriteId());
                            entry.addProperty("productType", base.getType().code.toLowerCase());
                            entry.addProperty("name", base.getName());
                        }
                        entries.add(entry);
                    }
                    category.add("items", entries);
                    category.addProperty("ownedCount", count);
                    category.addProperty("complete", count == ids.size());
                    category.addProperty("eligibleCount", eligibleCount);
                    category.addProperty("rewardComplete", eligibleCount == ids.size());
                    categoryByReward.put(rs.getInt("reward_id"), category);
                    categories.add(category);
                }
            }
            try (var s = c.prepareStatement(
                    "SELECT r.*,c.claimed_at FROM collectible_rewards r LEFT JOIN collectible_claims c ON c.reward_id=r.id AND c.user_id=? WHERE r.deleted=0 ORDER BY r.id DESC")) {
                s.setInt(1, userId);
                try (var rs = s.executeQuery()) {
                    while (rs.next()) {
                        CollectibleReward reward =
                                GSON.fromJson(rs.getString("configuration"), CollectibleReward.class);
                        boolean claimed = rs.getLong("claimed_at") > 0, enabled = rs.getBoolean("enabled");
                        JsonObject row = GSON.toJsonTree(reward).getAsJsonObject();
                        row.addProperty("id", rs.getInt("id"));
                        row.addProperty("enabled", enabled);
                        row.addProperty("active", enabled && (reward.expiresAt() == 0 || now < reward.expiresAt()));
                        row.addProperty("claimed", claimed);
                        if ("item".equals(reward.kind())) {
                            var base = Emulator.getGameEnvironment()
                                    .getItemManager()
                                    .getItem(reward.itemId());
                            if (base != null) {
                                row.addProperty("spriteId", base.getSpriteId());
                                row.addProperty(
                                        "productType", base.getType().code.toLowerCase());
                            }
                        }
                        var category = categoryByReward.get(rs.getInt("id"));
                        if (category != null) {
                            category.add("reward", row.deepCopy());
                            category.addProperty(
                                    "claimable",
                                    category.get("rewardComplete").getAsBoolean()
                                            && reward.availableTo(
                                                    userId, CollectibleReward.level(xp), now, enabled, claimed));
                        } else {
                            if (reward.availableTo(userId, CollectibleReward.level(xp), now, enabled, claimed))
                                rewards.add(row.deepCopy());
                            if (edit) admin.add(row.deepCopy());
                        }
                    }
                }
            }
            if (!edit) {
                var visibleCategories = new JsonArray();
                for (var entry : categories) {
                    var category = entry.getAsJsonObject();
                    if (category.has("reward")
                            && category.getAsJsonObject("reward").get("enabled").getAsBoolean())
                        visibleCategories.add(category);
                }
                categories = visibleCategories;
            }
            result.addProperty("serverTime", now);
            var shop = new JsonArray();
            try (var s = c.prepareStatement("SELECT * FROM collectible_shop WHERE deleted=0 ORDER BY id");
                    var rs = s.executeQuery()) {
                while (rs.next()) {
                    if (!edit && !rs.getBoolean("enabled")) continue;
                    var row = new JsonObject();
                    row.addProperty("enabled", rs.getBoolean("enabled"));
                    row.addProperty("quantity", rs.getInt("quantity"));
                    row.addProperty("sold", rs.getInt("sold"));
                    row.addProperty("remaining", Math.max(0, rs.getInt("quantity") - rs.getInt("sold")));
                    row.addProperty("soldOut", rs.getInt("sold") >= rs.getInt("quantity"));
                    var base = Emulator.getGameEnvironment().getItemManager().getItem(rs.getInt("item_id"));
                    if (base == null && !"badge".equals(rs.getString("category"))) continue;
                    row.addProperty("id", rs.getInt("id"));
                    row.addProperty("category", rs.getString("category"));
                    row.addProperty("title", rs.getString("title"));
                    row.addProperty("badgeCode", rs.getString("badge_code"));
                    row.addProperty(
                            "owned",
                            "badge".equals(rs.getString("category"))
                                    && ownsBadge(c, userId, rs.getString("badge_code")));
                    row.addProperty("itemId", base == null ? 0 : base.getId());
                    row.addProperty("spriteId", base == null ? 0 : base.getSpriteId());
                    row.addProperty(
                            "productType",
                            base == null ? "badge" : base.getType().code.toLowerCase());
                    row.addProperty("price", rs.getInt("price"));
                    row.addProperty("currencyType", rs.getInt("currency_type"));
                    shop.add(row);
                }
            }
            result.add("shop", shop);
        }
        result.addProperty("canEdit", edit);
        result.addProperty("xp", xp);
        result.addProperty("highestXp", high);
        result.addProperty("level", CollectibleReward.level(xp));
        result.addProperty("nextLevelXp", CollectibleReward.nextLevelXp(xp));
        result.add("rewards", rewards);
        result.add("collection", owned);
        result.add("definitions", admin);
        result.add("categories", categories);
        return result;
    }

    public JsonObject saveShop(int userId, JsonObject body) throws SQLException {
        if (!canEdit(userId)) throw new SecurityException("Permessi insufficienti.");
        String category = body.get("category").getAsString(),
                title = body.get("title").getAsString();
        int quantity = body.has("quantity") ? body.get("quantity").getAsInt() : 1;
        String badgeCode = body.has("badgeCode") ? body.get("badgeCode").getAsString() : "";
        int itemId = "badge".equals(category) ? 0 : body.get("itemId").getAsInt(),
                price = body.get("price").getAsInt(),
                currency = body.get("currencyType").getAsInt();
        if (!List.of("clothes", "furni", "badge").contains(category)
                || title.isBlank()
                || title.length() > 100
                || quantity < 1
                || quantity > 1000000
                || price < 1
                || !List.of(-1, 0, 5, 105).contains(currency))
            throw new IllegalArgumentException("Articolo o prezzo non valido.");
        var base = Emulator.getGameEnvironment().getItemManager().getItem(itemId);
        if ("badge".equals(category) && !CollectibleReward.validBadgeCode(badgeCode))
            throw new IllegalArgumentException("Codice badge non valido.");
        if (!"badge".equals(category)
                && (base == null || (base.getType() != FurnitureType.FLOOR && base.getType() != FurnitureType.WALL)))
            throw new IllegalArgumentException("Furni inesistente.");
        try (var c = connection();
                var s = c.prepareStatement(
                        body.has("id")
                                ? "UPDATE collectible_shop SET category=?,title=?,item_id=?,price=?,currency_type=?,quantity=?,badge_code=? WHERE id=? AND deleted=0"
                                : "INSERT INTO collectible_shop(category,title,item_id,price,currency_type,quantity,badge_code) VALUES(?,?,?,?,?,?,?)")) {
            s.setString(1, category);
            s.setString(2, title);
            s.setInt(3, itemId);
            s.setInt(4, price);
            s.setInt(5, currency);
            s.setInt(6, quantity);
            s.setString(7, "badge".equals(category) ? badgeCode : "");
            if (body.has("id")) s.setInt(8, body.get("id").getAsInt());
            if (s.executeUpdate() != 1) throw new IllegalArgumentException("Offerta inesistente.");
        }
        return state(userId);
    }

    public JsonObject disableShop(int userId, int id) throws SQLException {
        if (!canEdit(userId)) throw new SecurityException("Permessi insufficienti.");
        try (var c = connection();
                var s = c.prepareStatement("UPDATE collectible_shop SET enabled=0 WHERE id=?")) {
            s.setInt(1, id);
            s.executeUpdate();
        }
        return state(userId);
    }

    public JsonObject buyShop(int userId, int id) throws SQLException {
        var user = Emulator.getGameEnvironment().getHabboManager().getHabbo(userId);
        if (user == null || user.getClient() == null)
            throw new IllegalArgumentException("Accedi al client per acquistare.");
        return LedgerWalletMutation.coordinated(user, () -> buyShopLocked(user, id));
    }

    private JsonObject buyShopLocked(Habbo user, int id) throws SQLException {
        int userId = user.getHabboInfo().getId();
        var deliveredItems = new ArrayList<Integer>();
        var deliveredBadges = new ArrayList<String>();
        int currency, balance;
        try (var c = connection()) {
            c.setAutoCommit(false);
            try {
                try (var s = c.prepareStatement("SELECT id FROM users WHERE id=? FOR UPDATE")) {
                    s.setInt(1, userId);
                    try (var rs = s.executeQuery()) {
                        if (!rs.next()) throw new SQLException("User missing");
                    }
                }
                long purchaseTime = now(c);
                try (var stmt = c.prepareStatement(
                        "SELECT available_at FROM collectible_shop_cooldowns WHERE user_id=? FOR UPDATE")) {
                    stmt.setInt(1, userId);
                    try (var rs = stmt.executeQuery()) {
                        if (rs.next() && rs.getLong(1) > purchaseTime)
                            throw new IllegalArgumentException("Attendi 20 secondi tra un acquisto e il successivo.");
                    }
                }
                int baseId, price, quantity, sold;
                String shopCategory, badgeCode;
                try (var s = c.prepareStatement(
                        "SELECT item_id,price,currency_type,quantity,sold,category,badge_code FROM collectible_shop WHERE id=? AND enabled=1 AND deleted=0 FOR UPDATE")) {
                    s.setInt(1, id);
                    try (var rs = s.executeQuery()) {
                        if (!rs.next()) throw new IllegalArgumentException("Articolo non disponibile.");
                        baseId = rs.getInt(1);
                        price = rs.getInt(2);
                        currency = rs.getInt(3);
                        quantity = rs.getInt(4);
                        sold = rs.getInt(5);
                        shopCategory = rs.getString(6);
                        badgeCode = rs.getString(7);
                    }
                }
                if (quantity < 1
                        || quantity > 1000000
                        || price < 1
                        || !List.of(-1, 0, 5, 105).contains(currency)
                        || (!"badge".equals(shopCategory)
                                && Emulator.getGameEnvironment()
                                                .getItemManager()
                                                .getItem(baseId)
                                        == null)
                        || ("badge".equals(shopCategory) && !CollectibleReward.validBadgeCode(badgeCode)))
                    throw new IllegalArgumentException("Articolo non disponibile.");
                if ("badge".equals(shopCategory) && ownsBadge(c, userId, badgeCode))
                    throw new IllegalArgumentException("Possiedi già questo badge.");
                if (sold >= quantity) throw new IllegalArgumentException("Sold out: articolo esaurito.");
                try (var s = c.prepareStatement(
                        "UPDATE collectible_shop SET sold=sold+1 WHERE id=? AND sold<quantity AND enabled=1 AND deleted=0")) {
                    s.setInt(1, id);
                    if (s.executeUpdate() != 1) throw new IllegalArgumentException("Sold out: articolo esaurito.");
                }
                balance = EconomyLedger.apply(
                                c,
                                new EconomyOperation(
                                        "collectible-shop:" + UUID.randomUUID(),
                                        userId,
                                        userId,
                                        "collectible_shop",
                                        "collectibles",
                                        currency,
                                        -price,
                                        null,
                                        "shop:" + id))
                        .balanceAfter();
                if ("badge".equals(shopCategory)) grantBadge(c, userId, badgeCode, deliveredBadges);
                else
                    try (var s = c.prepareStatement(
                            "INSERT INTO items(user_id,item_id,extra_data,limited_data) VALUES(?,?,'','0:0')",
                            Statement.RETURN_GENERATED_KEYS)) {
                        s.setInt(1, userId);
                        s.setInt(2, baseId);
                        s.executeUpdate();
                        try (var rs = s.getGeneratedKeys()) {
                            if (!rs.next()) throw new SQLException("Missing item id");
                            deliveredItems.add(rs.getInt(1));
                        }
                    }
                try (var stmt = c.prepareStatement(
                        "INSERT INTO collectible_shop_cooldowns(user_id,available_at) VALUES(?,?) ON DUPLICATE KEY UPDATE available_at=VALUES(available_at)")) {
                    stmt.setInt(1, userId);
                    stmt.setLong(2, purchaseTime + 20);
                    stmt.executeUpdate();
                }
                c.commit();
            } catch (SQLException | RuntimeException e) {
                c.rollback();
                throw e;
            }
        }
        try {
            LedgerWalletMutation.applyCommitted(user, currency, balance);
            for (int item : deliveredItems) {
                var delivered = Emulator.getGameEnvironment().getItemManager().loadHabboItem(item);
                if (delivered != null) user.getInventory().getItemsComponent().addItem(delivered);
            }
            user.getClient().sendResponse(new InventoryRefreshComposer());
            user.getClient().sendResponse(new UserCreditsComposer(user));
            user.getClient().sendResponse(new UserCurrencyComposer(user));
            refreshBadges(user, deliveredBadges);
        } catch (Exception e) {
            LoggerFactory.getLogger(CollectibleManager.class)
                    .error("Collectible purchase committed; client refresh failed", e);
        }
        return state(userId);
    }

    public JsonObject createCategory(int userId, JsonObject body) throws SQLException {
        if (!canEdit(userId)) throw new SecurityException("Permessi insufficienti.");
        var category = new CollectibleCategory(
                body.get("categoryTitle").getAsString(),
                categoryItems(body.get("itemIds").toString()));
        category.validate();
        var reward = GSON.fromJson(body, CollectibleReward.class);
        if (!reward.recipients().isEmpty() || reward.requiredLevel() != null)
            throw new IllegalArgumentException(
                    "Il premio categoria richiede tutti gli articoli, senza altri destinatari.");
        try (var c = connection()) {
            long now = now(c);
            reward.validate(now);
            var all = new ArrayList<>(category.itemIds());
            if ("item".equals(reward.kind())) all.add(reward.itemId());
            for (int id : all) {
                var base = Emulator.getGameEnvironment().getItemManager().getItem(id);
                if (base == null || (base.getType() != FurnitureType.FLOOR && base.getType() != FurnitureType.WALL))
                    throw new IllegalArgumentException("Articolo non valido: " + id);
            }
            c.setAutoCommit(false);
            try {
                int rewardId;
                try (var s = c.prepareStatement(
                        body.has("id")
                                ? "UPDATE collectible_rewards SET configuration=?,expires_at=?,created_by=?,created_at=? WHERE id=? AND deleted=0 AND id IN (SELECT reward_id FROM collectible_categories)"
                                : "INSERT INTO collectible_rewards(configuration,expires_at,created_by,created_at) VALUES(?,?,?,?)",
                        Statement.RETURN_GENERATED_KEYS)) {
                    s.setString(1, GSON.toJson(reward));
                    s.setLong(2, reward.expiresAt());
                    s.setInt(3, userId);
                    s.setLong(4, now);
                    if (body.has("id")) s.setInt(5, body.get("id").getAsInt());
                    if (s.executeUpdate() != 1) throw new IllegalArgumentException("Premio inesistente.");
                    if (body.has("id")) rewardId = body.get("id").getAsInt();
                    else
                        try (var rs = s.getGeneratedKeys()) {
                            if (!rs.next()) throw new SQLException("Missing reward id");
                            rewardId = rs.getInt(1);
                        }
                }
                try (var s = c.prepareStatement(
                        body.has("id")
                                ? "UPDATE collectible_categories SET title=?,item_ids=? WHERE reward_id=?"
                                : "INSERT INTO collectible_categories(title,item_ids,reward_id) VALUES(?,?,?)")) {
                    s.setString(1, category.title());
                    s.setString(2, GSON.toJson(category.itemIds()));
                    s.setInt(3, rewardId);
                    s.executeUpdate();
                }
                c.commit();
            } catch (SQLException | RuntimeException error) {
                c.rollback();
                throw error;
            }
        }
        return state(userId);
    }

    public JsonObject create(int userId, JsonObject body) throws SQLException {
        if (!canEdit(userId)) throw new SecurityException("Permessi insufficienti.");
        CollectibleReward reward = GSON.fromJson(body, CollectibleReward.class);
        try (var c = connection()) {
            long now = now(c);
            reward.validate(now);
            if ("item".equals(reward.kind())) {
                var base = Emulator.getGameEnvironment().getItemManager().getItem(reward.itemId());
                if (base == null || (base.getType() != FurnitureType.FLOOR && base.getType() != FurnitureType.WALL))
                    throw new IllegalArgumentException("L'articolo deve essere un furni da pavimento o da parete.");
            }
            for (int id : reward.recipients())
                try (var s = c.prepareStatement("SELECT id FROM users WHERE id=?")) {
                    s.setInt(1, id);
                    try (var rs = s.executeQuery()) {
                        if (!rs.next()) throw new IllegalArgumentException("Utente inesistente: " + id);
                    }
                }
            try (var s = c.prepareStatement(
                    body.has("id")
                            ? "UPDATE collectible_rewards SET configuration=?,expires_at=?,created_by=?,created_at=? WHERE id=? AND deleted=0 AND id NOT IN (SELECT reward_id FROM collectible_categories)"
                            : "INSERT INTO collectible_rewards(configuration,expires_at,created_by,created_at) VALUES(?,?,?,?)")) {
                s.setString(1, GSON.toJson(reward));
                s.setLong(2, reward.expiresAt());
                s.setInt(3, userId);
                s.setLong(4, now);
                if (body.has("id")) s.setInt(5, body.get("id").getAsInt());
                if (s.executeUpdate() != 1) throw new IllegalArgumentException("Premio inesistente.");
            }
        }
        return state(userId);
    }

    public JsonObject manage(int userId, JsonObject body) throws SQLException {
        if (!canEdit(userId)) throw new SecurityException("Permessi insufficienti.");
        boolean shop = body.has("shop") && body.get("shop").getAsBoolean();
        boolean delete = body.has("delete") && body.get("delete").getAsBoolean();
        String table = shop ? "collectible_shop" : "collectible_rewards";
        try (var c = connection();
                var s = c.prepareStatement("UPDATE " + table + (delete ? " SET deleted=1,enabled=0" : " SET enabled=?")
                        + " WHERE id=? AND deleted=0")) {
            int index = 1;
            if (!delete) s.setBoolean(index++, body.get("visible").getAsBoolean());
            s.setInt(index, body.get("id").getAsInt());
            s.executeUpdate();
        }
        return state(userId);
    }

    public JsonObject disable(int userId, int id) throws SQLException {
        if (!canEdit(userId)) throw new SecurityException("Permessi insufficienti.");
        try (var c = connection();
                var s = c.prepareStatement("UPDATE collectible_rewards SET enabled=0 WHERE id=?")) {
            s.setInt(1, id);
            s.executeUpdate();
        }
        return state(userId);
    }

    public JsonObject claim(int userId, List<Integer> ids) throws SQLException {
        if (ids == null || ids.isEmpty() || ids.size() > 100 || ids.stream().anyMatch(id -> id == null || id <= 0))
            throw new IllegalArgumentException("Seleziona da 1 a 100 premi.");
        Habbo user = Emulator.getGameEnvironment().getHabboManager().getHabbo(userId);
        if (user == null || user.getClient() == null)
            throw new IllegalArgumentException("Accedi al client per riscattare.");
        return LedgerWalletMutation.coordinated(
                user, () -> claimLocked(user, ids.stream().distinct().sorted().toList()));
    }

    private JsonObject claimLocked(Habbo user, List<Integer> ids) throws SQLException {
        int userId = user.getHabboInfo().getId();
        var items = new ArrayList<Integer>();
        var badges = new ArrayList<String>();
        var balances = new HashMap<Integer, Integer>();
        try (var c = connection()) {
            c.setAutoCommit(false);
            try {
                try (var s = c.prepareStatement("SELECT id FROM users WHERE id=? FOR UPDATE")) {
                    s.setInt(1, userId);
                    try (var rs = s.executeQuery()) {
                        if (!rs.next()) throw new SQLException("User missing");
                    }
                }
                long totalXp = 0, currentXp = 0;
                try (var s = c.prepareStatement("SELECT xp FROM collectible_profiles WHERE user_id=? FOR UPDATE")) {
                    s.setInt(1, userId);
                    try (var rs = s.executeQuery()) {
                        if (rs.next()) currentXp = rs.getLong(1);
                    }
                }
                int userLevel = CollectibleReward.level(currentXp);
                for (int id : ids) {
                    CollectibleReward reward;
                    try (var s = c.prepareStatement(
                            "SELECT r.configuration,r.enabled,c.claimed_at FROM collectible_rewards r LEFT JOIN collectible_claims c ON c.reward_id=r.id AND c.user_id=? WHERE r.id=? FOR UPDATE")) {
                        s.setInt(1, userId);
                        s.setInt(2, id);
                        try (var rs = s.executeQuery()) {
                            if (!rs.next()) throw new IllegalArgumentException("Premio inesistente.");
                            reward = GSON.fromJson(rs.getString(1), CollectibleReward.class);
                            // Use server time after acquiring the lock, including concurrent deactivation.
                            if (!reward.availableTo(userId, userLevel, now(c), rs.getBoolean(2), rs.getLong(3) > 0))
                                throw new IllegalArgumentException(
                                        "Un premio non è disponibile per il tuo livello, è scaduto, disattivato o già riscattato. Aggiorna il pannello.");
                        }
                    }
                    try (var s = c.prepareStatement(
                            "SELECT title,item_ids FROM collectible_categories WHERE reward_id=? FOR UPDATE")) {
                        s.setInt(1, id);
                        try (var rs = s.executeQuery()) {
                            if (rs.next()
                                    && !new CollectibleCategory(rs.getString(1), categoryItems(rs.getString(2)))
                                            .complete(eligibleItems(c, userId, true)))
                                throw new IllegalArgumentException(
                                        "Per riscattare il premio devi possedere tutti gli articoli come primo proprietario, senza trasferimenti precedenti.");
                        }
                    }
                    if ("currency".equals(reward.kind())) {
                        var mutation = EconomyLedger.apply(
                                c,
                                new EconomyOperation(
                                        "collectible:" + userId + ":" + id,
                                        userId,
                                        userId,
                                        "collectible_reward",
                                        "collectibles",
                                        reward.currencyType(),
                                        reward.amount(),
                                        null,
                                        "collectible:" + id));
                        balances.put(reward.currencyType(), mutation.balanceAfter());
                    } else if ("badge".equals(reward.kind())) {
                        if (!CollectibleReward.validBadgeCode(reward.badgeCode()))
                            throw new IllegalArgumentException("Codice badge non valido.");
                        grantBadge(c, userId, reward.badgeCode(), badges);
                    } else {
                        var base =
                                Emulator.getGameEnvironment().getItemManager().getItem(reward.itemId());
                        if (base == null) throw new IllegalArgumentException("Articolo non disponibile.");
                        for (int i = 0; i < reward.amount(); i++)
                            try (var s = c.prepareStatement(
                                    "INSERT INTO items(user_id,item_id,extra_data,limited_data) VALUES(?,?,?,'0:0')",
                                    Statement.RETURN_GENERATED_KEYS)) {
                                s.setInt(1, userId);
                                s.setInt(2, base.getId());
                                s.setString(3, "");
                                s.executeUpdate();
                                try (var rs = s.getGeneratedKeys()) {
                                    if (!rs.next()) throw new SQLException("Missing item id");
                                    items.add(rs.getInt(1));
                                }
                            }
                    }
                    try (var s = c.prepareStatement(
                            "INSERT INTO collectible_claims(user_id,reward_id,claimed_at) VALUES(?,?,?)")) {
                        s.setInt(1, userId);
                        s.setInt(2, id);
                        s.setLong(3, now(c));
                        s.executeUpdate();
                    }
                    totalXp += reward.xp();
                }
                try (var s = c.prepareStatement(
                        "INSERT INTO collectible_profiles(user_id,xp,highest_xp) VALUES(?,?,?) ON DUPLICATE KEY UPDATE highest_xp=GREATEST(highest_xp,xp+VALUES(xp)),xp=xp+VALUES(xp)")) {
                    s.setInt(1, userId);
                    s.setLong(2, totalXp);
                    s.setLong(3, totalXp);
                    s.executeUpdate();
                }
                c.commit();
            } catch (SQLException | RuntimeException error) {
                c.rollback();
                throw error;
            }
        }
        try {
            balances.forEach((type, balance) -> LedgerWalletMutation.applyCommitted(user, type, balance));
            for (int id : items) {
                var item = Emulator.getGameEnvironment().getItemManager().loadHabboItem(id);
                if (item != null) user.getInventory().getItemsComponent().addItem(item);
            }
            if (!items.isEmpty()) user.getClient().sendResponse(new InventoryRefreshComposer());
            if (!balances.isEmpty()) {
                user.getClient().sendResponse(new UserCreditsComposer(user));
                user.getClient().sendResponse(new UserCurrencyComposer(user));
            }
            refreshBadges(user, badges);
        } catch (Exception error) {
            LoggerFactory.getLogger(CollectibleManager.class)
                    .error("Collectible claim committed; client refresh failed", error);
        }
        return state(userId);
    }
}
