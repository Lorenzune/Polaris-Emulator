package com.eu.habbo.habbohotel.wired.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.eu.habbo.database.TestDatabase;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import java.sql.SQLException;
import javax.sql.DataSource;
import org.junit.jupiter.api.Test;

class WiredLogRepositoryIT {
    @Test
    void persistsOnlyInDatabaseWithoutPruningAndRejectsFailedWrites() throws Exception {
        int roomId = java.util.concurrent.ThreadLocalRandom.current().nextInt(1_000_000, 2_000_000_000);
        String localUrl = System.getProperty("wiredLogTest.jdbcUrl");
        if (localUrl == null) org.junit.jupiter.api.Assumptions.assumeTrue(TestDatabase.dockerAvailable());
        HikariConfig config = new HikariConfig();
        if (localUrl != null) {
            config.setJdbcUrl(localUrl);
            config.setUsername("root");
            config.setPassword("");
        } else {
            config.setJdbcUrl(TestDatabase.sharedDataSource().getJdbcUrl());
            config.setUsername("polaris");
            config.setPassword("polaris");
        }
        try (HikariDataSource database = new HikariDataSource(config)) {
            try (var connection = database.getConnection();
                    var statement = connection.createStatement();
                    var migration =
                            getClass().getResourceAsStream("/db/migration/V20261002120000__wired_log_history.sql")) {
                statement.execute(new String(migration.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8));
            }
            WiredLogRepository repository = new WiredLogRepository(database);
            WiredRoomDiagnostics diagnostics =
                    new WiredRoomDiagnostics(1_000, 100, 1, 50, 150, 70, 5).attachRepository(roomId, repository);
            for (int i = 0; i < 260; i++)
                diagnostics.recordWiredLog("messaggio à 🦆 " + i, 42, WiredRoomDiagnostics.Severity.INFO);
            diagnostics.recordKilled(System.currentTimeMillis(), "system event", "System", 7);
            diagnostics.clearLogs();
            WiredRoomDiagnostics reloaded = new WiredRoomDiagnostics(1_000, 100, 1, 50, 150, 70, 5)
                    .attachRepository(roomId, new WiredLogRepository(database));
            var history =
                    reloaded.snapshot(0, 10, 0, System.currentTimeMillis()).getHistory();
            assertEquals(261, history.size());
            assertEquals("system event", history.getFirst().getReason());
            assertEquals("messaggio à 🦆 0", history.getLast().getReason());
            assertEquals(42, history.getLast().getSourceId());
            assertEquals(WiredRoomDiagnostics.Severity.INFO, history.getLast().getSeverity());
            assertTrue(repository.load(roomId + 1).isEmpty());
            var repeated = new WiredRoomDiagnostics.HistoryEntry(
                    WiredRoomDiagnostics.Type.WIRED_LOG,
                    WiredRoomDiagnostics.Severity.DEBUG,
                    System.currentTimeMillis(),
                    "retry",
                    "Wired",
                    43);
            repository.append(roomId, repeated);
            repository.append(roomId, repeated);
            assertEquals(262, repository.load(roomId).size());
            DataSource unavailable = mock(DataSource.class);
            when(unavailable.getConnection()).thenThrow(new SQLException("simulated outage"));
            WiredLogRepository failing = new WiredLogRepository(unavailable);
            assertThrows(IllegalStateException.class, () -> failing.append(roomId, repeated));
            assertThrows(IllegalStateException.class, () -> failing.load(roomId));
            doAnswer(invocation -> database.getConnection()).when(unavailable).getConnection();
            failing.append(roomId, repeated);
            assertEquals(262, failing.load(roomId).size());
        }
    }
}
