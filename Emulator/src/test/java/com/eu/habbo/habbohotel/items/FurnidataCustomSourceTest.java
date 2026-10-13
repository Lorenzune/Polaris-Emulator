package com.eu.habbo.habbohotel.items;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

class FurnidataCustomSourceTest {
    private static final long MAX_BYTES = 64L * 1024 * 1024;

    private String data(int id, String classname, String name) {
        return "{\"roomitemtypes\":{\"furnitype\":[{\"id\":" + id
                + ",\"classname\":\"" + classname + "\",\"name\":\"" + name
                + "\",\"description\":\"desc\"}]},\"wallitemtypes\":{\"furnitype\":[]}}";
    }

    private FurnidataSourceResolver.Source sources(Path dir, String mainData, String customData) throws Exception {
        Path base = dir.resolve("gamedata");
        Path main = base.resolve("gamedata/FurnitureData.json");
        Path custom = base.resolve("gamedata/gamedata_custom/FurnitureData_custom.json");
        Files.createDirectories(custom.getParent());
        Files.writeString(main, mainData);
        Files.writeString(custom, customData);
        Path config = dir.resolve("renderer-config.json");
        Files.writeString(config, """
                {"gamedata.url":"https://gamedata.ducket.city/gamedata/",
                 "gamedata_custom.url":"${gamedata.url}gamedata_custom/",
                 "furnidata.url":["${gamedata.url}FurnitureData.json?t=%timestamp%",
                                 "${gamedata_custom.url}FurnitureData_custom.json?t=%timestamp%"]}
                """);
        // The configured asset base is the inner gamedata directory on this deployment.
        return FurnidataSourceResolver.resolveFromRendererConfig(config, base.resolve("gamedata"));
    }

    @Test
    void editsCustomOnlyEntryAndReindexesWithoutChangingMain(@TempDir Path dir) throws Exception {
        String mainData = data(1, "main_chair", "Main");
        var source = sources(dir, mainData, data(2, "custom_chair", "Old custom"));
        assertTrue(source.ok());
        assertEquals(2, source.paths().size());
        FurnitureTextProvider provider = new FurnitureTextProvider(true);
        provider.reindex(FurnidataReader.readSources(source.paths(), MAX_BYTES));
        assertEquals("Old custom", provider.getName("custom_chair"));

        FurnidataWriter writer = new FurnidataWriter(source.paths(), MAX_BYTES, 3);
        assertTrue(writer.write("custom_chair", "Nuovo nome", ""));
        assertEquals(mainData, Files.readString(source.paths().getFirst()));
        var delta = provider.reindex(FurnidataReader.readSources(source.paths(), MAX_BYTES));
        assertEquals(1, delta.size());
        assertEquals(2, delta.getFirst().id());
        assertEquals("Nuovo nome", provider.getName("custom_chair"));
        assertEquals("", delta.getFirst().description());
        assertTrue(writer.revertLastBackup("custom_chair"));
        assertEquals("Old custom", FurnidataReader.readSources(source.paths(), MAX_BYTES).getLast().name());
    }

    @Test
    void writesWinningCustomOverrideAndLeavesMainBackupAlone(@TempDir Path dir) throws Exception {
        String mainData = data(1, "shared_chair", "Main");
        var source = sources(dir, mainData, data(1, "shared_chair", "Custom"));
        Path main = source.paths().getFirst();
        Files.writeString(main.resolveSibling("FurnitureData.json.bak.1"), "unrelated backup");
        FurnidataWriter writer = new FurnidataWriter(source.paths(), MAX_BYTES, 3);
        assertTrue(writer.write("shared_chair", "Winner", "new desc"));
        assertEquals(mainData, Files.readString(main));
        assertEquals("Winner", FurnidataReader.readSources(source.paths(), MAX_BYTES).getLast().name());
        assertTrue(writer.revertLastBackup("shared_chair"));
        assertEquals(mainData, Files.readString(main));
    }

    @Test
    void editsMainOnlyEntryAndLeavesCustomUntouched(@TempDir Path dir) throws Exception {
        String customData = data(2, "custom_chair", "Custom");
        var source = sources(dir, data(1, "main_chair", "Old"), customData);
        assertTrue(new FurnidataWriter(source.paths(), MAX_BYTES, 3).write("main_chair", "New", ""));
        assertEquals(customData, Files.readString(source.paths().getLast()));
        assertEquals("New", FurnidataReader.readSources(source.paths(), MAX_BYTES).getFirst().name());
    }

    @Test
    void createsInLastConfiguredFileAndChecksCollisionsAcrossBoth(@TempDir Path dir) throws Exception {
        String mainData = data(1, "main_chair", "Main");
        var source = sources(dir, mainData, data(2, "custom_chair", "Custom"));
        FurnidataWriter writer = new FurnidataWriter(source.paths(), MAX_BYTES, 3);
        String entry = "{\"id\":3,\"classname\":\"new_chair\",\"name\":\"New\",\"description\":\"\"}";
        assertEquals(FurnidataWriter.CreateResult.ID_COLLISION,
                writer.create("new_chair", 1, FurnitureType.FLOOR, entry, "custom"));
        assertEquals(FurnidataWriter.CreateResult.ALREADY_EXISTS,
                writer.create("main_chair", 3, FurnitureType.FLOOR, entry, "custom"));
        assertEquals(FurnidataWriter.CreateResult.CREATED,
                writer.create("new_chair", 3, FurnitureType.FLOOR, entry, "custom"));
        assertEquals(mainData, Files.readString(source.paths().getFirst()));
        assertTrue(new FurnidataReader(source.paths().getLast(), MAX_BYTES).read().stream()
                .anyMatch(e -> e.classname().equals("new_chair")));
    }

    @Test
    void missingCustomFileDoesNotResolveAsCompleteSource(@TempDir Path dir) throws Exception {
        var source = sources(dir, data(1, "main_chair", "Main"), data(2, "custom_chair", "Custom"));
        Files.delete(source.paths().getLast());
        var missing = FurnidataSourceResolver.resolveFromRendererConfig(dir.resolve("renderer-config.json"),
                dir.resolve("gamedata/gamedata"));
        assertFalse(missing.ok());
        assertEquals(FurnidataSourceResolver.Status.SOURCE_MISSING, missing.status());
        var configured = FurnidataSourceResolver.resolveConfigured("", dir.resolve("renderer-config.json").toString(),
                dir.resolve("gamedata/gamedata").toString());
        assertFalse(configured.ok(), "must not silently fall back to the main file when custom is missing");
    }

    @Test
    void mapsSecureCustomUrlAndRejectsTraversal(@TempDir Path dir) {
        var source = FurnidataSourceResolver.toLocalSource(dir,
                "https://ducket.city:2096/nitro-sec/file?kind=gamedata&file=gamedata_custom/FurnitureData_custom.json?t=%timestamp%");
        assertEquals(dir.resolve("gamedata_custom/FurnitureData_custom.json"), source.path());
        var escape = FurnidataSourceResolver.toLocalSource(dir,
                "https://ducket.city:2096/nitro-sec/file?kind=gamedata&file=%2E%2E%2Fsecret.json");
        assertEquals(FurnidataSourceResolver.Status.ERROR, escape.status());
        assertNull(escape.path());
    }
}
