package com.eu.habbo.messages.incoming.housekeeping;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.FlywayException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mariadb.jdbc.MariaDbDataSource;

class HousekeepingCollationDatabaseTest {
    private static final String VERSION = "20261012220000";
    private static final String SCRIPT = "V20261012220000__housekeeping_panel_upstream.sql";
    private static final String KEYS =
            "'acc_hk_users','acc_hk_bans','acc_hk_economy','acc_hk_rooms','acc_hk_permissions','acc_hk_hotel','acc_hk_view_private','cmd_maintenance'";
    private MariaDbDataSource source;
    private String database;
    private Path report;

    private void sql(String text) throws Exception {
        try (var c = source.getConnection();
                var s = c.createStatement()) {
            s.execute(text);
        }
    }

    private long number(String text) throws Exception {
        try (var c = source.getConnection();
                var s = c.createStatement();
                var r = s.executeQuery(text)) {
            assertTrue(r.next());
            return r.getLong(1);
        }
    }

    @BeforeEach
    void setup() throws Exception {
        database = System.getProperty("ducket.hk.collation.test.database", "");
        assumeTrue(database.matches("housekeeping_collation_test_[a-f0-9]+"));
        var config = new HashMap<String, String>();
        for (String line : Files.readAllLines(Path.of(System.getProperty("ducket.hk.collation.test.config")))) {
            if (line.stripLeading().startsWith("#")) continue;
            int eq = line.indexOf('=');
            if (eq > 0)
                config.put(line.substring(0, eq).trim(), line.substring(eq + 1).trim());
        }
        assertNotEquals(config.get("db.database"), database);
        report = Path.of(System.getProperty("ducket.hk.collation.test.report"));
        source = new MariaDbDataSource("jdbc:mariadb://" + config.get("db.hostname") + ":"
                + config.getOrDefault("db.port", "3306") + "/" + database);
        source.setUser(config.get("db.username"));
        source.setPassword(config.get("db.password"));
        sql("DROP TABLE IF EXISTS flyway_schema_history, hk_failed_migration_backup_20261012, housekeeping_user_notes");
        sql("DELETE FROM permission_definitions WHERE permission_key IN (" + KEYS + ")");
        sql("ALTER DATABASE `" + database + "` CHARACTER SET utf8mb4 COLLATE utf8mb4_uca1400_ai_ci");
        sql("ALTER TABLE permission_definitions CONVERT TO CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci");
    }

    private Flyway flyway(Path directory) {
        return Flyway.configure()
                .dataSource(source)
                .locations("filesystem:" + directory.toAbsolutePath())
                .baselineOnMigrate(true)
                .baselineVersion("20261012210000")
                .load();
    }

    private void executeRecovery() throws Exception {
        String script = Files.readString(report.resolve("01-rimuovi-solo-tentativo-fallito.sql"))
                .replace("USE `paxxo`;", "USE `" + database + "`;");
        String statements = script.lines()
                .filter(line -> !line.stripLeading().startsWith("--"))
                .collect(java.util.stream.Collectors.joining("\n"));
        for (String statement : statements.split(";")) if (!statement.isBlank()) sql(statement);
    }

    @Test
    void reproducesTheReportedFlywayFailureAndRecoversOnlyItsHistoryRow(@TempDir Path oldDir, @TempDir Path newDir)
            throws Exception {
        Files.copy(report.resolve("housekeeping-migration-before.sql"), oldDir.resolve(SCRIPT));
        var old = flyway(oldDir);
        var failure = assertThrows(FlywayException.class, old::migrate);
        assertTrue(failure.getMessage().contains("Illegal mix of collations"), failure.getMessage());
        assertEquals(
                1, number("SELECT COUNT(*) FROM flyway_schema_history WHERE version='" + VERSION + "' AND success=0"));
        long oldChecksum = number("SELECT checksum FROM flyway_schema_history WHERE version='" + VERSION + "'");
        executeRecovery();
        assertEquals(0, number("SELECT COUNT(*) FROM flyway_schema_history WHERE version='" + VERSION + "'"));
        assertEquals(1, number("SELECT COUNT(*) FROM flyway_schema_history WHERE success=1"), "the baseline remains");
        assertEquals(1, number("SELECT COUNT(*) FROM hk_failed_migration_backup_20261012 WHERE success=0"));
        Files.copy(Path.of("src/main/resources/db/migration", SCRIPT), newDir.resolve(SCRIPT));
        var fixed = flyway(newDir);
        assertEquals(1, fixed.migrate().migrationsExecuted);
        fixed.validate();
        assertEquals(
                1, number("SELECT COUNT(*) FROM flyway_schema_history WHERE version='" + VERSION + "' AND success=1"));
        long fixedChecksum = number("SELECT checksum FROM flyway_schema_history WHERE version='" + VERSION + "'");
        Files.writeString(
                report.resolve("flyway-checksums.json"),
                "{\"old\":" + oldChecksum + ",\"fixed\":" + fixedChecksum + "}");
    }

    @Test
    void initializesFreshPermissionsWithReversedCollationsAndKeepsExistingDenials(@TempDir Path directory)
            throws Exception {
        sql("ALTER DATABASE `" + database + "` CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci");
        sql("ALTER TABLE permission_definitions CONVERT TO CHARACTER SET utf8mb4 COLLATE utf8mb4_uca1400_ai_ci");
        sql(
                "INSERT INTO permission_definitions(permission_key,max_value,comment) VALUES('acc_hk_rooms',1,'Existing deliberate denial')");
        Files.copy(Path.of("src/main/resources/db/migration", SCRIPT), directory.resolve(SCRIPT));
        var fixed = flyway(directory);
        assertEquals(1, fixed.migrate().migrationsExecuted);
        fixed.validate();
        assertEquals(0, number("SELECT rank_7 FROM permission_definitions WHERE permission_key='acc_hk_rooms'"));
        assertEquals(
                number("SELECT rank_7 FROM permission_definitions WHERE permission_key='acc_housekeeping'"),
                number("SELECT rank_7 FROM permission_definitions WHERE permission_key='acc_hk_users'"));
        assertEquals(0, fixed.migrate().migrationsExecuted);
        assertEquals(0, number("SELECT rank_7 FROM permission_definitions WHERE permission_key='acc_hk_rooms'"));
    }

    @Test
    void successfulOldInstallationKeepsDataAndAdoptsOnlyTheKnownChecksum(@TempDir Path oldDir, @TempDir Path newDir)
            throws Exception {
        sql("ALTER DATABASE `" + database + "` CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci");
        Files.copy(report.resolve("housekeeping-migration-before.sql"), oldDir.resolve(SCRIPT));
        assertEquals(1, flyway(oldDir).migrate().migrationsExecuted);
        assertEquals(727403917, number("SELECT checksum FROM flyway_schema_history WHERE version='" + VERSION + "'"));
        sql("UPDATE permission_definitions SET rank_7=0 WHERE permission_key='acc_hk_rooms'");
        executeRecovery();
        assertEquals(-568993512, number("SELECT checksum FROM flyway_schema_history WHERE version='" + VERSION + "'"));
        assertEquals(
                727403917,
                number("SELECT checksum FROM hk_failed_migration_backup_20261012 WHERE version='" + VERSION + "'"));
        assertEquals(
                1, number("SELECT COUNT(*) FROM flyway_schema_history WHERE version='20261012210000' AND success=1"));
        Files.copy(Path.of("src/main/resources/db/migration", SCRIPT), newDir.resolve(SCRIPT));
        var fixed = flyway(newDir);
        fixed.validate();
        assertEquals(0, fixed.migrate().migrationsExecuted);
        assertEquals(0, number("SELECT rank_7 FROM permission_definitions WHERE permission_key='acc_hk_rooms'"));
        sql("UPDATE flyway_schema_history SET checksum=12345 WHERE version='" + VERSION + "'");
        executeRecovery();
        assertEquals(
                12345,
                number("SELECT checksum FROM flyway_schema_history WHERE version='" + VERSION + "'"),
                "unknown checksums are not repaired");
        assertThrows(FlywayException.class, fixed::validate);
    }
}
