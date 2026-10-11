package com.eu.habbo.database.migration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.nio.charset.StandardCharsets;
import java.sql.DriverManager;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class PetAssetPaletteMigrationTest {
    @Test
    void migrationCanBeReplayedWithoutReplacingCustomRowsOrRequiringUniqueKey() throws Exception {
        String url = System.getenv("POLARIS_PET_MIGRATION_TEST_URL");
        assumeTrue(url != null && !url.isBlank(), "Requires a disposable MariaDB test server");
        String schema = "pet_palette_test_" + UUID.randomUUID().toString().replace("-", "");
        try (var connection = DriverManager.getConnection(url, "root", "");
                var statement = connection.createStatement()) {
            statement.execute("CREATE DATABASE `" + schema + "`");
            try {
                connection.setCatalog(schema);
                statement.execute(
                        "CREATE TABLE pet_breeds(race INT, color_one INT, color_two INT, has_color_one CHAR(1), has_color_two CHAR(1), custom_note VARCHAR(30) DEFAULT 'new')");
                statement.execute("INSERT INTO pet_breeds VALUES(8,12,12,'1','0','custom'),(99,7,7,'1','0','hotel')");
                String sql;
                try (var input = getClass()
                        .getResourceAsStream("/db/migration/V20261011140000__pet_breeds_asset_palettes.sql")) {
                    assertNotNull(input);
                    sql = new String(input.readAllBytes(), StandardCharsets.UTF_8);
                }
                for (int replay = 0; replay < 2; replay++) {
                    for (String part :
                            sql.replaceAll("(?m)^\\s*--[^\\r\\n]*", "").split(";")) {
                        if (!part.isBlank()) statement.execute(part);
                    }
                    try (var result = statement.executeQuery("SELECT COUNT(*) FROM pet_breeds")) {
                        assertTrue(result.next());
                        assertEquals(30, result.getInt(1));
                    }
                    try (var result = statement.executeQuery(
                            "SELECT custom_note FROM pet_breeds WHERE race=8 AND color_one=12")) {
                        assertTrue(result.next());
                        assertEquals("custom", result.getString(1));
                    }
                    try (var result = statement.executeQuery("SELECT custom_note FROM pet_breeds WHERE race=99")) {
                        assertTrue(result.next());
                        assertEquals("hotel", result.getString(1));
                    }
                }
            } finally {
                statement.execute("DROP DATABASE `" + schema + "`");
            }
        }
    }
}
