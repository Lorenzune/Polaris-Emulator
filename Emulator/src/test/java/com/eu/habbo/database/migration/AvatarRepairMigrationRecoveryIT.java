package com.eu.habbo.database.migration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import com.eu.habbo.database.TestDatabase;
import com.zaxxer.hikari.HikariDataSource;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.FlywayException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class AvatarRepairMigrationRecoveryIT {
    @Test
    void historicalCollationsAndPartialDdlRecoverWithoutLosingRewards(@TempDir Path oldPackage) throws Exception {
        try {
            TestDatabase.sharedDataSource();
        } catch (Throwable exception) {
            if ("true".equalsIgnoreCase(System.getenv("CI")))
                throw new AssertionError("Docker required in CI", exception);
            assumeTrue(false, "Docker unavailable");
        }
        try (var ds = TestDatabase.freshDatabase("mig_avatar_recovery")) {
            verifyRecovery(ds, oldPackage);
        }
    }

    static void verifyRecovery(HikariDataSource ds, Path oldPackage) throws Exception {
        try (var connection = ds.getConnection();
                var sql = connection.createStatement()) {
            String schema = connection.getCatalog();
            assertTrue(schema.matches("[A-Za-z0-9_]+"));
            sql.execute("ALTER DATABASE `" + schema + "` CHARACTER SET utf8mb4 COLLATE utf8mb4_uca1400_ai_ci");
        }
        try (var files = Files.list(Path.of("src/main/resources/db/migration"))) {
            for (var file : files.toList()) {
                if (Files.isRegularFile(file)) Files.copy(file, oldPackage.resolve(file.getFileName()));
            }
        }
        String original = "V20261011170000__repair_and_reconcile_avatar_levels.sql";
        try (var fixture = AvatarRepairMigrationRecoveryIT.class.getResourceAsStream("/db/avatar-repair/" + original)) {
            assertNotNull(fixture);
            Files.copy(fixture, oldPackage.resolve(original), java.nio.file.StandardCopyOption.REPLACE_EXISTING);
        }
        var old = Flyway.configure()
                .dataSource(ds)
                .locations("filesystem:" + oldPackage)
                .javaMigrations(new db.migration.V20260518000400__normalize_legacy_permissions())
                .placeholderReplacement(false)
                .target("20261011153000")
                .load();
        old.migrate();
        try (var connection = ds.getConnection();
                var sql = connection.createStatement()) {
            sql.execute(
                    "ALTER TABLE users_earnings_pending MODIFY operation_id VARCHAR(96) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL");
            sql.execute(
                    "INSERT INTO users (id,username,password,ip_register,ip_current) VALUES (920004,'avatar_recovery','!','127.0.0.1','127.0.0.1')");
            sql.execute(
                    "INSERT INTO users_avatar_levels (user_id,level,rewarded_level,highest_score,score) VALUES (920004,30,30,40,28)");
            sql.execute(
                    "INSERT INTO users_earnings_pending (operation_id,user_id,category,currency_type,amount,reason) VALUES "
                            + "('avatar-level:920004:5',920004,'credits',0,25,'avatar.level.reward'),"
                            + "('avatar-level:920004:2',920004,'credits',0,10,'avatar.level.reward')");
        }
        FlywayException failure = assertThrows(
                FlywayException.class,
                () -> Flyway.configure()
                        .configuration(old.getConfiguration())
                        .target(AvatarRepairMigrationRecovery.VERSION)
                        .load()
                        .migrate());
        assertTrue(failure.getMessage().contains("Illegal mix of collations"), failure.getMessage());
        assertEquals(
                AvatarRepairMigrationRecovery.ORIGINAL_CHECKSUM,
                value(ds, "SELECT checksum FROM flyway_schema_history WHERE version='20261011170000'"));
        execute(ds, "UPDATE users_avatar_levels SET hosting_baseline_millis=123456 WHERE user_id=920004");
        assertThrows(
                MigrationException.class,
                () -> MigrationRunner.migrate(ds, pending -> {
                    throw new MigrationException("backup failed");
                }));
        assertEquals(0, value(ds, "SELECT success FROM flyway_schema_history WHERE version='20261011170000'"));
        AtomicInteger backups = new AtomicInteger();
        execute(ds, "UPDATE flyway_schema_history SET checksum=checksum+1 WHERE version='20261011140000'");
        assertThrows(MigrationException.class, () -> MigrationRunner.migrate(ds, pending -> backups.incrementAndGet()));
        assertEquals(0, backups.get(), "unrelated checksum errors must abort before any recovery");
        execute(ds, "UPDATE flyway_schema_history SET checksum=checksum-1 WHERE version='20261011140000'");
        MigrationRunner.migrate(ds, pending -> {
            assertEquals(
                    List.of("20261011170000", "20261011180000"),
                    pending.subList(0, 2),
                    "avatar recovery migrations remain first; later migrations are also backed up");
            backups.incrementAndGet();
        });
        assertEquals(1, backups.get());
        assertEquals(3, value(ds, "SELECT level FROM users_avatar_levels WHERE user_id=920004"));
        assertEquals(123456, value(ds, "SELECT hosting_baseline_millis FROM users_avatar_levels WHERE user_id=920004"));
        assertEquals(30, value(ds, "SELECT previous_level FROM users_avatar_level_repairs WHERE user_id=920004"));
        assertEquals(
                1, value(ds, "SELECT COUNT(*) FROM users_avatar_deferred_rewards WHERE user_id=920004 AND level=5"));
        assertEquals(1, value(ds, "SELECT COUNT(*) FROM users_earnings_pending WHERE user_id=920004"));
        execute(
                ds,
                "UPDATE flyway_schema_history SET checksum=" + AvatarRepairMigrationRecovery.ORIGINAL_CHECKSUM
                        + " WHERE version='20261011170000'");
        MigrationRunner.migrate(ds, pending -> backups.incrementAndGet());
        assertEquals(2, backups.get(), "successful original deployments require one backed-up checksum transition");
        assertEquals(
                AvatarRepairMigrationRecovery.REENTRANT_CHECKSUM,
                value(ds, "SELECT checksum FROM flyway_schema_history WHERE version='20261011170000'"));
        MigrationRunner.migrate(ds, pending -> fail("no second recovery or backup expected"));
        assertTrue(MigrationRunner.status(ds).contains("Pending migrations: 0"));
        assertEquals(1, value(ds, "SELECT COUNT(*) FROM users_avatar_deferred_rewards WHERE user_id=920004"));
    }

    private static void execute(HikariDataSource ds, String sql) throws Exception {
        try (var connection = ds.getConnection();
                var statement = connection.createStatement()) {
            statement.execute(sql);
        }
    }

    private static int value(HikariDataSource ds, String sql) throws Exception {
        try (var connection = ds.getConnection();
                var statement = connection.createStatement();
                var result = statement.executeQuery(sql)) {
            assertTrue(result.next());
            return result.getInt(1);
        }
    }
}
