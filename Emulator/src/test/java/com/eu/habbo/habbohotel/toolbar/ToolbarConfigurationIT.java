package com.eu.habbo.habbohotel.toolbar;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

import com.eu.habbo.Emulator;
import com.eu.habbo.database.Database;
import com.eu.habbo.database.TestDatabase;
import com.eu.habbo.database.migration.MigrationRunner;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;

class ToolbarConfigurationIT {
    @Test
    void durableConfigurationRejectsStaleWritersAndSurvivesMigrationReplay() throws Exception {
        if (!TestDatabase.dockerAvailable()) {
            if ("true".equalsIgnoreCase(System.getenv("CI"))) throw new AssertionError("Docker required in CI");
            assumeTrue(false, "Docker unavailable");
        }
        try (var source = TestDatabase.freshDatabase("toolbar_configuration")) {
            MigrationRunner.migrate(source);
            var database = mock(Database.class);
            when(database.getDataSource()).thenReturn(source);
            var configuration = ToolbarConfiguration.parse("""
                {"version":1,"currencies":[{"type":42,"label":"Evento","icon":"/42.webp","color":"#bb77cc","visible":true,"placement":"bubble"}],"widgets":{"radio":false},"bubbles":[{"id":"campaign","title":"Evento programmato","image":"","link":"navigator/show","color":"#336699","visible":true,"dismissible":false,"startsAt":1800000000,"endsAt":1800003600,"width":232,"height":100,"linkLabel":"Scopri evento"}]}
                """);
            try (var emulator = mockStatic(Emulator.class)) {
                emulator.when(Emulator::getDatabase).thenReturn(database);
                assertNull(
                        ToolbarConfigurationStore.load().configuration(),
                        "no configured row preserves existing client defaults");
                assertTrue(ToolbarConfigurationStore.save(0, configuration, 99));
                assertEquals(1, ToolbarConfigurationStore.load().revision());
                assertEquals(configuration, ToolbarConfigurationStore.load().configuration());
                assertFalse(
                        ToolbarConfigurationStore.save(0, configuration, 100),
                        "a stale editor cannot overwrite another save");
                try (var db = source.getConnection();
                        var sql = db.createStatement()) {
                    sql.executeUpdate(
                            "UPDATE permission_definitions SET rank_7=0 WHERE permission_key='acc_hk_toolbar'");
                    String migration;
                    try (var input = getClass()
                            .getResourceAsStream("/db/migration/V20261014100000__toolbar_configuration.sql")) {
                        migration = new String(input.readAllBytes(), StandardCharsets.UTF_8);
                    }
                    for (String statement :
                            migration.replaceAll("(?m)^\\s*--[^\\r\\n]*", "").split(";"))
                        if (!statement.isBlank()) sql.execute(statement);
                    try (var rows = sql.executeQuery(
                            "SELECT rank_7 FROM permission_definitions WHERE permission_key='acc_hk_toolbar'")) {
                        assertTrue(rows.next());
                        assertEquals(0, rows.getInt(1));
                    }
                }
                assertEquals(new ToolbarConfigurationStore.State(1, configuration), ToolbarConfigurationStore.load());
            }
        }
    }
}
