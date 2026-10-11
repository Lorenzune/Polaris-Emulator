package com.eu.habbo.database.migration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.UUID;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Runs the actual packaged SQL against a disposable schema, never the hotel's schema. */
class QuestMigrationTest {
    private static final String FILE = "V20261003140000__wired_quest_manager.sql";
    private Connection connection;
    private String schema, sql, jdbcUrl;

    @TempDir
    Path directory;

    @BeforeEach
    void prepare() throws Exception {
        String url = System.getenv("POLARIS_QUEST_MIGRATION_TEST_URL");
        assumeTrue(url != null && !url.isBlank(), "Set POLARIS_QUEST_MIGRATION_TEST_URL to an isolated MariaDB server");
        connection = DriverManager.getConnection(url, "root", "");
        schema = "quest_migration_" + UUID.randomUUID().toString().replace("-", "");
        execute("CREATE DATABASE `" + schema + "` CHARACTER SET utf8mb4");
        connection.setCatalog(schema);
        jdbcUrl = url.substring(0, url.lastIndexOf('/')) + "/" + schema;
        execute("CREATE TABLE users(id INT NOT NULL PRIMARY KEY) ENGINE=InnoDB");
        execute("INSERT INTO users VALUES(1)");
        execute("CREATE TABLE permissions(id INT NOT NULL PRIMARY KEY) ENGINE=InnoDB");
        execute(
                "CREATE TABLE permission_definitions(permission_key VARCHAR(100) PRIMARY KEY,max_value INT NOT NULL,comment TEXT,rank_1 INT DEFAULT 0,rank_2 INT DEFAULT 0,rank_3 INT DEFAULT 0,rank_4 INT DEFAULT 0,rank_5 INT DEFAULT 0,rank_6 INT DEFAULT 0,rank_7 INT DEFAULT 0) ENGINE=InnoDB");
        execute(
                "CREATE TABLE items_base(item_name VARCHAR(100) PRIMARY KEY,interaction_type VARCHAR(100)) ENGINE=InnoDB");
        execute(
                "INSERT INTO items_base VALUES('wf_var_quest','default'),('wf_var_quest_chain',NULL),('chair','default')");
        try (var input = getClass().getResourceAsStream("/db/migration/" + FILE)) {
            assertNotNull(input);
            sql = new String(input.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    @AfterEach
    void close() throws Exception {
        if (connection != null) {
            try {
                if (schema != null) execute("DROP DATABASE `" + schema + "`");
            } finally {
                connection.close();
            }
        }
    }

    private void execute(String statement) throws SQLException {
        try (var s = connection.createStatement()) {
            s.execute(statement);
        }
    }

    private void migrate() throws SQLException {
        for (var statement : sql.replaceAll("(?m)^\\s*--[^\\r\\n]*", "").split(";"))
            if (!statement.isBlank()) execute(statement);
    }

    private int number(String query) throws SQLException {
        try (var s = connection.createStatement();
                var rs = s.executeQuery(query)) {
            assertTrue(rs.next());
            return rs.getInt(1);
        }
    }

    @Test
    void normalizedPermissionsWorkWithoutAnyLegacyAchievementColumnAndRetryPreservesData() throws Exception {
        execute(
                "INSERT INTO permission_definitions(permission_key,max_value,rank_5,rank_7) VALUES('acc_achievement_editor',1,1,1)");
        migrate();
        assertEquals(1, number("SELECT rank_5 FROM permission_definitions WHERE permission_key='acc_quest_editor'"));
        assertEquals(1, number("SELECT rank_7 FROM permission_definitions WHERE permission_key='acc_quest_editor'"));
        assertEquals(2, number("SELECT COUNT(*) FROM items_base WHERE interaction_type=item_name"));
        execute("INSERT INTO wired_quest_definitions VALUES('WELCOME','{}',1)");
        execute("INSERT INTO wired_quest_progress VALUES(1,'{\"WELCOME\":{\"rewarded\":true}}',1)");
        execute("UPDATE permission_definitions SET rank_1=1 WHERE permission_key='acc_quest_editor'");
        migrate();
        assertEquals(1, number("SELECT COUNT(*) FROM wired_quest_progress WHERE progress LIKE '%rewarded%true%'"));
        assertEquals(1, number("SELECT COUNT(*) FROM wired_quest_definitions"));
        assertEquals(1, number("SELECT rank_1 FROM permission_definitions WHERE permission_key='acc_quest_editor'"));
    }

    @Test
    void missingAchievementPermissionFallsBackToRankSeven() throws Exception {
        migrate();
        assertEquals(1, number("SELECT rank_7 FROM permission_definitions WHERE permission_key='acc_quest_editor'"));
        assertEquals(
                0,
                number(
                        "SELECT rank_1+rank_2+rank_3+rank_4+rank_5+rank_6 FROM permission_definitions WHERE permission_key='acc_quest_editor'"));
    }

    @Test
    void failedMigrationCanBeRepairedAndRetriedWithoutLosingProgress() throws Exception {
        var original = Files.createDirectory(directory.resolve("original"));
        var corrected = Files.createDirectory(directory.resolve("corrected"));
        String broken = sql.substring(0, sql.indexOf("-- Polaris permissions"))
                + "ALTER TABLE permissions ADD COLUMN acc_quest_editor ENUM('0','1') DEFAULT '0'; UPDATE permissions SET acc_quest_editor='1' WHERE acc_achievement_editor='1';";
        Files.writeString(original.resolve(FILE), broken);
        Files.writeString(corrected.resolve(FILE), sql);
        var old = Flyway.configure()
                .dataSource(jdbcUrl, "root", "")
                .locations("filesystem:" + original)
                .baselineOnMigrate(true)
                .load();
        assertThrows(Exception.class, old::migrate);
        execute("INSERT INTO wired_quest_progress VALUES(1,'{\"WELCOME\":{\"rewarded\":true}}',1)");
        var fixed = Flyway.configure()
                .dataSource(jdbcUrl, "root", "")
                .locations("filesystem:" + corrected)
                .load();
        fixed.repair();
        assertEquals(1, fixed.migrate().migrationsExecuted);
        assertEquals(1, number("SELECT COUNT(*) FROM wired_quest_progress WHERE progress LIKE '%rewarded%true%'"));
        assertEquals(
                1, number("SELECT COUNT(*) FROM flyway_schema_history WHERE version='20261003140000' AND success=1"));
    }
}
