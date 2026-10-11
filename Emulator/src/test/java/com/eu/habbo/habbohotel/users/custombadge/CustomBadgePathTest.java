package com.eu.habbo.habbohotel.users.custombadge;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.CALLS_REAL_METHODS;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class CustomBadgePathTest {
    @TempDir
    Path directory;

    @Test
    void rejectsTraversalAndOnlyDeletesAnOwnedBadgeName() throws Exception {
        CustomBadgeManager manager = mock(CustomBadgeManager.class, CALLS_REAL_METHODS);
        CustomBadgeSettings settings = mock(CustomBadgeSettings.class);
        Path storage = Files.createDirectory(directory.resolve("badges"));
        Path victim = Files.writeString(directory.resolve("victim.gif"), "untouched");
        Path valid = Files.writeString(storage.resolve("CUSTABCDE-1700000000.gif"), "badge");
        when(settings.getBadgePath()).thenReturn(storage.toString());
        var field = CustomBadgeManager.class.getDeclaredField("settings");
        field.setAccessible(true);
        field.set(manager, settings);
        var deletion = CustomBadgeManager.class.getDeclaredMethod("deleteBadgeFileQuietly", String.class);
        deletion.setAccessible(true);
        for (String id : new String[] {"../victim", "..\\victim", "CUSTABCDE-1/../../victim", "CUSTABCDE-1\n"}) {
            assertFalse(manager.isCustomBadgeId(id));
            deletion.invoke(manager, id);
        }
        assertTrue(Files.exists(victim));
        assertTrue(Files.exists(valid));
        deletion.invoke(manager, "CUSTABCDE-1700000000");
        assertFalse(Files.exists(valid));
        assertTrue(Files.exists(victim));
    }
}
