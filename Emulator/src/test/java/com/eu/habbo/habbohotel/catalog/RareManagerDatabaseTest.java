package com.eu.habbo.habbohotel.catalog;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;
import static org.mockito.Mockito.RETURNS_DEEP_STUBS;
import static org.mockito.Mockito.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.eu.habbo.habbohotel.permissions.Permission;
import com.eu.habbo.habbohotel.users.Habbo;
import com.google.gson.JsonObject;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mariadb.jdbc.MariaDbDataSource;

class RareManagerDatabaseTest {
    private MariaDbDataSource source;
    private RareManagerService service;
    private Habbo actor;

    private void sql(String sql) throws Exception {
        try (var c = source.getConnection();
                var s = c.createStatement()) {
            s.execute(sql);
        }
    }

    private long number(String sql) throws Exception {
        try (var c = source.getConnection();
                var s = c.createStatement();
                var r = s.executeQuery(sql)) {
            assertTrue(r.next());
            return r.getLong(1);
        }
    }

    @BeforeEach
    void setup() throws Exception {
        String database = System.getProperty("ducket.rare.manager.test.database", "");
        assumeTrue(database.matches("rare_manager_test_[a-f0-9]+"));
        var config = new HashMap<String, String>();
        for (String line : Files.readAllLines(Path.of(System.getProperty("ducket.rare.manager.test.config")))) {
            if (line.stripLeading().startsWith("#")) continue;
            int eq = line.indexOf('=');
            if (eq > 0)
                config.put(line.substring(0, eq).trim(), line.substring(eq + 1).trim());
        }
        assertNotEquals(config.get("db.database"), database);
        source = new MariaDbDataSource("jdbc:mariadb://" + config.get("db.hostname") + ":"
                + config.getOrDefault("db.port", "3306") + "/" + database);
        source.setUser(config.get("db.username"));
        source.setPassword(config.get("db.password"));
        sql("DELETE FROM collectible_categories");
        sql("DELETE FROM collectible_rewards");
        sql("DELETE FROM catalog_items");
        sql("DELETE FROM items_base");
        sql("DELETE FROM catalog_pages WHERE id>=900000 AND id<901000");
        sql(
                "INSERT INTO catalog_pages(id,parent_id,caption,rare_page,rare_visible) VALUES(900001,-1,'Set test',1,0),(900002,-1,'Staff test',0,0)");
        sql(
                "INSERT INTO items_base(id,sprite_id,item_name,public_name,type,is_rare,catalog_price,value_type) VALUES(70001,70101,'rare_a','Raro A','s',1,100,5),(70002,70102,'rare_b','Raro B','i',1,200,-1),(70003,70103,'pet','Pet','p',0,0,-1)");
        sql(
                "INSERT INTO catalog_items(id,page_id,item_ids,catalog_name,extradata) VALUES(70001,900001,'70001;70002:4;70001;70003','Bundle',''),(70002,900001,'70002','B',''),(70003,900002,'70001','Staff','')");
        service = new RareManagerService(source);
        actor = mock(Habbo.class, RETURNS_DEEP_STUBS);
        when(actor.hasPermission(Permission.ACC_RARE_MANAGER)).thenReturn(true);
        when(actor.getHabboInfo().getId()).thenReturn(7);
    }

    private void savePage(boolean visible) throws Exception {
        var input = new JsonObject();
        input.addProperty("pageId", 900001);
        input.addProperty("rarePage", true);
        input.addProperty("rareVisible", visible);
        service.execute(actor, "admin-save-page", input);
    }

    @Test
    void visiblePageCreatesARealCollectionWithoutAPlaceholderReward() throws Exception {
        savePage(true);
        assertEquals(
                1,
                number(
                        "SELECT COUNT(*) FROM collectible_categories WHERE catalog_page_id=900001 AND reward_id IS NULL"));
        assertEquals(0, number("SELECT COUNT(*) FROM collectible_rewards"));
        try (var c = source.getConnection();
                var s = c.createStatement();
                var r = s.executeQuery("SELECT title,item_ids FROM collectible_categories")) {
            assertTrue(r.next());
            assertEquals("Set test", r.getString(1));
            assertEquals("[70001,70002]", r.getString(2));
        }
    }

    @Test
    void repeatedVisibilityAndContentChangesPreserveCollectionAndRewardIds() throws Exception {
        savePage(true);
        long category = number("SELECT id FROM collectible_categories WHERE catalog_page_id=900001");
        sql("INSERT INTO collectible_rewards(id,configuration,expires_at,created_by,created_at) VALUES(77,'{}',0,7,1)");
        sql("UPDATE collectible_categories SET reward_id=77 WHERE id=" + category);
        savePage(false);
        savePage(true);
        sql("UPDATE catalog_items SET item_ids='70002' WHERE id=70001");
        try (var c = source.getConnection()) {
            c.setAutoCommit(false);
            RareCatalogCollections.syncPage(c, 900001);
            c.commit();
        }
        assertEquals(category, number("SELECT id FROM collectible_categories WHERE catalog_page_id=900001"));
        assertEquals(77, number("SELECT reward_id FROM collectible_categories WHERE catalog_page_id=900001"));
        assertEquals(1, number("SELECT COUNT(*) FROM collectible_categories"));
    }

    @Test
    void uncommittedPublicationRollsBackBothFlagAndGeneratedSet() throws Exception {
        try (var c = source.getConnection()) {
            c.setAutoCommit(false);
            try (var s = c.createStatement()) {
                s.execute("UPDATE catalog_pages SET rare_visible=1 WHERE id=900001");
            }
            RareCatalogCollections.syncPage(c, 900001);
            c.rollback();
        }
        assertEquals(0, number("SELECT rare_visible FROM catalog_pages WHERE id=900001"));
        assertEquals(0, number("SELECT COUNT(*) FROM collectible_categories"));
    }

    @Test
    void categoryListingAndDetailsExcludeUnmarkedStaffAndDeduplicateBundles() throws Exception {
        @SuppressWarnings("unchecked")
        var categories =
                (List<java.util.Map<String, Object>>) service.categories().get("categories");
        assertTrue(categories.stream().anyMatch(c -> c.get("id").equals(900001)));
        assertFalse(categories.stream().anyMatch(c -> c.get("id").equals(900002)));
        var page = service.page(900001);
        assertEquals(2, ((java.util.Collection<?>) page.get("items")).size());
        assertThrows(IllegalArgumentException.class, () -> service.page(900002));
    }

    @Test
    void rewardIsLinkedAndUpdatingItDoesNotInventANewClaimableId() throws Exception {
        var input = com.google.gson.JsonParser.parseString(
                        "{\"pageId\":900001,\"enabled\":true,\"reward\":{\"title\":\"Premio\",\"collection\":\"Set test\",\"kind\":\"currency\",\"itemId\":0,\"currencyType\":5,\"amount\":100,\"xp\":10,\"expiresAt\":0,\"recipients\":[],\"requiredLevel\":null}}")
                .getAsJsonObject();
        service.execute(actor, "admin-save-reward", input);
        long reward = number("SELECT reward_id FROM collectible_categories WHERE catalog_page_id=900001");
        input.getAsJsonObject("reward").addProperty("amount", 200);
        service.execute(actor, "admin-save-reward", input);
        assertEquals(reward, number("SELECT reward_id FROM collectible_categories WHERE catalog_page_id=900001"));
        assertEquals(1, number("SELECT COUNT(*) FROM collectible_rewards"));
    }

    @Test
    void updatingSingleOfferSupportsCurrencyZeroAndRejectsOtherPagesAndBundles() throws Exception {
        var input = new JsonObject();
        input.addProperty("offerId", 70002);
        input.addProperty("price", 85);
        input.addProperty("currency", 0);
        service.savePrice(900001, 70002, input);
        assertEquals(85, number("SELECT cost_points FROM catalog_items WHERE id=70002"));
        assertEquals(0, number("SELECT cost_credits FROM catalog_items WHERE id=70002"));
        assertEquals(0, number("SELECT points_type FROM catalog_items WHERE id=70002"));
        assertEquals(85, number("SELECT catalog_price FROM items_base WHERE id=70002"));
        input.addProperty("offerId", 70001);
        assertThrows(IllegalArgumentException.class, () -> service.savePrice(900001, 70002, input));
        input.addProperty("offerId", 70003);
        assertThrows(IllegalArgumentException.class, () -> service.savePrice(900001, 70002, input));
        assertEquals(85, number("SELECT catalog_price FROM items_base WHERE id=70002"));
    }

    @Test
    void removingTheLastOfferEmptiesTheAutoSetWithoutDeletingItsIdentity() throws Exception {
        savePage(true);
        long category = number("SELECT id FROM collectible_categories WHERE catalog_page_id=900001");
        sql("DELETE FROM catalog_items WHERE page_id=900001");
        try (var c = source.getConnection()) {
            c.setAutoCommit(false);
            RareCatalogCollections.syncPage(c, 900001);
            c.commit();
        }
        assertEquals(category, number("SELECT id FROM collectible_categories WHERE catalog_page_id=900001"));
        try (var c = source.getConnection();
                var s = c.createStatement();
                var r = s.executeQuery("SELECT item_ids FROM collectible_categories")) {
            assertTrue(r.next());
            assertEquals("[]", r.getString(1));
        }
    }
}
