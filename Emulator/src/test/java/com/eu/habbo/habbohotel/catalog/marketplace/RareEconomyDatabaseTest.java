package com.eu.habbo.habbohotel.catalog.marketplace;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import com.eu.habbo.habbohotel.catalog.RareEconomyRepository;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mariadb.jdbc.MariaDbDataSource;

/** Mutations are restricted to the disposable schema created by the local harness. */
class RareEconomyDatabaseTest {
    private MariaDbDataSource ds;

    @BeforeEach
    void fixture() throws Exception {
        String database = System.getProperty("ducket.rare.test.database", "");
        assumeTrue(database.matches("rare_system_test_[a-f0-9]+"));
        var cfg = new HashMap<String, String>();
        for (String line : Files.readAllLines(Path.of(System.getProperty("ducket.rare.test.config")))) {
            if (line.stripLeading().startsWith("#")) continue;
            int equal = line.indexOf('=');
            if (equal > 0)
                cfg.put(
                        line.substring(0, equal).trim(),
                        line.substring(equal + 1).trim());
        }
        assertNotEquals(cfg.get("db.database"), database);
        ds = new MariaDbDataSource("jdbc:mariadb://" + cfg.get("db.hostname") + ":"
                + cfg.getOrDefault("db.port", "3306") + "/" + database);
        ds.setUser(cfg.get("db.username"));
        ds.setPassword(cfg.get("db.password"));
        for (String table : List.of(
                "marketplace_items",
                "items",
                "rare_market_sales",
                "rare_emission_values",
                "rare_emissions",
                "logs_shop_purchases",
                "logs_economy",
                "users_currency",
                "users")) sql("DELETE FROM " + table);
        sql("INSERT INTO users(id,credits) VALUES(100,1000),(55,0)");
        sql("INSERT INTO users_currency(user_id,type,amount) VALUES(100,5,1000),(100,0,1000)");
        sql("UPDATE items_base SET catalog_price=100,value_type=5,is_rare=1 WHERE id=2000032719");
    }

    private void sql(String sql) throws Exception {
        try (var c = ds.getConnection();
                var s = c.createStatement()) {
            s.execute(sql);
        }
    }

    private long number(String sql) throws Exception {
        try (var c = ds.getConnection();
                var s = c.createStatement();
                var r = s.executeQuery(sql)) {
            assertTrue(r.next());
            return r.getLong(1);
        }
    }

    private void listing(int id, int currency, int price) throws Exception {
        sql("INSERT INTO items(id,item_id,user_id,room_id,extra_data,wired_data) VALUES(" + id
                + ",2000032719,-1,0,'','')");
        sql("INSERT INTO marketplace_items(id,item_id,user_id,price,timestamp,state,currency_type) VALUES(" + id + ","
                + id + ",55," + price + ",UNIX_TIMESTAMP(),1," + currency + ")");
    }

    @Test
    void diamondPurchaseAndClaimAreAtomicAndCannotBeRepeated() throws Exception {
        listing(2001, 5, 100);
        assertNotNull(MarketPlacePurchaseTransaction.commit(ds, 2001, 2001, 100, 5, 101, 1_000_000, 100));
        assertEquals(899, number("SELECT amount FROM users_currency WHERE user_id=100 AND type=5"));
        assertEquals(1000, number("SELECT credits FROM users WHERE id=100"));
        assertEquals(100, number("SELECT buyer_id FROM rare_market_sales WHERE offer_id=2001"));
        assertEquals(100, number("SELECT unit_price FROM rare_market_sales WHERE offer_id=2001"));
        assertNull(MarketPlacePurchaseTransaction.commit(ds, 2001, 2001, 100, 5, 101, 1_000_000, 100));
        assertEquals(
                List.of(2001),
                MarketPlaceClaimTransaction.commit(ds, 55, -2, t -> true).offerIds());
        assertEquals(100, number("SELECT amount FROM users_currency WHERE user_id=55 AND type=5"));
        assertTrue(MarketPlaceClaimTransaction.commit(ds, 55, -2, t -> true)
                .offerIds()
                .isEmpty());
        assertEquals(2, number("SELECT COUNT(*) FROM logs_economy"));
    }

    @Test
    void wrongCurrencyAndInsufficientFundsRollBackHistoryAndOwnership() throws Exception {
        listing(2002, 5, 1100);
        assertNull(MarketPlacePurchaseTransaction.commit(ds, 2002, 2002, 100, -1, 1111, 1_000_000, 1100));
        assertNull(MarketPlacePurchaseTransaction.commit(ds, 2002, 2002, 100, 5, 1111, 1_000_000, 1100));
        assertEquals(1, number("SELECT state FROM marketplace_items WHERE id=2002"));
        assertEquals(-1, number("SELECT user_id FROM items WHERE id=2002"));
        assertEquals(0, number("SELECT COUNT(*) FROM rare_market_sales"));
        assertEquals(1000, number("SELECT amount FROM users_currency WHERE user_id=100 AND type=5"));
        assertEquals(0, number("SELECT COUNT(*) FROM logs_economy"));
    }

    @Test
    void missingTransferRollsBackSaleAndPriceChange() throws Exception {
        listing(2003, -1, 100);
        sql("UPDATE items SET user_id=8 WHERE id=2003");
        assertNull(MarketPlacePurchaseTransaction.commit(ds, 2003, 2003, 100, -1, 202, 1_000_000, 200));
        assertEquals(100, number("SELECT price FROM marketplace_items WHERE id=2003"));
        assertEquals(1, number("SELECT state FROM marketplace_items WHERE id=2003"));
        assertEquals(0, number("SELECT COUNT(*) FROM rare_market_sales"));
    }

    @Test
    void allCurrenciesCanBeClaimedSeparatelyAndCancelledApprovalChangesNothing() throws Exception {
        for (int i = 0; i < 3; i++) {
            int currency = new int[] {-1, 0, 5}[i];
            listing(2010 + i, currency, 100 + i);
            sql("UPDATE marketplace_items SET state=2 WHERE id=" + (2010 + i));
        }
        assertTrue(MarketPlaceClaimTransaction.commit(ds, 55, -2, t -> false)
                .offerIds()
                .isEmpty());
        assertEquals(3, number("SELECT COUNT(*) FROM marketplace_items WHERE user_id=55"));
        assertEquals(
                1,
                MarketPlaceClaimTransaction.commit(ds, 55, 0, t -> true)
                        .offerIds()
                        .size());
        assertEquals(101, number("SELECT amount FROM users_currency WHERE user_id=55 AND type=0"));
        assertEquals(
                2,
                MarketPlaceClaimTransaction.commit(ds, 55, -2, t -> true)
                        .offerIds()
                        .size());
        assertEquals(100, number("SELECT credits FROM users WHERE id=55"));
        assertEquals(102, number("SELECT amount FROM users_currency WHERE user_id=55 AND type=5"));
    }

    @Test
    void walletOverflowRollsBackEveryClaim() throws Exception {
        listing(2020, -1, 100);
        listing(2021, 5, 100);
        sql("UPDATE marketplace_items SET state=2");
        sql("INSERT INTO users_currency(user_id,type,amount) VALUES(55,5,2147483647)");
        assertThrows(IllegalArgumentException.class, () -> MarketPlaceClaimTransaction.commit(ds, 55, -2, t -> true));
        assertEquals(0, number("SELECT credits FROM users WHERE id=55"));
        assertEquals(2, number("SELECT COUNT(*) FROM marketplace_items WHERE user_id=55"));
        assertEquals(0, number("SELECT COUNT(*) FROM logs_economy"));
    }

    @Test
    void giftContentsCountOnceAtPaidPriceAndOpeningDoesNotCreateAnEmission() throws Exception {
        listing(2030, 5, 100);
        sql(
                "INSERT INTO logs_shop_purchases(timestamp,user_id,catalog_item_id,item_ids,catalog_name,cost_credits,cost_points,points_type,amount) VALUES(1,100,1,'2030','gift contents',0,80,5,1)");
        sql("UPDATE items SET user_id=55 WHERE id=2030");
        assertEquals(1, number("SELECT COUNT(*) FROM rare_emissions"));
        assertEquals(80, number("SELECT unit_value FROM rare_emission_values"));
        sql("UPDATE items_base SET catalog_price=200 WHERE id=2000032719");
        assertEquals(80, number("SELECT unit_value FROM rare_emission_values"));
    }

    @Test
    void oldPurchaseBackfillRestoresPaidPriceAndIsIdempotent() throws Exception {
        listing(2040, 5, 100);
        sql("UPDATE rare_emissions SET source='legacy_reference'");
        // A zero-price log bypasses the new trigger; amend it to reproduce a pre-upgrade payment.
        sql(
                "INSERT INTO logs_shop_purchases(timestamp,user_id,catalog_item_id,item_ids,catalog_name,cost_credits,cost_points,points_type,amount) VALUES(1,100,1,'2040','historic',0,0,5,1)");
        sql("UPDATE logs_shop_purchases SET cost_points=70");
        sql("DELETE FROM rare_system_metadata WHERE `key`='catalog_history_backfilled'");
        RareEconomyRepository.backfillCatalogHistory(ds);
        RareEconomyRepository.backfillCatalogHistory(ds);
        assertEquals(70, number("SELECT unit_value FROM rare_emission_values WHERE item_id=2040"));
        assertEquals(1, number("SELECT COUNT(*) FROM rare_emission_values WHERE item_id=2040"));
    }

    @Test
    void offerSearchUsesCompletedHistoryForOnlyTheSelectedCurrency() throws Exception {
        listing(2050, 5, 100);
        listing(2051, 0, 20);
        sql(
                "INSERT INTO rare_market_sales(offer_id,item_id,base_item_id,seller_id,currency_type,unit_price,sold_at) VALUES(9001,0,2000032719,55,5,200,1),(9002,0,2000032719,55,5,400,2),(9003,0,2000032719,55,0,999,1)");
        var request = new MarketplaceOffersQuery.Request(-1, -1, "", 2, false, 5);
        try (var c = ds.getConnection();
                var s = c.prepareStatement(MarketplaceOffersQuery.sql(request))) {
            MarketplaceOffersQuery.bind(s, request, (int) (System.currentTimeMillis() / 1000), 1, 1_000_000_000);
            try (var r = s.executeQuery()) {
                assertTrue(r.next());
                assertEquals(2050, r.getInt("id"));
                assertEquals(300, r.getInt("avg"));
                assertEquals(2, r.getInt("sold_count_today"));
                assertFalse(r.next());
            }
        }
    }

    @Test
    void flywayAppliesAndValidatesThePackagedMigrationWithoutResettingPrices() throws Exception {
        var flyway = org.flywaydb.core.Flyway.configure()
                .dataSource(ds)
                .locations("filesystem:" + System.getProperty("ducket.rare.test.migrationdir"))
                .baselineOnMigrate(true)
                .baselineVersion("20261012140000")
                .load();
        sql("UPDATE items_base SET catalog_price=175,value_type=0 WHERE id=2000032719");
        flyway.migrate();
        flyway.validate();
        assertEquals(175, number("SELECT catalog_price FROM items_base WHERE id=2000032719"));
        assertEquals(0, number("SELECT value_type FROM items_base WHERE id=2000032719"));
    }

    @Test
    void furnitureClassifiedAsRareLaterRecoversItsPreviousPurchases() throws Exception {
        RareEconomyRepository.backfillCatalogHistory(ds);
        sql("UPDATE items_base SET is_rare=0 WHERE id=2000032719");
        listing(2060, 5, 100);
        sql(
                "INSERT INTO logs_shop_purchases(timestamp,user_id,catalog_item_id,item_ids,catalog_name,cost_credits,cost_points,points_type,amount) VALUES(1,100,1,'2060','before rare classification',0,80,5,1)");
        assertEquals(0, number("SELECT COUNT(*) FROM rare_emissions"));
        sql("UPDATE items_base SET is_rare=1 WHERE id=2000032719");
        RareEconomyRepository.backfillCatalogHistory(ds);
        assertEquals(80, number("SELECT unit_value FROM rare_emission_values WHERE item_id=2060"));
        assertEquals(1, number("SELECT COUNT(*) FROM rare_emissions"));
    }

    @Test
    void legacyReferenceWritesRemainCompatibleForNewDefinitions() throws Exception {
        sql("DELETE FROM items_base WHERE id=2000032720");
        sql(
                "INSERT INTO items_base(id,sprite_id,item_name,public_name,`value`,value_type,is_rare) VALUES(2000032720,2000032720,'rare_test','Rare test',50,5,1)");
        assertEquals(50, number("SELECT catalog_price FROM items_base WHERE id=2000032720"));
        sql("DELETE FROM items_base WHERE id=2000032720");
    }
}
