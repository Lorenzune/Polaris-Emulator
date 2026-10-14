package com.eu.habbo.habbohotel.users.levels;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.eu.habbo.database.TestDatabase;
import com.eu.habbo.database.migration.MigrationRunner;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;

/** Explicit, repeatable disk-backed load check; excluded from the normal integration suite. */
@EnabledIfSystemProperty(named = "polaris.performance.load", matches = "true")
class AvatarLevelLoadIT {
    private static final int USERS = 64;
    private static final int FIRST_USER = 930000;
    private static final int WRITES = 10000;
    private static final int REFRESHES = 1000;
    private static final int WRITERS = 16;
    private static final int READERS = 4;
    private static final int POOL_SIZE = 16;

    @Test
    void concurrentAchievementWritesAndAvatarRefreshesRetainEveryIncrement() throws Exception {
        assertTrue(
                !Boolean.parseBoolean(System.getenv("POLARIS_TEST_MARIADB_TMPFS")),
                "The load benchmark must use the disk-backed fixture");
        try (var fixture = TestDatabase.freshDatabase("avatar_load")) {
            MigrationRunner.migrate(fixture);
            HikariConfig config = new HikariConfig();
            config.setJdbcUrl(fixture.getJdbcUrl());
            config.setUsername(fixture.getUsername());
            config.setPassword(fixture.getPassword());
            config.setMaximumPoolSize(POOL_SIZE);
            config.setMinimumIdle(POOL_SIZE);
            config.setPoolName("avatar-load");
            try (var database = new HikariDataSource(config)) {
                seed(database);
                String version;
                int flushAtCommit;
                int syncBinlog;
                try (var connection = database.getConnection();
                        var sql = connection.createStatement();
                        var row =
                                sql.executeQuery("SELECT VERSION(), @@innodb_flush_log_at_trx_commit, @@sync_binlog")) {
                    row.next();
                    version = row.getString(1);
                    flushAtCommit = row.getInt(2);
                    syncBinlog = row.getInt(3);
                }
                assertEquals(1, flushAtCommit, "Do not weaken commit durability for the benchmark");
                long[] writes = new long[WRITES];
                long[] refreshes = new long[REFRESHES];
                AtomicInteger nextWrite = new AtomicInteger();
                AtomicInteger nextRefresh = new AtomicInteger();
                var errors = new ConcurrentLinkedQueue<Throwable>();
                var start = new CountDownLatch(1);
                var ready = new CountDownLatch(WRITERS + READERS);
                long began;
                try (var workers = Executors.newFixedThreadPool(WRITERS + READERS)) {
                    List<java.util.concurrent.Future<?>> futures = new ArrayList<>();
                    for (int worker = 0; worker < WRITERS + READERS; worker++) {
                        final boolean writer = worker < WRITERS;
                        futures.add(workers.submit(() -> {
                            ready.countDown();
                            try {
                                start.await();
                                int operation;
                                AtomicInteger next = writer ? nextWrite : nextRefresh;
                                while ((operation = next.getAndIncrement()) < (writer ? WRITES : REFRESHES)) {
                                    long before = System.nanoTime();
                                    try (Connection connection = database.getConnection()) {
                                        int user = FIRST_USER + operation % USERS;
                                        if (writer) {
                                            try (var update = connection.prepareStatement(
                                                    "UPDATE users_achievements SET progress=progress+1 WHERE user_id=? AND achievement_name='duck_load'")) {
                                                update.setInt(1, user);
                                                assertEquals(1, update.executeUpdate());
                                            }
                                        } else {
                                            connection.setAutoCommit(false);
                                            AvatarLevelReconciler.reconcile(connection, user, false);
                                            try (var refresh =
                                                    connection.prepareCall("{CALL avatar_levels_refresh(?)}")) {
                                                refresh.setInt(1, user);
                                                refresh.execute();
                                            }
                                            connection.commit();
                                        }
                                    } catch (Throwable failure) {
                                        errors.add(failure);
                                    } finally {
                                        (writer ? writes : refreshes)[operation] = System.nanoTime() - before;
                                    }
                                }
                            } catch (InterruptedException failure) {
                                Thread.currentThread().interrupt();
                                errors.add(failure);
                            }
                        }));
                    }
                    assertTrue(ready.await(30, TimeUnit.SECONDS));
                    began = System.nanoTime();
                    start.countDown();
                    for (var future : futures) future.get(240, TimeUnit.SECONDS);
                }
                double seconds = (System.nanoTime() - began) / 1e9;
                String report = String.format(
                        Locale.ROOT,
                        "MariaDB: %s%nStorage: Docker disk (no tmpfs)%ninnodb_flush_log_at_trx_commit: %d%nsync_binlog: %d%n"
                                + "Users: %d; badges/user: 100; furniture definitions/user: 32; achievement thresholds: 100%n"
                                + "Concurrent clients: %d writers + %d refreshers; JDBC pool: %d%n"
                                + "Achievement UPDATE statements: %d; refresh transactions: %d; minimum timed JDBC statements: %d%n"
                                + "Errors (no retries): %d%nDuration: %.3f s; combined throughput: %.2f operations/s%n"
                                + "Achievement latency including pool wait: %s%nAvatar refresh latency including pool wait: %s%n",
                        version,
                        flushAtCommit,
                        syncBinlog,
                        USERS,
                        WRITERS,
                        READERS,
                        POOL_SIZE,
                        WRITES,
                        REFRESHES,
                        WRITES + REFRESHES * 3,
                        errors.size(),
                        seconds,
                        (WRITES + REFRESHES) / seconds,
                        percentiles(writes),
                        percentiles(refreshes));
                Path output = Path.of("target", "performance-load.txt");
                Files.createDirectories(output.getParent());
                Files.writeString(output, report + "Final invariant validation: pending\n");
                System.out.println(report);
                assertTrue(errors.isEmpty(), () -> "Load failures: " + errors);
                validate(database);
                Files.writeString(
                        output,
                        report
                                + "Final invariant validation: PASS (all 64 users; no lost increments; incremental score matches source rebuild; peaks preserved)\n");
            }
        }
    }

    private static void seed(HikariDataSource database) throws Exception {
        try (var connection = database.getConnection();
                var sql = connection.createStatement()) {
            connection.setAutoCommit(false);
            sql.execute("SET @avatar_levels_defer=1");
            try (var definitions = connection.prepareStatement(
                    "INSERT INTO users_avatar_achievement_levels VALUES ('duck_load',930000,?,?)")) {
                for (int level = 1; level <= 100; level++) {
                    definitions.setInt(1, level);
                    definitions.setInt(2, level);
                    definitions.addBatch();
                }
                definitions.executeBatch();
            }
            List<Integer> items = new ArrayList<>();
            try (var rows = sql.executeQuery(
                    "SELECT id FROM items_base WHERE LOWER(interaction_type) NOT IN ('postit','post_it','photo') AND LOWER(item_name) NOT IN ('photo','camera_photo','post_it') AND id<>0 ORDER BY id LIMIT 32")) {
                while (rows.next()) items.add(rows.getInt(1));
            }
            assertEquals(32, items.size());
            try (var achievement = connection.prepareStatement(
                            "INSERT INTO users_achievements (user_id,achievement_name,progress) VALUES (?,'duck_load',0)");
                    var badge =
                            connection.prepareStatement("INSERT INTO users_badges (user_id,badge_code) VALUES (?,?)");
                    var furniture = connection.prepareStatement(
                            "INSERT INTO items (id,user_id,item_id,extra_data) VALUES (?,?,?,'')")) {
                for (int offset = 0; offset < USERS; offset++) {
                    int user = FIRST_USER + offset;
                    achievement.setInt(1, user);
                    achievement.executeUpdate();
                    for (int index = 0; index < 100; index++) {
                        badge.setInt(1, user);
                        badge.setString(2, "DUCK_LOAD_" + index);
                        badge.addBatch();
                    }
                    badge.executeBatch();
                    for (int index = 0; index < items.size(); index++) {
                        furniture.setInt(1, FIRST_USER + offset * 32 + index);
                        furniture.setInt(2, user);
                        furniture.setInt(3, items.get(index));
                        furniture.addBatch();
                    }
                    furniture.executeBatch();
                }
            }
            sql.execute("SET @avatar_levels_defer=0");
            sql.executeUpdate("UPDATE users_avatar_level_config SET ready=1 WHERE id=1");
            sql.execute("CALL avatar_levels_initialize_components()");
            sql.executeUpdate(
                    "UPDATE users_avatar_levels SET last_reconciled_at=CURRENT_TIMESTAMP WHERE user_id BETWEEN 930000 AND 930063");
            connection.commit();
        }
    }

    private static void validate(HikariDataSource database) throws Exception {
        try (var connection = database.getConnection()) {
            for (int offset = 0; offset < USERS; offset++) {
                int user = FIRST_USER + offset;
                long[] before = counters(connection, user);
                assertEquals(WRITES / USERS + (offset < WRITES % USERS ? 1 : 0), before[0]);
                assertEquals(100, before[1]);
                assertEquals(100, before[2]);
                assertEquals(32, before[3]);
                assertEquals(232, before[4]);
                assertEquals(232, before[5]);
                connection.setAutoCommit(false);
                AvatarLevelReconciler.reconcile(connection, user, true);
                try (var refresh = connection.prepareCall("{CALL avatar_levels_refresh(?)}")) {
                    refresh.setInt(1, user);
                    refresh.execute();
                }
                connection.commit();
                assertTrue(Arrays.equals(before, counters(connection, user)), "Source rebuild differs for " + user);
            }
        }
    }

    private static long[] counters(Connection connection, int user) throws Exception {
        try (var query = connection.prepareStatement(
                "SELECT a.progress,l.raw_achievements,l.raw_badges,l.raw_furni,l.score,l.highest_score FROM users_achievements a JOIN users_avatar_levels l ON l.user_id=a.user_id WHERE a.user_id=? AND a.achievement_name='duck_load'")) {
            query.setInt(1, user);
            try (var row = query.executeQuery()) {
                assertTrue(row.next());
                return new long[] {
                    row.getLong(1), row.getLong(2), row.getLong(3), row.getLong(4), row.getLong(5), row.getLong(6)
                };
            }
        }
    }

    private static String percentiles(long[] times) {
        long[] sorted = times.clone();
        Arrays.sort(sorted);
        return String.format(
                Locale.ROOT,
                "p50=%.3f ms; p95=%.3f ms; p99=%.3f ms; max=%.3f ms",
                sorted[(int) Math.ceil(sorted.length * 0.50) - 1] / 1e6,
                sorted[(int) Math.ceil(sorted.length * 0.95) - 1] / 1e6,
                sorted[(int) Math.ceil(sorted.length * 0.99) - 1] / 1e6,
                sorted[sorted.length - 1] / 1e6);
    }
}
