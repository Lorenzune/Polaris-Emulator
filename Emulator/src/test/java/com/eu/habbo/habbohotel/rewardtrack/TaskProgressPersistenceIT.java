package com.eu.habbo.habbohotel.rewardtrack;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.RETURNS_DEEP_STUBS;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

import com.eu.habbo.Emulator;
import com.eu.habbo.database.Database;
import com.eu.habbo.database.TestDatabase;
import com.eu.habbo.database.migration.MigrationRunner;
import com.eu.habbo.habbohotel.GameEnvironment;
import com.eu.habbo.habbohotel.achievements.AchievementInteractions;
import com.eu.habbo.habbohotel.dailytasks.DailyTaskManager;
import com.eu.habbo.habbohotel.users.Habbo;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Proxy;
import java.sql.Connection;
import java.sql.Date;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class TaskProgressPersistenceIT {
    private static final int USER = 940001;
    private static final int TASK = 940001;
    private static final int OTHER_TASK = 940002;
    private static final LocalDate TODAY = LocalDate.now();

    @Test
    void progressAndAchievementCallbacksWorkWithOneConnectionAndLongStreaks() throws Exception {
        if (!TestDatabase.dockerAvailable()) {
            if ("true".equalsIgnoreCase(System.getenv("CI"))) throw new AssertionError("MariaDB is required in CI");
            org.junit.jupiter.api.Assumptions.assumeTrue(false, "Docker/Testcontainers unavailable");
        }
        try (var fixture = TestDatabase.freshDatabase("task_progress")) {
            MigrationRunner.migrate(fixture);
            var config = new HikariConfig();
            config.setJdbcUrl(fixture.getJdbcUrl());
            config.setUsername(fixture.getUsername());
            config.setPassword(fixture.getPassword());
            config.setMaximumPoolSize(1);
            config.setConnectionTimeout(250);
            try (var pool = new HikariDataSource(config)) {
                seed(pool);
                List<String> queries = new ArrayList<>();
                HikariDataSource observed = mock(HikariDataSource.class);
                when(observed.getConnection()).thenAnswer(ignored -> observed(pool.getConnection(), queries));
                Database database = mock(Database.class);
                when(database.getDataSource()).thenReturn(observed);
                GameEnvironment environment = mock(GameEnvironment.class, RETURNS_DEEP_STUBS);
                Habbo habbo = mock(Habbo.class, RETURNS_DEEP_STUBS);
                when(environment.getHabboManager().getHabbo(USER)).thenReturn(habbo);
                var daily = new DailyTaskManager();
                when(environment.getDailyTaskManager()).thenReturn(daily);
                Map<String, Integer> achievements = new HashMap<>();
                var variables = habbo.getHabboInfo().getCurrentRoom().getUserVariableManager();
                doAnswer(ignored -> {
                            assertPoolReleased(pool);
                            return null;
                        })
                        .when(variables)
                        .refreshDailyTaskVariables(USER);
                try (var emulator = mockStatic(Emulator.class);
                        var progress = mockStatic(AchievementInteractions.class)) {
                    emulator.when(Emulator::getDatabase).thenReturn(database);
                    emulator.when(Emulator::getGameEnvironment).thenReturn(environment);
                    emulator.when(Emulator::getIntUnixTimestamp).thenReturn(1700000000);
                    progress.when(() -> AchievementInteractions.sync(eq(habbo), anyString(), anyInt()))
                            .thenAnswer(call -> {
                                assertPoolReleased(pool);
                                achievements.put(call.getArgument(1), call.getArgument(2));
                                return null;
                            });
                    var rewards = new RewardTrackManager();
                    // An action absent from reward tracks must still reach daily tasks.
                    assertEquals(0, rewards.addProgress(USER, "duck_daily_only", 1));
                    assertEquals(
                            1,
                            scalar(
                                    pool,
                                    "SELECT progress FROM user_daily_task_progress WHERE user_id=940001 AND task_id=940002 AND task_date=CURRENT_DATE"));
                    assertEquals(0, achievements.get("DailyTaskStreakDays"));
                    assertEquals(1, rewards.addProgress(USER, "duck_task_perf", 1));
                    assertEquals(2, rewards.addProgress(USER, "duck_task_perf", 1));
                    // An upsert's affected-row count of two is still a single increment.
                    assertEquals(
                            2,
                            scalar(
                                    pool,
                                    "SELECT progress FROM user_reward_track_progress WHERE user_id=940001 AND task_id=940001"));
                    queries.clear();
                    assertEquals(2, rewards.addProgress(USER, "duck_task_perf", 1));
                    assertEquals(
                            3,
                            scalar(
                                    pool,
                                    "SELECT progress FROM user_daily_task_progress WHERE user_id=940001 AND task_id=940001 AND task_date=CURRENT_DATE"));
                    assertEquals(368, achievements.get("DailyTasksCompleted"));
                    assertEquals(366, achievements.get("DailyTaskStreakDays"));
                    assertEquals(1, achievements.get("RewardTracksCompleted"));
                    assertEquals(
                            2,
                            queries.stream()
                                    .filter(sql -> sql.contains("FROM user_daily_task_progress WHERE user_id = ?"))
                                    .count(),
                            "Completion count plus streak must use two reads regardless of streak length");
                    // Query history remains live: removing yesterday creates a real streak gap.
                    execute(
                            pool,
                            "DELETE FROM user_daily_task_progress WHERE user_id=940001 AND task_date=CURRENT_DATE-INTERVAL 1 DAY");
                    assertEquals(1, daily.addProgress(USER, "duck_daily_only", 1));
                    assertEquals(1, achievements.get("DailyTaskStreakDays"));
                    assertEquals(366, achievements.get("DailyTasksCompleted"));
                    // No completed task today means zero, even with past/future completions.
                    execute(
                            pool,
                            "DELETE FROM user_daily_task_progress WHERE user_id=940001 AND task_id=940001 AND task_date=CURRENT_DATE");
                    assertEquals(1, daily.addProgress(USER, "duck_daily_only", 1));
                    assertEquals(0, achievements.get("DailyTaskStreakDays"));
                    assertEquals(365, achievements.get("DailyTasksCompleted"));
                    assertEquals(0, rewards.addProgress(USER, "toggle_floor_item", 1));
                    assertEquals(
                            3,
                            scalar(
                                    pool,
                                    "SELECT progress FROM user_reward_track_progress WHERE user_id=940001 AND task_id=940001"));
                    assertEquals(
                            0,
                            scalar(
                                    pool,
                                    "SELECT SUM(reward_claimed_at) FROM user_daily_task_progress WHERE user_id=940001 AND task_date=CURRENT_DATE"));
                    assertTrue(rewards.applyWiredProgress(USER, "duck_task_perf", "duck_task_perf", 5, true) > 0);
                    assertEquals(
                            8,
                            scalar(
                                    pool,
                                    "SELECT progress FROM user_reward_track_progress WHERE user_id=940001 AND task_id=940001"));
                    assertTrue(rewards.applyWiredProgress(USER, "duck_task_perf", "duck_task_perf", 4, false) > 0);
                    assertEquals(
                            4,
                            scalar(
                                    pool,
                                    "SELECT progress FROM user_reward_track_progress WHERE user_id=940001 AND task_id=940001"));
                    assertEquals(1, rewards.resetWiredProgress(USER, "duck_task_perf"));
                    assertEquals(
                            0,
                            scalar(
                                    pool,
                                    "SELECT COUNT(*) FROM user_reward_track_progress WHERE user_id=940001 AND task_id=940001"));
                    assertEquals(1, scalar(pool, "SELECT COUNT(*) FROM user_reward_track_claims WHERE user_id=940001"));
                    int beforeIgnoredActions = queries.size();
                    for (int action = 0; action < 1000; action++) {
                        assertEquals(0, rewards.addProgress(USER, "toggle_floor_item", 1));
                    }
                    assertEquals(
                            0, scalar(pool, "SELECT COUNT(*) FROM user_reward_track_progress WHERE user_id=940001"));
                    assertEquals(1, scalar(pool, "SELECT COUNT(*) FROM user_reward_track_claims WHERE user_id=940001"));
                    System.out.println("TASK_PROGRESS_LOAD actions=1000 prepared_statements="
                            + (queries.size() - beforeIgnoredActions)
                            + " pool_size=1 lost_progress=0 unexpected_claim_changes=0");
                    assertTrue(
                            queries.stream().noneMatch(sql -> sql.startsWith("SELECT 1 FROM user_daily_task_progress")),
                            "Streak calculation must not issue one SELECT for every day");
                }
            }
        }
    }

    private static void assertPoolReleased(HikariDataSource pool) throws Exception {
        assertEquals(0, pool.getHikariPoolMXBean().getActiveConnections(), "Callback retained a parent connection");
        try (var connection = pool.getConnection()) {
            assertTrue(connection.isValid(1));
        }
    }

    private static Connection observed(Connection connection, List<String> queries) {
        return (Connection) Proxy.newProxyInstance(
                Connection.class.getClassLoader(), new Class<?>[] {Connection.class}, (proxy, method, arguments) -> {
                    if (method.getName().equals("prepareStatement") && arguments[0] instanceof String sql)
                        queries.add(sql);
                    try {
                        return method.invoke(connection, arguments);
                    } catch (InvocationTargetException error) {
                        throw error.getCause();
                    }
                });
    }

    private static void seed(HikariDataSource pool) throws Exception {
        execute(pool, "UPDATE reward_tracks SET enabled=0");
        execute(
                pool,
                "INSERT INTO reward_tracks (id,code,title,description,instructions,enabled) VALUES (940001,'duck_task_perf','Perf','','',1)");
        execute(
                pool,
                "INSERT INTO reward_track_tasks (id,track_id,action_type,title,description) VALUES (940001,940001,'duck_task_perf','Perf','')");
        execute(pool, "INSERT INTO reward_track_task_levels (task_id,level,target) VALUES (940001,1,3)");
        execute(
                pool,
                "INSERT INTO reward_track_prizes (id,track_id,required_points,reward_type,reward_code) VALUES (940001,940001,3,'badge','DUCK_PERF')");
        execute(pool, "INSERT INTO user_reward_track_claims (user_id,prize_id,claimed_at) VALUES (940001,940001,1)");
        execute(
                pool,
                "INSERT INTO daily_tasks (id,code,action_type,title,target) VALUES (940001,'duck_task_perf','duck_task_perf','Perf',3),(940002,'duck_daily_only','duck_daily_only','Other',100)");
        execute(
                pool,
                "INSERT INTO user_daily_task_assignments (user_id,task_date,task_id,slot) VALUES (940001,CURRENT_DATE,940001,0),(940001,CURRENT_DATE,940002,1)");
        try (var connection = pool.getConnection();
                var insert = connection.prepareStatement(
                        "INSERT INTO user_daily_task_progress (user_id,task_id,task_date,progress,completed_at) VALUES (940001,?,?,3,1)")) {
            for (int day = 1; day <= 365; day++) {
                insert.setInt(1, TASK);
                insert.setDate(2, Date.valueOf(TODAY.minusDays(day)));
                insert.addBatch();
            }
            insert.setInt(1, OTHER_TASK);
            insert.setDate(2, Date.valueOf(TODAY.minusDays(1)));
            insert.addBatch();
            insert.setInt(1, OTHER_TASK);
            insert.setDate(2, Date.valueOf(TODAY.plusDays(1)));
            insert.addBatch();
            insert.executeBatch();
        }
    }

    private static long scalar(HikariDataSource pool, String query) throws Exception {
        try (var connection = pool.getConnection();
                var statement = connection.createStatement();
                var result = statement.executeQuery(query.replace("CURRENT_DATE", "DATE '" + TODAY + "'"))) {
            assertTrue(result.next());
            return result.getLong(1);
        }
    }

    private static void execute(HikariDataSource pool, String query) throws Exception {
        try (var connection = pool.getConnection();
                var statement = connection.createStatement()) {
            statement.execute(query.replace("CURRENT_DATE", "DATE '" + TODAY + "'"));
        }
    }
}
