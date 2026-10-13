package com.eu.habbo.habbohotel.catalog;

import com.eu.habbo.Emulator;
import com.eu.habbo.habbohotel.collectibles.CollectibleReward;
import com.eu.habbo.habbohotel.permissions.Permission;
import com.eu.habbo.habbohotel.users.Habbo;
import com.google.gson.Gson;
import com.google.gson.JsonObject;
import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import javax.sql.DataSource;

/** All entry points require the dedicated resolved rank permission, including reads. */
public final class RareManagerService {
    private static final Gson GSON = new Gson();
    private static final AtomicBoolean catalogReloadRunning = new AtomicBoolean();
    private static final AtomicLong catalogRevision = new AtomicLong();
    private final DataSource source;

    public RareManagerService(DataSource source) {
        this.source = source;
    }

    public static void requirePermission(Habbo habbo) {
        if (habbo == null || !habbo.hasPermission(Permission.ACC_RARE_MANAGER))
            throw new SecurityException("Permesso acc_rare_manager mancante");
    }

    public synchronized Object execute(Habbo actor, String action, JsonObject input) throws SQLException {
        requirePermission(actor);
        int page = input.has("pageId") ? input.get("pageId").getAsInt() : 0;
        int base = input.has("baseId") ? input.get("baseId").getAsInt() : 0;
        int actorId = actor.getHabboInfo().getId();
        switch (action) {
            case "admin-categories":
                return categories();
            case "admin-page":
                return page(page);
            case "admin-stats":
                return statistics(page, base);
            case "admin-save-page":
                savePage(page, input);
                return categories();
            case "admin-save-item":
                saveItem(page, base, input);
                refreshCatalog();
                return page(page);
            case "admin-save-price":
                savePrice(page, base, input);
                refreshCatalog();
                return page(page);
            case "admin-save-reward":
                saveReward(page, actorId, input);
                return page(page);
            case "admin-save-release":
                try (var c = source.getConnection()) {
                    requireItem(c, page, base);
                    requireOffer(c, page, base, input.get("offerId").getAsInt());
                }
                RareReleaseService.save(
                        actorId,
                        input.get("id").getAsInt(),
                        input.get("offerId").getAsInt(),
                        base,
                        input.get("price").getAsInt(),
                        input.get("currency").getAsInt(),
                        input.get("start").getAsLong(),
                        input.get("end").getAsLong(),
                        input.get("repeat").getAsString(),
                        input.get("enabled").getAsBoolean());
                Emulator.getGameEnvironment().getItemManager().loadItems();
                return statistics(page, base);
            default:
                throw new IllegalArgumentException("Azione gestione rari non valida");
        }
    }

    public Map<String, Object> categories() throws SQLException {
        try (var c = source.getConnection()) {
            var all = new LinkedHashMap<Integer, Map<String, Object>>();
            try (var s = c.prepareStatement(
                            "SELECT id,parent_id,caption,rare_page,rare_visible FROM catalog_pages ORDER BY order_num,id");
                    var r = s.executeQuery()) {
                while (r.next()) {
                    var row = new HashMap<String, Object>();
                    row.put("id", r.getInt("id"));
                    row.put("parentId", r.getInt("parent_id"));
                    row.put("caption", r.getString("caption"));
                    row.put("rarePage", r.getBoolean("rare_page"));
                    row.put("rareVisible", r.getBoolean("rare_visible"));
                    row.put("itemCount", 0);
                    all.put(r.getInt("id"), row);
                }
            }
            var counts = new HashMap<Integer, java.util.Set<Integer>>();
            try (var s = c.prepareStatement(
                            "SELECT ci.page_id,ci.item_ids FROM catalog_items ci JOIN catalog_pages p ON p.id=ci.page_id WHERE p.rare_page=1 OR p.rare_visible=1");
                    var r = s.executeQuery()) {
                while (r.next())
                    counts.computeIfAbsent(r.getInt(1), key -> new java.util.HashSet<>())
                            .addAll(RareCatalogCollections.baseIds(r.getString(2)));
            }
            var out = new ArrayList<Map<String, Object>>();
            for (var entry : all.entrySet()) {
                var row = entry.getValue();
                row.put(
                        "itemCount",
                        counts.getOrDefault(entry.getKey(), java.util.Set.of()).size());
                var path = new ArrayList<String>();
                var seen = new java.util.HashSet<Integer>();
                var current = row;
                while (current != null && seen.add((Integer) current.get("id"))) {
                    path.add(0, (String) current.get("caption"));
                    current = all.get((Integer) current.get("parentId"));
                }
                row.put("path", String.join(" / ", path));
                if ((boolean) row.get("rarePage") || (boolean) row.get("rareVisible")) out.add(row);
            }
            return Map.of(
                    "categories",
                    out,
                    "currencies",
                    new RareEconomyRepository(source).currencies(),
                    "serverTime",
                    Instant.now().getEpochSecond(),
                    "timeZone",
                    com.eu.habbo.util.HotelDateTimeUtil.getTimezoneId());
        }
    }

    private void requirePage(Connection c, int id, boolean marked) throws SQLException {
        try (var s = c.prepareStatement("SELECT rare_page,rare_visible FROM catalog_pages WHERE id=?")) {
            s.setInt(1, id);
            try (var r = s.executeQuery()) {
                if (!r.next() || (marked && !r.getBoolean(1) && !r.getBoolean(2)))
                    throw new IllegalArgumentException("Categoria rara inesistente");
            }
        }
    }

    private void requireItem(Connection c, int page, int base) throws SQLException {
        requirePage(c, page, true);
        if (!RareCatalogCollections.pageItems(c, page).contains(base))
            throw new IllegalArgumentException("Furni non presente nella categoria");
    }

    private void requireOffer(Connection c, int page, int base, int offer) throws SQLException {
        try (var s =
                c.prepareStatement("SELECT item_ids,amount FROM catalog_items WHERE id=? AND page_id=? FOR UPDATE")) {
            s.setInt(1, offer);
            s.setInt(2, page);
            try (var r = s.executeQuery()) {
                if (!r.next() || r.getInt(2) != 1 || !r.getString(1).trim().equals(Integer.toString(base)))
                    throw new IllegalArgumentException("Scegli un'offerta singola di questo furni nella categoria");
            }
        }
    }

    public Map<String, Object> page(int id) throws SQLException {
        try (var c = source.getConnection()) {
            requirePage(c, id, true);
            var items = new LinkedHashMap<Integer, Map<String, Object>>();
            try (var s = c.prepareStatement(
                    "SELECT id,sprite_id,item_name,public_name,type,is_rare,catalog_price,value_type FROM items_base WHERE id=?")) {
                for (int base : RareCatalogCollections.pageItems(c, id)) {
                    s.setInt(1, base);
                    try (var r = s.executeQuery()) {
                        if (!r.next()) continue;
                        var row = new HashMap<String, Object>();
                        row.put("id", base);
                        row.put("spriteId", r.getInt("sprite_id"));
                        row.put("className", r.getString("item_name"));
                        row.put("name", r.getString("public_name"));
                        row.put("type", r.getString("type"));
                        row.put("isRare", r.getBoolean("is_rare"));
                        row.put("catalogPrice", r.getBigDecimal("catalog_price"));
                        row.put("valueType", r.getInt("value_type"));
                        row.put("offers", new ArrayList<Map<String, Object>>());
                        items.put(base, row);
                    }
                }
            }
            try (var s = c.prepareStatement(
                    "SELECT id,catalog_name,item_ids,amount,cost_credits,cost_points,points_type FROM catalog_items WHERE page_id=? ORDER BY order_number,id")) {
                s.setInt(1, id);
                try (var r = s.executeQuery()) {
                    while (r.next())
                        for (int base : RareCatalogCollections.baseIds(r.getString("item_ids"))) {
                            var row = items.get(base);
                            if (row == null) continue;
                            var offer = Map.<String, Object>of(
                                    "id",
                                    r.getInt("id"),
                                    "name",
                                    r.getString("catalog_name"),
                                    "credits",
                                    r.getInt("cost_credits"),
                                    "points",
                                    r.getInt("cost_points"),
                                    "currency",
                                    r.getInt("points_type"),
                                    "single",
                                    r.getInt("amount") == 1
                                            && r.getString("item_ids").trim().equals(Integer.toString(base)));
                            @SuppressWarnings("unchecked")
                            var offers = (List<Map<String, Object>>) row.get("offers");
                            offers.add(offer);
                        }
                }
            }
            var out = new HashMap<String, Object>();
            out.put("pageId", id);
            out.put("items", items.values());
            try (var s = c.prepareStatement(
                    "SELECT p.caption,p.rare_page,p.rare_visible,cc.id category_id,cr.id reward_id,cr.configuration,cr.enabled "
                            + "FROM catalog_pages p LEFT JOIN collectible_categories cc ON cc.catalog_page_id=p.id LEFT JOIN collectible_rewards cr ON cr.id=cc.reward_id AND cr.deleted=0 WHERE p.id=?")) {
                s.setInt(1, id);
                try (var r = s.executeQuery()) {
                    r.next();
                    out.put("caption", r.getString("caption"));
                    out.put("rarePage", r.getBoolean("rare_page"));
                    out.put("rareVisible", r.getBoolean("rare_visible"));
                    out.put("collectionId", r.getInt("category_id"));
                    if (r.getInt("reward_id") > 0) {
                        out.put("reward", GSON.fromJson(r.getString("configuration"), CollectibleReward.class));
                        out.put("rewardEnabled", r.getBoolean("enabled"));
                    }
                }
            }
            return out;
        }
    }

    public Map<String, Object> statistics(int page, int base) throws SQLException {
        try (var c = source.getConnection()) {
            requireItem(c, page, base);
        }
        return Map.of(
                "pageId",
                page,
                "baseId",
                base,
                "statistics",
                new RareEconomyRepository(source).findByBaseId(base),
                "releases",
                RareReleaseService.forBase(base));
    }

    private void savePage(int id, JsonObject input) throws SQLException {
        boolean rare = input.get("rarePage").getAsBoolean(),
                visible = input.get("rareVisible").getAsBoolean();
        try (var c = source.getConnection()) {
            c.setAutoCommit(false);
            try {
                requirePage(c, id, false);
                if (visible && RareCatalogCollections.pageItems(c, id).isEmpty())
                    throw new IllegalArgumentException("La categoria non contiene furni da collezionare");
                try (var s = c.prepareStatement("UPDATE catalog_pages SET rare_page=?,rare_visible=? WHERE id=?")) {
                    s.setBoolean(1, rare);
                    s.setBoolean(2, visible);
                    s.setInt(3, id);
                    s.executeUpdate();
                }
                RareCatalogCollections.syncPage(c, id);
                c.commit();
            } catch (SQLException | RuntimeException e) {
                c.rollback();
                throw e;
            }
        }
    }

    private void saveItem(int page, int base, JsonObject input) throws SQLException {
        var price = input.get("catalogPrice").getAsBigDecimal();
        int currency = input.get("valueType").getAsInt();
        if (price.signum() < 0
                || price.compareTo(new BigDecimal("1000000000")) > 0
                || price.stripTrailingZeros().scale() > 2)
            throw new IllegalArgumentException("Prezzo di riferimento non valido (massimo due decimali)");
        if (!new RareEconomyRepository(source).currencyEnabled(currency))
            throw new IllegalArgumentException("Valuta non abilitata");
        boolean rare = input.get("isRare").getAsBoolean();
        long now = Instant.now().getEpochSecond();
        if (RareReleaseService.forBase(base).stream().anyMatch(r -> r.active(now)))
            throw new IllegalArgumentException("Pubblicazione attiva: modifica prezzo e valuta dal calendario");
        if (!rare && RareReleaseService.forBase(base).stream().anyMatch(RareReleaseService.Release::enabled))
            throw new IllegalArgumentException("Disattiva prima le pubblicazioni di questo raro");
        try (var c = source.getConnection()) {
            c.setAutoCommit(false);
            try {
                requireItem(c, page, base);
                try (var s =
                        c.prepareStatement("UPDATE items_base SET is_rare=?,catalog_price=?,value_type=? WHERE id=?")) {
                    s.setBoolean(1, rare);
                    s.setBigDecimal(2, price);
                    s.setInt(3, currency);
                    s.setInt(4, base);
                    s.executeUpdate();
                }
                c.commit();
            } catch (SQLException | RuntimeException e) {
                c.rollback();
                throw e;
            }
        }
        RareReleaseService.requestHistoryBackfill();
    }

    void savePrice(int page, int base, JsonObject input) throws SQLException {
        int offer = input.get("offerId").getAsInt(),
                price = input.get("price").getAsInt(),
                currency = input.get("currency").getAsInt();
        if (price < 0 || price > 1000000000 || !new RareEconomyRepository(source).currencyEnabled(currency))
            throw new IllegalArgumentException("Prezzo o valuta non valido");
        try (var c = source.getConnection()) {
            c.setAutoCommit(false);
            try {
                requireItem(c, page, base);
                requireOffer(c, page, base, offer);
                try (var s = c.prepareStatement(
                        "UPDATE catalog_items SET cost_credits=?,cost_points=?,points_type=? WHERE id=?")) {
                    s.setInt(1, currency == -1 ? price : 0);
                    s.setInt(2, currency == -1 ? 0 : price);
                    s.setInt(3, currency == -1 ? 0 : currency);
                    s.setInt(4, offer);
                    s.executeUpdate();
                }
                // Reference for free-generated copies. Existing emission snapshots stay untouched.
                var active = RareReleaseService.forBase(base).stream()
                        .filter(r -> r.active(Instant.now().getEpochSecond()))
                        .findFirst()
                        .orElse(null);
                try (var s = c.prepareStatement("UPDATE items_base SET catalog_price=?,value_type=? WHERE id=?")) {
                    s.setInt(1, active == null ? price : active.price());
                    s.setInt(2, active == null ? currency : active.currency());
                    s.setInt(3, base);
                    s.executeUpdate();
                }
                c.commit();
            } catch (SQLException | RuntimeException e) {
                c.rollback();
                throw e;
            }
        }
    }

    private void saveReward(int page, int actor, JsonObject input) throws SQLException {
        boolean enabled = input.get("enabled").getAsBoolean();
        var reward = GSON.fromJson(input.get("reward"), CollectibleReward.class);
        reward.validate(Instant.now().getEpochSecond());
        if (!reward.recipients().isEmpty() || reward.requiredLevel() != null)
            throw new IllegalArgumentException("Il premio richiede il completamento del set");
        if (reward.kind().equals("currency")
                && !new RareEconomyRepository(source).currencyEnabled(reward.currencyType()))
            throw new IllegalArgumentException("Valuta premio non abilitata");
        try (var c = source.getConnection()) {
            c.setAutoCommit(false);
            try {
                requirePage(c, page, true);
                // Allows preparing a reward while the collection is still hidden.
                try (var s = c.prepareStatement("SELECT caption FROM catalog_pages WHERE id=? FOR UPDATE")) {
                    s.setInt(1, page);
                    s.executeQuery().close();
                }
                var ids = RareCatalogCollections.pageItems(c, page);
                new com.eu.habbo.habbohotel.collectibles.CollectibleCategory(reward.collection(), ids).validate();
                if (reward.kind().equals("item")) {
                    try (var s = c.prepareStatement("SELECT 1 FROM items_base WHERE id=? AND type IN ('s','i')")) {
                        s.setInt(1, reward.itemId());
                        try (var r = s.executeQuery()) {
                            if (!r.next()) throw new IllegalArgumentException("Furni premio inesistente");
                        }
                    }
                }
                int rewardId = 0;
                try (var s = c.prepareStatement(
                        "SELECT reward_id FROM collectible_categories WHERE catalog_page_id=? FOR UPDATE")) {
                    s.setInt(1, page);
                    try (var r = s.executeQuery()) {
                        if (r.next()) rewardId = r.getInt(1);
                    }
                }
                String sql = rewardId > 0
                        ? "UPDATE collectible_rewards SET configuration=?,expires_at=?,enabled=?,deleted=0 WHERE id=?"
                        : "INSERT INTO collectible_rewards(configuration,expires_at,enabled,created_by,created_at) VALUES(?,?,?,?,UNIX_TIMESTAMP())";
                try (var s = c.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
                    s.setString(1, GSON.toJson(reward));
                    s.setLong(2, reward.expiresAt());
                    s.setBoolean(3, enabled);
                    s.setInt(4, rewardId > 0 ? rewardId : actor);
                    s.executeUpdate();
                    if (rewardId == 0)
                        try (var r = s.getGeneratedKeys()) {
                            if (!r.next()) throw new SQLException("ID premio non disponibile");
                            rewardId = r.getInt(1);
                        }
                }
                try (var s = c.prepareStatement(
                        "INSERT INTO collectible_categories(title,item_ids,reward_id,catalog_page_id) "
                                + "SELECT LEFT(caption,100),?,?,id FROM catalog_pages WHERE id=? ON DUPLICATE KEY UPDATE title=VALUES(title),item_ids=VALUES(item_ids),reward_id=VALUES(reward_id)")) {
                    s.setString(1, GSON.toJson(ids));
                    s.setInt(2, rewardId);
                    s.setInt(3, page);
                    s.executeUpdate();
                }
                c.commit();
            } catch (SQLException | RuntimeException e) {
                c.rollback();
                throw e;
            }
        }
    }

    private static void refreshCatalog() {
        Emulator.getGameEnvironment().getItemManager().loadItems();
        catalogRevision.incrementAndGet();
        queueReload();
    }

    private static void queueReload() {
        if (catalogReloadRunning.compareAndSet(false, true))
            Emulator.getThreading().run(RareManagerService::reload);
    }

    private static void reload() {
        if (Emulator.isShuttingDown) {
            catalogReloadRunning.set(false);
            return;
        }
        long revision = catalogRevision.get();
        var env = Emulator.getGameEnvironment();
        if (!env.reloadCatalogAndCraftingNow(env::broadcastCatalogReload)) {
            Emulator.getThreading().run(RareManagerService::reload, 250);
            return;
        }
        catalogReloadRunning.set(false);
        if (revision != catalogRevision.get()) queueReload();
    }
}
