package com.eu.habbo.habbohotel.users.levels;

import static org.junit.jupiter.api.Assertions.assertEquals;
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

    private static long scalar(DataSource database, String query) throws Exception {
        try (var connection = database.getConnection();
                var statement = connection.createStatement();
                var row = statement.executeQuery(query)) {
            assertTrue(row.next());
            return row.getLong(1);
        }
    }
}
