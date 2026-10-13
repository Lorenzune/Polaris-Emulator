package com.eu.habbo.habbohotel.items.editor;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import org.junit.jupiter.api.Test;
import org.mariadb.jdbc.MariaDbDataSource;

class FurnitureValueLocalTest {
    @Test
    void readsKimonoAhriFromTheAuthorizedLocalDatabase() throws Exception {
        String configPath = System.getProperty("ducket.rare.test.config", "");
        assumeTrue(!configPath.isBlank(), "Opt-in local database check");
        var config = new HashMap<String, String>();
        for (String line : Files.readAllLines(Path.of(configPath), StandardCharsets.UTF_8)) {
            if (line.stripLeading().startsWith("#") || line.stripLeading().startsWith(";")) continue;
            int equals = line.indexOf('=');
            if (equals > 0)
                config.put(
                        line.substring(0, equals).trim(),
                        line.substring(equals + 1).trim());
        }
        var ds = new MariaDbDataSource();
        ds.setUrl("jdbc:mariadb://" + config.getOrDefault("db.hostname", "127.0.0.1") + ":"
                + config.getOrDefault("db.port", "3306") + "/" + config.get("db.database"));
        ds.setUser(config.get("db.username"));
        ds.setPassword(config.getOrDefault("db.password", ""));
        if (Boolean.getBoolean("ducket.rare.test.backfill"))
            com.eu.habbo.habbohotel.catalog.RareEconomyRepository.backfillCatalogHistory(ds);
        var value = new FurnitureValueRepository(ds).find(2000032719, "s");
        assertEquals(100.0, ((Number) value.get("value")).doubleValue());
        assertEquals(5, value.get("valueType"));
        assertEquals(true, value.get("isRare"));
        assertEquals(
                false, new FurnitureValueRepository(ds).find(2000032719, "i").get("isRare"));
        String output = System.getProperty("ducket.rare.test.output", "");
        if (!output.isBlank())
            Files.writeString(
                    Path.of(output),
                    new com.google.gson.GsonBuilder()
                            .setPrettyPrinting()
                            .create()
                            .toJson(value),
                    StandardCharsets.UTF_8);
    }
}
