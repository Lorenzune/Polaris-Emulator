package com.eu.habbo.database.migration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import com.eu.habbo.core.ConfigurationManager;
import com.eu.habbo.database.TestDatabase;
import com.eu.habbo.database.compat.LegacyBridgeDataSource;
import com.eu.habbo.database.compat.LegacySqlBridge;
import com.eu.habbo.database.compat.LegacySqlTranslator;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Verifies the packaged migration chain against a real MariaDB. */
class MigrationRunnerIT {

    @Test
    void petReleaseWithoutAvatarMigrationsBackfillsBeforeRepairs(@TempDir Path legacyPackage) throws Exception {
        requireDocker();
        try (var ds = TestDatabase.freshDatabase("mig_pet_avatar_backfill")) {
            verifyPetReleaseAvatarBackfill(ds, legacyPackage);
        }
    }

    static void verifyPetReleaseAvatarBackfill(HikariDataSource ds, Path legacyPackage) throws Exception {
        Set<String> avatarFiles = Set.of(
                "V20261011120000__avatar_level_progression.sql",
                "V20261011150000__durable_avatar_level_counts.sql",
                "V20261011170000__repair_and_reconcile_avatar_levels.sql",
                "V20261011180000__unique_achievement_progress.sql");
        try (var files = Files.list(Path.of("src/main/resources/db/migration"))) {
            for (Path file : files.toList()) {
                if (Files.isRegularFile(file)
                        && !avatarFiles.contains(file.getFileName().toString())) {
                    Files.copy(file, legacyPackage.resolve(file.getFileName()));
                }
            }
        }
        Flyway.configure()
                .dataSource(ds)
                .locations("filesystem:" + legacyPackage)
                .javaMigrations(new db.migration.V20260518000400__normalize_legacy_permissions())
                .target("20261011153000")
                .placeholderReplacement(false)
                .load()
                .migrate();
        assertEquals(
                "20261011153000",
                stringValue(ds, "SELECT version FROM flyway_schema_history ORDER BY installed_rank DESC LIMIT 1"));
        try (var connection = ds.getConnection();
                var sql = connection.createStatement()) {
            sql.executeUpdate("INSERT INTO users_achievements (user_id, achievement_name, progress) "
                    + "VALUES (920003, 'Profile_Pantheon_', 37), "
                    + "(920003, 'duck_avcheck_rollout', 10), (920003, 'duck_avcheck_rollout', 20)");
            sql.executeUpdate(
                    "UPDATE flyway_schema_history SET checksum = checksum + 1 " + "WHERE version = '20261011140000'");
        }
        assertThrows(
                MigrationException.class,
                () -> MigrationRunner.migrate(ds),
                "controlled backfill must still reject checksum mismatches");
        assertTrue(!tableExists(ds, "users_avatar_levels"));
        try (var connection = ds.getConnection();
                var sql = connection.createStatement()) {
            sql.executeUpdate(
                    "UPDATE flyway_schema_history SET checksum = checksum - 1 " + "WHERE version = '20261011140000'");
        }
        assertTrue(MigrationRunner.status(ds).contains("Pending migrations: 4"));
        assertTrue(!tableExists(ds, "users_avatar_levels"), "status must remain read-only");
        AtomicReference<List<String>> backedUp = new AtomicReference<>();
        MigrationRunner.migrate(ds, pending -> {
            try {
                assertTrue(!tableExists(ds, "users_avatar_levels"), "backup precedes every avatar DDL");
            } catch (Exception exception) {
                throw new AssertionError("Could not inspect schema before backup", exception);
            }
            backedUp.set(pending);
        });
        assertEquals(List.of("20261011120000", "20261011150000", "20261011170000", "20261011180000"), backedUp.get());
        assertEquals(37, intValue(ds, "SELECT progress FROM users_pantheon_legacy_progress WHERE user_id = 920003"));
        assertEquals(
                20,
                intValue(
                        ds,
                        "SELECT progress FROM users_achievements "
                                + "WHERE user_id = 920003 AND achievement_name = 'duck_avcheck_rollout'"));
        MigrationRunner.migrate(ds);
        assertEquals(37, intValue(ds, "SELECT progress FROM users_pantheon_legacy_progress WHERE user_id = 920003"));
        assertTrue(MigrationRunner.status(ds).contains("Pending migrations: 0"));
    }

    /** Local builds may skip without Docker; CI must fail if its DB tests cannot run. */
    private static void requireDocker() {
        try {
            TestDatabase.sharedDataSource();
        } catch (Throwable t) {
            if ("true".equalsIgnoreCase(System.getenv("CI"))) {
                throw new AssertionError("Docker/Testcontainers is required in CI", t);
            }
            assumeTrue(false, "Docker/Testcontainers not available — skipping DB integration test: " + t.getMessage());
        }
    }

    @Test
    void freshDatabaseMigratesThroughTheCurrentPackagedVersion() throws Exception {
        requireDocker();
        try (HikariDataSource ds = TestDatabase.freshDatabase("mig_fresh")) {
            // Empty DB -> EMPTY state.
            assertEquals(SchemaPreflight.State.EMPTY, SchemaPreflight.detect(ds));

            MigrationRunner.migrate(ds);
            assertEquals(
                    RuntimeSchemaValidator.packagedContract(),
                    RuntimeSchemaValidator.generateContract(ds),
                    "the packaged runtime contract must match the fully migrated schema — "
                            + "run 'mvn -Pupdate-runtime-schema-contract verify' in Emulator/ "
                            + "and commit the updated runtime-schema-contract.json");

            // Base table and representative Polaris-only tables both exist.
            assertTrue(tableExists(ds, "users"), "base table users must exist");
            assertTrue(tableExists(ds, "permission_ranks"), "Polaris table permission_ranks must exist");
            assertTrue(tableExists(ds, "wired_emulator_settings"), "Polaris table wired_emulator_settings must exist");
            assertTrue(tableExists(ds, "habbo_mentions"), "current mentions schema must exist");
            assertTrue(tableExists(ds, "wheel_prizes"), "current wheel schema must exist");
            assertTrue(tableExists(ds, "users_earnings_claims"), "current earnings schema must exist");
            assertTrue(tableExists(ds, "furnidata_edit_log"), "current furnidata audit schema must exist");
            assertTrue(tableExists(ds, "messenger_messages"), "dev messenger history schema must exist");
            assertTrue(tableExists(ds, "logs_economy"), "dev economy audit schema must exist");

            // Polaris-added column on a shared table.
            assertTrue(
                    columnExists(ds, "users", "auth_ticket_expires_at"),
                    "Polaris column users.auth_ticket_expires_at must exist");
            assertTrue(
                    columnExists(ds, "users", "background_border_id"), "current profile background schema must exist");
            assertTrue(columnExists(ds, "users", "access_token_version"), "credential revocation state must exist");

            // The engine conversion took effect.
            assertEquals(
                    "InnoDB", tableEngine(ds, "marketplace_items"), "marketplace_items must be InnoDB after migration");
            assertEquals(
                    "InnoDB",
                    tableEngine(ds, "users_currency"),
                    "wallet currency rows must support ledger transaction rollback");

            // The dynamic Arc permissions conversion must produce usable Polaris
            // rows, not merely empty marker tables.
            assertEquals(7, intValue(ds, "SELECT COUNT(*) FROM permission_ranks"));
            assertTrue(intValue(ds, "SELECT COUNT(*) FROM permission_definitions") >= 200);
            assertEquals(
                    1,
                    intValue(ds, "SELECT rank_7 FROM permission_definitions WHERE permission_key = 'acc_supporttool'"));
            assertEquals(5, intValue(ds, "SELECT COUNT(*) FROM pet_breeding"));
            assertEquals(100, intValue(ds, """
                    SELECT COUNT(*) FROM pet_breeding_races
                    WHERE pet_type IN (24, 25, 28, 29, 30)
                    """));
            assertEquals("breeding_nest", stringValue(ds, """
                    SELECT interaction_type FROM items_base
                    WHERE item_name = 'pet_breeding_bear'
                    """));

            // Now MANAGED, and a second migrate is a no-op (Flyway history).
            assertEquals(SchemaPreflight.State.MANAGED, SchemaPreflight.detect(ds));
            String managedStatus = MigrationRunner.status(ds);
            assertTrue(managedStatus.contains("Pending migrations: 0"));
            assertTrue(
                    managedStatus.contains("Runtime schema: compatible"),
                    "a fully migrated status must confirm the runtime contract");
            MigrationRunner.migrate(ds);
        }
    }

    @Test
    void furnitureVisualStateMovesFromOpacityTableToItems() throws Exception {
        requireDocker();
        try (HikariDataSource ds = TestDatabase.freshDatabase("mig_opacity_scaling")) {
            Flyway.configure()
                    .dataSource(ds)
                    .locations(MigrationRunner.MIGRATION_LOCATION)
                    .target("20260925120000")
                    .placeholderReplacement(false)
                    .load()
                    .migrate();

            // Simulate an older installation with the former per-room state table.
            try (Connection c = ds.getConnection();
                    Statement s = c.createStatement()) {
                s.execute("INSERT INTO `items` (`id`, `room_id`) VALUES (9001, 42)");
                s.execute("""
                        CREATE TABLE `room_furni_opacity` (
                            `room_id` INT(11) NOT NULL,
                            `furni_id` INT(11) NOT NULL,
                            `opacity` TINYINT UNSIGNED NOT NULL DEFAULT 100,
                            `click_through` TINYINT(1) NOT NULL DEFAULT 0,
                            `scaling` SMALLINT UNSIGNED NOT NULL DEFAULT 100,
                            PRIMARY KEY (`room_id`, `furni_id`)
                        ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
                        """);
                s.execute("""
                        INSERT INTO `room_furni_opacity` (`room_id`, `furni_id`, `opacity`, `click_through`, `scaling`)
                        VALUES (42, 9001, 67, 1, 185)
                        """);
            }

            MigrationRunner.migrate(ds);

            assertTrue(columnExists(ds, "items", "wired_opacity"));
            assertTrue(columnExists(ds, "items", "wired_scaling"));
            assertTrue(!tableExists(ds, "room_furni_opacity"));
            assertEquals(67, intValue(ds, "SELECT wired_opacity FROM items WHERE id = 9001"));
            assertEquals(200, intValue(ds, "SELECT wired_scaling FROM items WHERE id = 9001"));
        }
    }

    @Test
    void realArcturusFixtureMigratesAndPreservesHotelData() throws Exception {
        requireDocker();

        try (HikariDataSource ds = TestDatabase.freshDatabase("mig_arc_355")) {
            installArcturusFixture(ds);

            assertEquals(SchemaPreflight.State.RECOGNISED_EXISTING, SchemaPreflight.detect(ds));
            String status = MigrationRunner.status(ds);
            assertTrue(status.contains("Adoption: record baseline V" + MigrationRunner.BASELINE_VERSION));
            // Adoption applies every packaged migration except the skipped baseline.
            assertTrue(status.contains("Pending migrations: " + (packagedMigrationCount() - 1)));
            assertTrue(
                    !tableExists(ds, "flyway_schema_history"), "read-only status must not adopt or mutate the hotel");
            MigrationRunner.migrate(ds);

            assertEquals(SchemaPreflight.State.MANAGED, SchemaPreflight.detect(ds));
            assertTrue(tableExists(ds, "flyway_schema_history"), "adoption must create the Flyway history");
            assertTrue(tableExists(ds, "old_guilds_forums"), "unused Arc tables must be tolerated and preserved");
            assertTrue(
                    tableExists(ds, "old_guilds_forums_comments"), "unused Arc tables must be tolerated and preserved");

            // Conversion guarantees the schema Polaris requires without rewriting
            // unrelated legacy defaults, collations or storage engines.
            assertTrue(tableExists(ds, "permission_ranks"));
            assertTrue(tableExists(ds, "wired_emulator_settings"));
            assertTrue(tableExists(ds, "habbo_mentions"));
            assertTrue(tableExists(ds, "messenger_messages"));
            assertTrue(tableExists(ds, "logs_economy"));
            assertTrue(columnExists(ds, "users", "auth_ticket_expires_at"));
            assertTrue(columnExists(ds, "users", "background_border_id"));
            assertTrue(columnExists(ds, "users", "access_token_version"));
            assertEquals("InnoDB", tableEngine(ds, "marketplace_items"));
            assertEquals(7, intValue(ds, "SELECT COUNT(*) FROM permission_ranks"));
            assertTrue(intValue(ds, "SELECT COUNT(*) FROM permission_definitions") >= 200);

            // Representative operator-owned hotel data survives the conversion.
            assertEquals("Keep this hotel data", stringValue(ds, "SELECT motto FROM users WHERE id = 4242"));
            assertEquals(98765, intValue(ds, "SELECT credits FROM users WHERE id = 4242"));
            assertEquals("Existing Arcturus Room", stringValue(ds, "SELECT name FROM rooms WHERE id = 4242"));
            assertEquals("existing-item-data", stringValue(ds, "SELECT extra_data FROM items WHERE id = 4242"));
            assertEquals(777, intValue(ds, "SELECT amount FROM users_currency WHERE user_id = 4242 AND type = 5"));
            assertEquals(
                    "keep-me",
                    stringValue(ds, "SELECT value FROM emulator_settings WHERE `key` = 'fixture.operator.setting'"));
        }
    }

    @Test
    void customDevMigrationHistoryIsAdoptedWithoutOverwritingCanonicalPermissions() throws Exception {
        requireDocker();
        try (HikariDataSource ds = TestDatabase.freshDatabase("mig_existing_permissions")) {
            Flyway.configure()
                    .dataSource(ds)
                    .locations(MigrationRunner.MIGRATION_LOCATION)
                    .target("20260528020000")
                    .placeholderReplacement(false)
                    .load()
                    .migrate();

            int existingFrankResponses;
            try (Connection c = ds.getConnection();
                    Statement s = c.createStatement()) {
                s.execute("UPDATE permission_ranks SET rank_name = 'Custom Member' WHERE id = 1");
                s.execute("""
                        INSERT INTO permission_definitions
                            (permission_key, max_value, comment, rank_1)
                        VALUES ('custom_operator_permission', 1, 'keep this canonical definition', 1)
                        """);
                s.execute("""
                        UPDATE wired_emulator_settings
                        SET value = '321'
                        WHERE `key` = 'wired.engine.maxStepsPerStack'
                        """);
                s.execute("""
                        INSERT INTO emulator_settings (`key`, `value`)
                        VALUES ('websockets.whitelist', 'legacy-host.example')
                        ON DUPLICATE KEY UPDATE `value` = VALUES(`value`)
                        """);
                s.execute("""
                        INSERT INTO emulator_settings (`key`, `value`)
                        VALUES ('ws.whitelist', 'operator-host.example')
                        ON DUPLICATE KEY UPDATE `value` = VALUES(`value`)
                        """);
                s.execute("""
                        INSERT INTO pet_actions (pet_type, pet_name, offspring_type)
                        VALUES (99, 'Plugin Pet', 123)
                        """);
                s.execute("""
                        INSERT INTO pet_breeding_races (pet_type, rarity_level, breed)
                        VALUES (99, 9, 999)
                        """);
                existingFrankResponses =
                        intValue(ds, "SELECT COUNT(*) FROM bot_chat_responses WHERE bot_type = 'frank'");
                s.execute("DROP TABLE flyway_schema_history");
                s.execute("""
                        CREATE TABLE schema_migrations (
                            version INT NOT NULL PRIMARY KEY,
                            description VARCHAR(255) NOT NULL,
                            applied_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
                        )
                        """);
                s.execute("""
                        INSERT INTO schema_migrations (version, description)
                        VALUES (27, 'custom dev runner baseline')
                        """);
            }

            assertEquals(SchemaPreflight.State.RECOGNISED_EXISTING, SchemaPreflight.detect(ds));
            MigrationRunner.migrate(ds);

            assertEquals("Custom Member", stringValue(ds, "SELECT rank_name FROM permission_ranks WHERE id = 1"));
            assertEquals("keep this canonical definition", stringValue(ds, """
                            SELECT comment FROM permission_definitions
                            WHERE permission_key = 'custom_operator_permission'
                            """));
            assertEquals(1, intValue(ds, """
                    SELECT rank_1 FROM permission_definitions
                    WHERE permission_key = 'custom_operator_permission'
                    """));
            assertEquals("321", stringValue(ds, """
                    SELECT value FROM wired_emulator_settings
                    WHERE `key` = 'wired.engine.maxStepsPerStack'
                    """));
            assertEquals("operator-host.example", stringValue(ds, """
                    SELECT value FROM emulator_settings
                    WHERE `key` = 'ws.whitelist'
                    """));
            assertEquals(
                    existingFrankResponses,
                    intValue(ds, "SELECT COUNT(*) FROM bot_chat_responses WHERE bot_type = 'frank'"));
            assertEquals(123, intValue(ds, "SELECT offspring_type FROM pet_actions WHERE pet_type = 99"));
            assertEquals(1, intValue(ds, """
                    SELECT COUNT(*) FROM pet_breeding_races
                    WHERE pet_type = 99 AND rarity_level = 9 AND breed = 999
                    """));
            assertTrue(
                    tableExists(ds, "schema_migrations"),
                    "the prior custom runner history must remain harmless and untouched");
        }
    }

    @Test
    void legacyPasswordResetShapeIsRepairedWithoutRedundantIndexes() throws Exception {
        requireDocker();
        try (HikariDataSource ds = TestDatabase.freshDatabase("mig_password_resets")) {
            installArcturusFixture(ds);
            try (Connection c = ds.getConnection();
                    Statement s = c.createStatement()) {
                s.execute("""
                        CREATE TABLE password_resets (
                            email VARCHAR(255) NOT NULL,
                            token VARCHAR(128) NOT NULL,
                            expires_at TIMESTAMP NOT NULL,
                            UNIQUE KEY legacy_password_resets_token (token)
                        ) ENGINE=InnoDB
                        """);
                s.execute("""
                        INSERT INTO password_resets (email, token, expires_at)
                        VALUES ('legacy@example.test', 'keep-this-token', CURRENT_TIMESTAMP)
                        """);
            }

            MigrationRunner.migrate(ds);

            assertTrue(columnExists(ds, "password_resets", "user_id"));
            assertTrue(columnExists(ds, "password_resets", "created_ip"));
            assertEquals(1, intValue(ds, "SELECT COUNT(*) FROM password_resets"));
            assertEquals("keep-this-token", stringValue(ds, "SELECT token FROM password_resets"));
            assertEquals(1, singleColumnUniqueKeyCount(ds, "password_resets", "user_id"));
            assertEquals(1, singleColumnUniqueKeyCount(ds, "password_resets", "token"));

            MigrationRunner.migrate(ds);
            assertEquals(1, singleColumnUniqueKeyCount(ds, "password_resets", "user_id"));
            assertEquals(1, singleColumnUniqueKeyCount(ds, "password_resets", "token"));
        }
    }

    @Test
    void runtimeValidationToleratesExtensionsAndRejectsCriticalDrift() throws Exception {
        requireDocker();
        try (HikariDataSource ds = TestDatabase.freshDatabase("mig_runtime_contract")) {
            MigrationRunner.migrate(ds);
            try (Connection c = ds.getConnection();
                    Statement s = c.createStatement()) {
                s.execute("CREATE TABLE plugin_extension (id INT PRIMARY KEY, custom_value TEXT)");
            }

            MigrationRunner.migrate(ds);
            assertTrue(tableExists(ds, "plugin_extension"));

            try (Connection c = ds.getConnection();
                    Statement s = c.createStatement()) {
                s.execute("DROP TABLE messenger_offline");
            }
            MigrationException error = assertThrows(MigrationException.class, () -> MigrationRunner.migrate(ds));
            assertTrue(error.getMessage().contains("missing table messenger_offline"));

            try (Connection c = ds.getConnection();
                    Statement s = c.createStatement()) {
                s.execute("""
                        CREATE TABLE messenger_offline (
                            id INT, message TEXT, sended_on INT, user_from_id INT, user_id INT
                        )
                        """);
                s.execute("ALTER TABLE messenger_offline DROP COLUMN message");
            }
            error = assertThrows(MigrationException.class, () -> MigrationRunner.migrate(ds));
            assertTrue(error.getMessage().contains("missing column messenger_offline.message"));

            try (Connection c = ds.getConnection();
                    Statement s = c.createStatement()) {
                s.execute("ALTER TABLE messenger_offline ADD COLUMN message TEXT");
                s.execute("ALTER TABLE password_resets DROP INDEX idx_token");
            }
            error = assertThrows(MigrationException.class, () -> MigrationRunner.migrate(ds));
            assertTrue(error.getMessage().contains("missing unique key password_resets(token)"));

            try (Connection c = ds.getConnection();
                    Statement s = c.createStatement()) {
                s.execute("ALTER TABLE password_resets ADD UNIQUE INDEX restored_token (token)");
                s.execute("""
                        ALTER TABLE password_resets
                        MODIFY COLUMN created_ip VARCHAR(16) NOT NULL DEFAULT ''
                        """);
            }
            error = assertThrows(MigrationException.class, () -> MigrationRunner.migrate(ds));
            assertTrue(error.getMessage().contains("password_resets.created_ip has length 16; expected at least 45"));
        }
    }

    @Test
    void unknownNonEmptyDatabaseIsRefusedWithoutMutation() throws Exception {
        requireDocker();
        try (HikariDataSource ds = TestDatabase.freshDatabase("mig_unknown")) {
            try (Connection c = ds.getConnection();
                    Statement s = c.createStatement()) {
                s.execute("CREATE TABLE `something_unrelated` (`id` INT PRIMARY KEY)");
            }
            assertEquals(SchemaPreflight.State.UNKNOWN, SchemaPreflight.detect(ds));

            try {
                MigrationRunner.migrate(ds);
                org.junit.jupiter.api.Assertions.fail("expected refusal");
            } catch (MigrationException expected) {
                // no flyway history was created
                assertTrue(!tableExists(ds, "flyway_schema_history"), "unknown DB must not be touched");
            }
        }
    }

    @Test
    void startupMigrationsBypassTheLegacySqlBridge() throws Exception {
        requireDocker();
        try (HikariDataSource database = TestDatabase.freshDatabase("mig_raw_datasource")) {
            AtomicInteger translatedStatements = new AtomicInteger();
            LegacySqlBridge bridge = new LegacySqlBridge();
            bridge.registerTranslator(new LegacySqlTranslator() {
                @Override
                public boolean appliesTo(String lowerCaseSql) {
                    return true;
                }

                @Override
                public String translate(String sql, com.eu.habbo.database.compat.TranslationContext context) {
                    translatedStatements.incrementAndGet();
                    return sql;
                }
            });

            HikariConfig runtimeConfig = new HikariConfig();
            runtimeConfig.setJdbcUrl(database.getJdbcUrl());
            runtimeConfig.setUsername(database.getUsername());
            runtimeConfig.setPassword(database.getPassword());
            runtimeConfig.setMaximumPoolSize(2);
            runtimeConfig.setPoolName("polaris-it-legacy-wrapper");

            Path configFile = Files.createTempFile("polaris-migration", ".ini");
            Files.writeString(configFile, "db.migrate.on_startup=true\n", StandardCharsets.UTF_8);
            try (LegacyBridgeDataSource runtime = new LegacyBridgeDataSource(runtimeConfig, bridge)) {
                MigrationRunner.runAtStartup(runtime, new ConfigurationManager(configFile.toString()));
            } finally {
                Files.deleteIfExists(configFile);
            }

            assertEquals(0, translatedStatements.get(), "Flyway SQL must never be offered to the legacy plugin bridge");
            assertEquals(SchemaPreflight.State.MANAGED, SchemaPreflight.detect(database));
        }
    }

    private static boolean tableExists(HikariDataSource ds, String table) throws Exception {
        try (Connection c = ds.getConnection();
                var st = c.prepareStatement(
                        "SELECT COUNT(*) FROM information_schema.TABLES WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME=?")) {
            st.setString(1, table);
            try (ResultSet rs = st.executeQuery()) {
                return rs.next() && rs.getInt(1) > 0;
            }
        }
    }

    private static boolean columnExists(HikariDataSource ds, String table, String column) throws Exception {
        try (Connection c = ds.getConnection();
                var st = c.prepareStatement(
                        "SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME=? AND COLUMN_NAME=?")) {
            st.setString(1, table);
            st.setString(2, column);
            try (ResultSet rs = st.executeQuery()) {
                return rs.next() && rs.getInt(1) > 0;
            }
        }
    }

    private static String tableEngine(HikariDataSource ds, String table) throws Exception {
        try (Connection c = ds.getConnection();
                var st = c.prepareStatement(
                        "SELECT ENGINE FROM information_schema.TABLES WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME=?")) {
            st.setString(1, table);
            try (ResultSet rs = st.executeQuery()) {
                return rs.next() ? rs.getString(1) : null;
            }
        }
    }

    private static int singleColumnUniqueKeyCount(HikariDataSource ds, String table, String column) throws Exception {
        try (Connection c = ds.getConnection();
                var st = c.prepareStatement("""
                     SELECT COUNT(*)
                     FROM (
                         SELECT INDEX_NAME
                         FROM information_schema.STATISTICS
                         WHERE TABLE_SCHEMA = DATABASE()
                           AND TABLE_NAME = ?
                           AND NON_UNIQUE = 0
                         GROUP BY INDEX_NAME
                         HAVING COUNT(*) = 1
                            AND MAX(CASE WHEN SEQ_IN_INDEX = 1 AND COLUMN_NAME = ? THEN 1 ELSE 0 END) = 1
                     ) AS matching_unique_keys
                     """)) {
            st.setString(1, table);
            st.setString(2, column);
            try (ResultSet rs = st.executeQuery()) {
                return rs.next() ? rs.getInt(1) : 0;
            }
        }
    }

    /** Counts the packaged SQL and Java migrations from the source tree. */
    private static long packagedMigrationCount() throws Exception {
        long count = 0;
        for (Path directory :
                List.of(Path.of("src/main/resources/db/migration"), Path.of("src/main/java/db/migration"))) {
            try (var files = Files.list(directory)) {
                count += files.filter(p -> p.getFileName().toString().matches("V\\d{14}__\\w+\\.(sql|java)"))
                        .count();
            }
        }
        return count;
    }

    private static void installArcturusFixture(HikariDataSource ds) throws Exception {
        executeSqlResource(ds, "db/fixture/arcturus_ms_3_5_5_schema.sql");
        executeSqlResource(ds, "db/fixture/arcturus_ms_3_5_5_running_hotel.sql");
    }

    private static void executeSqlResource(HikariDataSource ds, String resource) throws Exception {
        InputStream input = MigrationRunnerIT.class.getClassLoader().getResourceAsStream(resource);
        if (input == null) {
            throw new IllegalStateException("Missing SQL fixture resource: " + resource);
        }

        StringBuilder sql = new StringBuilder();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(input, StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                if (!line.stripLeading().startsWith("--")) {
                    sql.append(line).append('\n');
                }
            }
        }

        try (Connection c = ds.getConnection();
                Statement s = c.createStatement()) {
            for (String statement : splitSqlStatements(sql.toString())) {
                if (!statement.isBlank()) {
                    s.execute(statement);
                }
            }
        }
    }

    private static List<String> splitSqlStatements(String sql) {
        List<String> statements = new ArrayList<>();
        StringBuilder statement = new StringBuilder();
        char quote = 0;

        for (int i = 0; i < sql.length(); i++) {
            char current = sql.charAt(i);
            statement.append(current);

            if (quote != 0) {
                if (current == '\\' && i + 1 < sql.length()) {
                    statement.append(sql.charAt(++i));
                } else if (current == quote) {
                    if (i + 1 < sql.length() && sql.charAt(i + 1) == quote) {
                        statement.append(sql.charAt(++i));
                    } else {
                        quote = 0;
                    }
                }
            } else if (current == '\'' || current == '"' || current == '`') {
                quote = current;
            } else if (current == ';') {
                statements.add(statement.substring(0, statement.length() - 1));
                statement.setLength(0);
            }
        }

        if (!statement.isEmpty()) {
            statements.add(statement.toString());
        }
        return statements;
    }

    private static String stringValue(HikariDataSource ds, String query) throws Exception {
        try (Connection c = ds.getConnection();
                Statement s = c.createStatement();
                ResultSet rs = s.executeQuery(query)) {
            return rs.next() ? rs.getString(1) : null;
        }
    }

    private static int intValue(HikariDataSource ds, String query) throws Exception {
        try (Connection c = ds.getConnection();
                Statement s = c.createStatement();
                ResultSet rs = s.executeQuery(query)) {
            return rs.next() ? rs.getInt(1) : Integer.MIN_VALUE;
        }
    }
}
