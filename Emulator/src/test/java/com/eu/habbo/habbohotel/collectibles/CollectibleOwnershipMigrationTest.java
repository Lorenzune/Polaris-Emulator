package com.eu.habbo.habbohotel.collectibles;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.UUID;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class CollectibleOwnershipMigrationTest {
    private Connection c;
    private String schema;

    @TempDir
    Path directory;

    @Test
    void tracksEveryTransferAndNeverTreatsUnknownLegacyOwnersAsOriginalOwners() throws Exception {
        String url = System.getenv("POLARIS_COLLECTIBLE_MIGRATION_TEST_URL");
        assumeTrue(url != null && !url.isBlank(), "Requires a disposable MariaDB test schema");
        String username = System.getenv().getOrDefault("POLARIS_COLLECTIBLE_MIGRATION_TEST_USER", "root");
        String password = System.getenv().getOrDefault("POLARIS_COLLECTIBLE_MIGRATION_TEST_PASSWORD", "");
        c = DriverManager.getConnection(url, username, password);
        schema = "collectible_ownership_" + UUID.randomUUID().toString().replace("-", "");
        execute("CREATE DATABASE `" + schema + "`");
        c.setCatalog(schema);
        execute(
                "CREATE TABLE items(id INT PRIMARY KEY,user_id INT NOT NULL,item_id INT NOT NULL,room_id INT DEFAULT 0) ENGINE=InnoDB");
        execute("INSERT INTO items(id,user_id,item_id) VALUES(1,7,42)");
        String file = "V20261010110000__collectible_item_ownership.sql";
        try (var input = getClass().getResourceAsStream("/db/migration/" + file)) {
            assertNotNull(input);
            Files.copy(input, directory.resolve(file));
        }
        Flyway.configure()
                .dataSource(url.substring(0, url.lastIndexOf('/')) + "/" + schema, username, password)
                .locations("filesystem:" + directory)
                .baselineOnMigrate(true)
                .baselineVersion("0")
                .load()
                .migrate();
        assertEquals(0, number("SELECT first_owner_id FROM items WHERE id=1"));
        execute("INSERT INTO items(id,user_id,item_id) VALUES(2,7,42),(3,7,43)");
        assertEquals(7, number("SELECT first_owner_id FROM items WHERE id=2"));
        execute("UPDATE items SET room_id=99 WHERE id=2");
        assertEquals(0, number("SELECT ownership_transferred FROM items WHERE id=2"));
        assertEquals(2, eligible(7));
        execute("UPDATE items SET user_id=8 WHERE id=2");
        assertEquals(7, number("SELECT first_owner_id FROM items WHERE id=2"));
        assertEquals(0, eligible(8));
        execute("UPDATE items SET user_id=7,first_owner_id=7,ownership_transferred=0 WHERE id=2");
        assertEquals(1, number("SELECT ownership_transferred FROM items WHERE id=2"));
        assertEquals(1, eligible(7));
        assertEquals(
                2,
                number("SELECT COUNT(DISTINCT item_id) FROM items WHERE user_id=7")); // Both types still count in the
        // collection.
        execute("INSERT INTO items(id,user_id,item_id) VALUES(4,7,42)");
        assertEquals(2, eligible(7)); // A fresh original copy restores reward eligibility.
        c.setAutoCommit(false);
        execute("UPDATE items SET user_id=8 WHERE id=4");
        c.rollback();
        assertEquals(0, number("SELECT ownership_transferred FROM items WHERE id=4"));
    }

    private int eligible(int user) throws SQLException {
        return number("SELECT COUNT(DISTINCT item_id) FROM items WHERE user_id=" + user + " AND first_owner_id=" + user
                + " AND ownership_transferred=0");
    }

    private int number(String sql) throws SQLException {
        try (var s = c.createStatement();
                var rs = s.executeQuery(sql)) {
            rs.next();
            return rs.getInt(1);
        }
    }

    private void execute(String sql) throws SQLException {
        try (var s = c.createStatement()) {
            s.execute(sql);
        }
    }

    @AfterEach
    void close() throws Exception {
        if (c != null) {
            try {
                if (schema != null) execute("DROP DATABASE `" + schema + "`");
            } finally {
                c.close();
            }
        }
    }
}
