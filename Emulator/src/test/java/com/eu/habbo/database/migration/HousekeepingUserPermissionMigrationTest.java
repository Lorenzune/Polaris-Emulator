package com.eu.habbo.database.migration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.DriverManager;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** Executes the shipped migration on a disposable schema on an isolated server. */
class HousekeepingUserPermissionMigrationTest {
    @Test
    void freshAndExistingSchemasPreserveConfiguredOverridesAndGrants() throws Exception {
        String url = System.getenv("POLARIS_HK_MIGRATION_TEST_URL");
        assumeTrue(url != null && !url.isBlank(), "Set POLARIS_HK_MIGRATION_TEST_URL to an isolated MariaDB server");
        String schema = "hk_permissions_" + UUID.randomUUID().toString().replace("-", "");
        try (Connection db = DriverManager.getConnection(url, "root", "")) {
            execute(db, "CREATE DATABASE `" + schema + "`");
            try {
                db.setCatalog(schema);
                execute(db, "CREATE TABLE permission_ranks(id INT PRIMARY KEY)");
                execute(db, "INSERT INTO permission_ranks VALUES(1),(7),(12)");
                execute(
                        db,
                        "CREATE TABLE permission_definitions(permission_key VARCHAR(64) PRIMARY KEY,max_value INT,comment TEXT,rank_1 INT DEFAULT 0,rank_7 INT DEFAULT 0,rank_12 INT DEFAULT 0)");
                execute(
                        db,
                        "INSERT INTO permission_definitions VALUES('acc_hk_permissions',1,'',1,1,1),('cmd_perm_edit',1,'',0,1,1)");
                execute(
                        db,
                        "CREATE TABLE permissions(id INT PRIMARY KEY,acc_hk_permissions ENUM('0','1'),cmd_perm_edit ENUM('0','1'))");
                execute(db, "INSERT INTO permissions VALUES(1,'1','0'),(7,'1','1'),(12,'1','1')");
                execute(db, "CREATE TABLE user_permission_overrides(user_id INT,permission_key VARCHAR(64),value INT)");
                execute(db, "INSERT INTO user_permission_overrides VALUES(42,'cmd_about',0)");
                String sql;
                try (var input = getClass()
                        .getResourceAsStream("/db/migration/V20261013120000__housekeeping_user_permissions.sql")) {
                    assertNotNull(input);
                    sql = new String(input.readAllBytes(), StandardCharsets.UTF_8);
                }
                migrate(db, sql);
                assertEquals(
                        0,
                        number(
                                db,
                                "SELECT rank_1 FROM permission_definitions WHERE permission_key='acc_hk_user_permissions'"));
                assertEquals(
                        1,
                        number(
                                db,
                                "SELECT rank_7 FROM permission_definitions WHERE permission_key='acc_hk_user_permissions'"));
                assertEquals(
                        1,
                        number(
                                db,
                                "SELECT rank_12 FROM permission_definitions WHERE permission_key='acc_hk_user_permissions'"));
                assertEquals(1, number(db, "SELECT acc_hk_user_permissions FROM permissions WHERE id=7"));
                execute(
                        db,
                        "UPDATE permission_definitions SET rank_7=0 WHERE permission_key='acc_hk_user_permissions'");
                execute(db, "UPDATE permissions SET acc_hk_user_permissions='0' WHERE id=7");
                migrate(db, sql);
                assertEquals(
                        0,
                        number(
                                db,
                                "SELECT rank_7 FROM permission_definitions WHERE permission_key='acc_hk_user_permissions'"));
                assertEquals(0, number(db, "SELECT acc_hk_user_permissions FROM permissions WHERE id=7"));
                assertEquals(0, number(db, "SELECT value FROM user_permission_overrides WHERE user_id=42"));
            } finally {
                execute(db, "DROP DATABASE `" + schema + "`");
            }
        }
    }

    private static void migrate(Connection db, String sql) throws Exception {
        for (String statement : sql.replaceAll("(?m)^\\s*--[^\\r\\n]*", "").split(";"))
            if (!statement.isBlank()) execute(db, statement);
    }

    private static void execute(Connection db, String sql) throws Exception {
        try (var statement = db.createStatement()) {
            statement.execute(sql);
        }
    }

    private static int number(Connection db, String sql) throws Exception {
        try (var statement = db.createStatement();
                var rows = statement.executeQuery(sql)) {
            assertTrue(rows.next());
            return rows.getInt(1);
        }
    }
}
