package com.eu.habbo.habbohotel.catalog;

import com.google.gson.Gson;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicLong;
import javax.sql.DataSource;

/** Catalogue-linked collections keep their category/reward IDs, and therefore existing claims. */
public final class RareCatalogCollections {
    private static final Gson GSON = new Gson();
    private static final AtomicLong NEXT_REFRESH = new AtomicLong();

    private RareCatalogCollections() {}

    public static List<Integer> baseIds(String text) {
        Set<Integer> ids = new LinkedHashSet<>();
        if (text == null) return List.of();
        for (String token : text.split(";")) {
            if (token.isBlank()) continue;
            try {
                int id = Integer.parseInt(token.trim().split(":", 2)[0]);
                if (id > 0) ids.add(id);
            } catch (NumberFormatException ignored) {
                throw new IllegalArgumentException("Offerta con item_ids non valido");
            }
        }
        return List.copyOf(ids);
    }

    public static List<Integer> pageItems(Connection c, int pageId) throws SQLException {
        Set<Integer> ids = new LinkedHashSet<>();
        try (var s =
                c.prepareStatement("SELECT item_ids FROM catalog_items WHERE page_id=? ORDER BY order_number,id")) {
            s.setInt(1, pageId);
            try (var r = s.executeQuery()) {
                while (r.next()) ids.addAll(baseIds(r.getString(1)));
            }
        }
        if (ids.isEmpty()) return List.of();
        // Only furniture the room/inventory and collection interfaces can represent.
        Set<Integer> valid = new LinkedHashSet<>();
        var ordered = List.copyOf(ids);
        for (int offset = 0; offset < ordered.size(); offset += 500) {
            var batch = ordered.subList(offset, Math.min(offset + 500, ordered.size()));
            String placeholders = String.join(",", java.util.Collections.nCopies(batch.size(), "?"));
            try (var s = c.prepareStatement(
                    "SELECT id FROM items_base WHERE type IN ('s','i') AND id IN (" + placeholders + ")")) {
                for (int index = 0; index < batch.size(); index++) s.setInt(index + 1, batch.get(index));
                try (var r = s.executeQuery()) {
                    while (r.next()) valid.add(r.getInt(1));
                }
            }
        }
        return ids.stream().filter(valid::contains).toList();
    }

    public static void syncPage(Connection c, int pageId) throws SQLException {
        String title;
        boolean visible;
        try (var s = c.prepareStatement("SELECT caption,rare_visible FROM catalog_pages WHERE id=? FOR UPDATE")) {
            s.setInt(1, pageId);
            try (var r = s.executeQuery()) {
                if (!r.next()) throw new IllegalArgumentException("Categoria inesistente");
                title = r.getString(1);
                visible = r.getBoolean(2);
            }
        }
        if (!visible) return; // Visibility is read directly from catalog_pages; never delete claims.
        var ids = pageItems(c, pageId);
        if (ids.isEmpty()) {
            try (var s =
                    c.prepareStatement("UPDATE collectible_categories SET item_ids='[]' WHERE catalog_page_id=?")) {
                s.setInt(1, pageId);
                s.executeUpdate();
            }
            return;
        }
        if (ids.size() > 500) throw new IllegalArgumentException("Una collezione può contenere al massimo 500 furni");
        try (var s = c.prepareStatement("INSERT INTO collectible_categories(title,item_ids,reward_id,catalog_page_id) "
                + "VALUES(?,?,NULL,?) ON DUPLICATE KEY UPDATE title=VALUES(title),item_ids=VALUES(item_ids)")) {
            s.setString(
                    1,
                    title == null || title.isBlank()
                            ? "Set " + pageId
                            : title.substring(0, Math.min(100, title.length())));
            s.setString(2, GSON.toJson(ids));
            s.setInt(3, pageId);
            s.executeUpdate();
        }
    }

    public static synchronized void refresh(DataSource source) throws SQLException {
        if (System.currentTimeMillis() < NEXT_REFRESH.get()) return;
        try (var c = source.getConnection()) {
            c.setAutoCommit(false);
            try {
                var pages = new java.util.ArrayList<Integer>();
                try (var s = c.prepareStatement("SELECT id FROM catalog_pages WHERE rare_visible=1 ORDER BY id");
                        var r = s.executeQuery()) {
                    while (r.next()) pages.add(r.getInt(1));
                }
                for (int id : pages) syncPage(c, id);
                c.commit();
                NEXT_REFRESH.set(System.currentTimeMillis() + 5000);
            } catch (SQLException | RuntimeException failure) {
                c.rollback();
                throw failure;
            }
        }
    }
}
