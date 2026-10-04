package com.eu.habbo.habbohotel.achievements;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;

import java.io.Reader;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class AchievementSourceTest {
    @Test
    void fallsBackToBundledCatalogWithArchivedAchievements() throws Exception {
        try (Reader reader = AchievementManager.openAchievementSource(Path.of("missing-achievements.json"))) {
            assertNotNull(reader);
            JsonArray entries = JsonParser.parseReader(reader).getAsJsonArray();
            long archived = 0;

            for (JsonElement entry : entries) {
                if ("ARCHIVED".equals(entry.getAsJsonObject().getAsJsonObject("achievement").get("state").getAsString())) {
                    archived++;
                }
            }

            assertEquals(14, archived);
        }
    }
}
