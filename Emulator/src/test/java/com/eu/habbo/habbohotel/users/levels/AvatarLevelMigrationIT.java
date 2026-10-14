package com.eu.habbo.habbohotel.users.levels;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;
import static org.mockito.Mockito.mock;

import com.eu.habbo.core.ConfigurationManager;
import com.eu.habbo.database.TestDatabase;
import com.eu.habbo.database.migration.MigrationRunner;
import com.eu.habbo.habbohotel.GameEnvironment;
import java.sql.Connection;
import java.sql.Statement;
import javax.sql.DataSource;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;

class AvatarLevelMigrationIT {
    private static void requireDatabase() {
        try {
            TestDatabase.sharedDataSource();
        } catch (Throwable exception) {
            if ("true".equalsIgnoreCase(System.getenv("CI")))
                throw new AssertionError("MariaDB integration tests are required in CI", exception);
            assumeTrue(false, "Docker/Testcontainers unavailable: " + exception.getMessage());
        }
    }

    @Test
    void legacyDuplicateAchievementsMigrateWithoutLosingTheirMaximum() throws Exception {
        requireDatabase();
        try (var database = TestDatabase.freshDatabase("avatar_duplicates")) {
            Flyway.configure()
                    .dataSource(database)
                    .locations(MigrationRunner.MIGRATION_LOCATION)
                    .target("20261011110000")
                    .placeholderReplacement(false)
                    .load()
                    .migrate();
            try (Connection connection = database.getConnection();
                    Statement sql = connection.createStatement()) {
                sql.executeUpdate(
                        "INSERT INTO users_achievements (user_id, achievement_name, progress) VALUES (920001, 'duck_avcheck_legacy', 10), (920001, 'duck_avcheck_legacy', 20)");
            }
            MigrationRunner.migrate(database);
            assertEquals(1, scalar(database, "SELECT COUNT(*) FROM users_achievements WHERE user_id = 920001"));
            assertEquals(20, scalar(database, "SELECT progress FROM users_achievements WHERE user_id = 920001"));
            assertEquals(
                    20,
                    scalar(database, "SELECT progress FROM users_avatar_achievement_progress WHERE user_id = 920001"));
            assertEquals(
                    2,
                    scalar(
                            database,
                            "SELECT source_rows FROM users_achievement_progress_repairs WHERE user_id = 920001"));
        }
    }

    @Test
    void hostingCheckpointsAreIdempotentAcrossRepeatedWorkerFlushes() throws Exception {
        requireDatabase();
        try (var database = TestDatabase.freshDatabase("avatar_hosting")) {
            MigrationRunner.migrate(database);
            var manager =
                    new AvatarLevelManager(database, mock(GameEnvironment.class), mock(ConfigurationManager.class));
            assertTrue(manager.recordHosting(920002, "duck_avcheck_hosting", 10));
            manager.setDisposed(true);
            assertEquals(
                    10,
                    scalar(database, "SELECT hosting_weighted_millis FROM users_avatar_levels WHERE user_id = 920002"));
            var restarted =
                    new AvatarLevelManager(database, mock(GameEnvironment.class), mock(ConfigurationManager.class));
            assertTrue(restarted.recordHosting(920002, "duck_avcheck_hosting", 10));
            assertTrue(restarted.recordHosting(920002, "duck_avcheck_hosting", 25));
            restarted.setDisposed(true);
            assertEquals(
                    25,
                    scalar(database, "SELECT hosting_weighted_millis FROM users_avatar_levels WHERE user_id = 920002"));
            assertEquals(
                    25,
                    scalar(
                            database,
                            "SELECT weighted_millis FROM users_avatar_hosting_checkpoints WHERE session_id = 'duck_avcheck_hosting'"));
        }
    }

    @Test
    void fixtureResetsStoredRoutinesBetweenMigrationRuns() throws Exception {
        requireDatabase();
        try (var first = TestDatabase.freshDatabase("avatar_fixture_first")) {
            MigrationRunner.migrate(first);
            assertTrue(
                    scalar(first, "SELECT COUNT(*) FROM information_schema.ROUTINES WHERE ROUTINE_SCHEMA = DATABASE()")
                            > 0);
        }
        try (var second = TestDatabase.freshDatabase("avatar_fixture_second")) {
            assertEquals(
                    0,
                    scalar(
                            second,
                            "SELECT COUNT(*) FROM information_schema.ROUTINES WHERE ROUTINE_SCHEMA = DATABASE()"));
            MigrationRunner.migrate(second);
        }
    }

    @Test
    void incrementalUpgradePreservesExistingPeaksRewardsAndProgress() throws Exception {
        requireDatabase();
        try (var database = TestDatabase.freshDatabase("avatar_incremental_upgrade")) {
            Flyway.configure()
                    .dataSource(database)
                    .locations(MigrationRunner.MIGRATION_LOCATION)
                    .target("20261014100000")
                    .placeholderReplacement(false)
                    .load()
                    .migrate();
            execute(
                    database,
                    "INSERT INTO users_achievements (user_id,achievement_name,progress) VALUES (920010,'duck_perf',35)");
            execute(database, "INSERT INTO users_badges (user_id,badge_code) VALUES (920010,'DUCK_PREVIOUS')");
            execute(database, "INSERT INTO items (id,user_id,item_id) VALUES (920010,920010,13)");
            execute(
                    database,
                    "UPDATE users_avatar_levels SET highest_score=999,level=19,rewarded_level=18,pantheon_progress=37 WHERE user_id=920010");
            execute(database, "ALTER TABLE items ADD INDEX custom_item_lookup (item_id)");
            MigrationRunner.migrate(database);
            assertEquals(
                    0,
                    scalar(
                            database,
                            "SELECT COUNT(*) FROM information_schema.STATISTICS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='items' AND INDEX_NAME='idx_items_item_id'"));
            assertEquals(35, scalar(database, "SELECT progress FROM users_achievements WHERE user_id=920010"));
            assertEquals(999, scalar(database, "SELECT highest_score FROM users_avatar_levels WHERE user_id=920010"));
            assertEquals(18, scalar(database, "SELECT rewarded_level FROM users_avatar_levels WHERE user_id=920010"));
            assertEquals(
                    37, scalar(database, "SELECT pantheon_progress FROM users_avatar_levels WHERE user_id=920010"));
            assertEquals(1, scalar(database, "SELECT raw_badges FROM users_avatar_levels WHERE user_id=920010"));
            assertEquals(1, scalar(database, "SELECT raw_furni FROM users_avatar_levels WHERE user_id=920010"));
            execute(database, "INSERT INTO users_avatar_achievement_levels VALUES ('duck_perf',920010,1,1)");
            execute(database, "UPDATE users_avatar_level_config SET ready=1 WHERE id=1");
            execute(database, "CALL avatar_levels_initialize_components()");
            execute(database, "UPDATE users_achievements SET progress=36 WHERE user_id=920010");
            assertEquals(3, scalar(database, "SELECT score FROM users_avatar_levels WHERE user_id=920010"));
            assertEquals(999, scalar(database, "SELECT highest_score FROM users_avatar_levels WHERE user_id=920010"));
        }
    }

    @Test
    void incrementalTriggersAndDeferredTradesMatchFullRefreshAndRollback() throws Exception {
        requireDatabase();
        try (var database = TestDatabase.freshDatabase("avatar_incremental_trade")) {
            MigrationRunner.migrate(database);
            assertTrue(scalar(
                            database,
                            "SELECT COUNT(*) FROM information_schema.STATISTICS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='items' AND COLUMN_NAME='item_id' AND SEQ_IN_INDEX=1")
                    > 0);
            execute(database, "INSERT INTO users_avatar_achievement_levels VALUES ('duck_perf',920010,1,1)");
            execute(database, "UPDATE users_avatar_level_config SET ready=1 WHERE id=1");
            for (int user : new int[] {920010, 920011}) {
                execute(
                        database,
                        "INSERT INTO users_achievements (user_id,achievement_name,progress) VALUES (" + user
                                + ",'duck_perf',1)");
                execute(database, "INSERT INTO users_badges (user_id,badge_code) VALUES (" + user + ",'DUCK_PERF')");
                execute(
                        database,
                        "UPDATE users_avatar_levels SET last_reconciled_at=CURRENT_TIMESTAMP WHERE user_id=" + user);
            }
            execute(database, "INSERT INTO items (id,user_id,item_id) VALUES (920010,920010,13),(920011,920011,14)");
            assertEquals(3, scalar(database, "SELECT score FROM users_avatar_levels WHERE user_id=920010"));
            try (var connection = database.getConnection()) {
                connection.setAutoCommit(false);
                try (var deferred = new AvatarLevelTransaction(connection, 920010, 920011);
                        var sql = connection.createStatement()) {
                    sql.executeUpdate("UPDATE items SET user_id=920010 WHERE id=920011");
                    // Projection changes are retained, but the intermediate extra furni earns no peak.
                    sql.executeUpdate("UPDATE items SET user_id=920011 WHERE id=920010");
                    deferred.flush();
                    connection.commit();
                }
            }
            for (int user : new int[] {920010, 920011}) {
                assertEquals(1, scalar(database, "SELECT raw_furni FROM users_avatar_levels WHERE user_id=" + user));
                assertEquals(
                        3, scalar(database, "SELECT highest_score FROM users_avatar_levels WHERE user_id=" + user));
                long score = scalar(database, "SELECT score FROM users_avatar_levels WHERE user_id=" + user);
                execute(database, "CALL avatar_levels_refresh(" + user + ")");
                assertEquals(score, scalar(database, "SELECT score FROM users_avatar_levels WHERE user_id=" + user));
            }
            try (var connection = database.getConnection();
                    var sql = connection.createStatement()) {
                connection.setAutoCommit(false);
                sql.executeUpdate("INSERT INTO users_badges (user_id,badge_code) VALUES (920010,'DUCK_ROLLBACK')");
                connection.rollback();
            }
            assertEquals(1, scalar(database, "SELECT raw_badges FROM users_avatar_levels WHERE user_id=920010"));
            execute(database, "DELETE FROM users_badges WHERE user_id=920010");
            assertEquals(0, scalar(database, "SELECT raw_badges FROM users_avatar_levels WHERE user_id=920010"));
            assertEquals(3, scalar(database, "SELECT highest_score FROM users_avatar_levels WHERE user_id=920010"));
            long revision = scalar(database, "SELECT revision FROM users_avatar_levels WHERE user_id=920010");
            execute(database, "UPDATE items_base SET public_name='Performance test' WHERE id=14");
            assertEquals(revision, scalar(database, "SELECT revision FROM users_avatar_levels WHERE user_id=920010"));
            execute(database, "UPDATE items_base SET interaction_type='photo' WHERE id=14");
            assertEquals(0, scalar(database, "SELECT raw_furni FROM users_avatar_levels WHERE user_id=920010"));
            execute(database, "UPDATE users_achievements SET progress=2 WHERE user_id=920010");
            assertEquals(1, scalar(database, "SELECT score FROM users_avatar_levels WHERE user_id=920010"));
            execute(database, "UPDATE items_base SET interaction_type='default' WHERE id=14");
            assertEquals(1, scalar(database, "SELECT raw_furni FROM users_avatar_levels WHERE user_id=920010"));
            try (var connection = database.getConnection();
                    var sql = connection.createStatement()) {
                connection.setAutoCommit(false);
                sql.executeUpdate("UPDATE items_base SET interaction_type='photo' WHERE id=14");
                connection.rollback();
            }
            assertEquals(1, scalar(database, "SELECT raw_furni FROM users_avatar_levels WHERE user_id=920010"));
            execute(database, "DELETE FROM items_base WHERE id=14");
            assertEquals(0, scalar(database, "SELECT raw_furni FROM users_avatar_levels WHERE user_id=920010"));
            execute(
                    database,
                    "INSERT INTO items_base(id,item_name,interaction_type) VALUES(14,'duck_restore','default')");
            assertEquals(1, scalar(database, "SELECT raw_furni FROM users_avatar_levels WHERE user_id=920010"));
        }
    }

    @Test
    void achievementAndHostingUpdatesDoNotWaitForAnUnrelatedFurnitureDefinitionRowLock() throws Exception {
        requireDatabase();
        try (var database = TestDatabase.freshDatabase("avatar_incremental_lock")) {
            MigrationRunner.migrate(database);
            execute(database, "UPDATE users_avatar_level_config SET ready=1 WHERE id=1");
            execute(database, "INSERT INTO items (id,user_id,item_id) VALUES (920010,920010,13)");
            execute(database, "CALL avatar_levels_refresh(920010)");
            execute(
                    database,
                    "UPDATE users_avatar_levels SET last_reconciled_at=CURRENT_TIMESTAMP WHERE user_id=920010");
            try (var blocker = database.getConnection();
                    var lock = blocker.createStatement();
                    var worker = java.util.concurrent.Executors.newSingleThreadExecutor()) {
                // A whole-table LOCK TABLES would also lock the definition trigger's
                // owner counters. Hold only the unchanged definition row, as an editor does.
                blocker.setAutoCommit(false);
                try (var definition = lock.executeQuery("SELECT id FROM items_base WHERE id=13 FOR UPDATE")) {
                    assertTrue(definition.next());
                }
                var future = worker.submit(() -> {
                    execute(
                            database,
                            "INSERT INTO users_achievements (user_id,achievement_name,progress) VALUES (920010,'duck_perf',1)");
                    execute(database, "CALL avatar_levels_publish(920010)");
                    return true;
                });
                try {
                    assertTrue(future.get(3, java.util.concurrent.TimeUnit.SECONDS));
                } finally {
                    blocker.rollback();
                }
                assertFalse(future.isCancelled());
            }
        }
    }

    private static void execute(DataSource database, String query) throws Exception {
        try (var connection = database.getConnection();
                var statement = connection.createStatement()) {
            statement.execute(query);
        }
    }

    private static long scalar(DataSource database, String query) throws Exception {
        try (var connection = database.getConnection();
                var statement = connection.createStatement();
                var row = statement.executeQuery(query)) {
            assertTrue(row.next());
            return row.getLong(1);
        }
    }
}
