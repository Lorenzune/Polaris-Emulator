package com.eu.habbo.habbohotel.users;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class BadgeRealtimePersistenceContractTest {

    @Test
    void wearingBadgesCommitAtomicallyBeforePublishingTheirState() throws Exception {
        String event = Files.readString(Path.of(
                "src/main/java/com/eu/habbo/messages/incoming/users/UserWearBadgeEvent.java"));
        String badge = Files.readString(Path.of(
                "src/main/java/com/eu/habbo/habbohotel/users/HabboBadge.java"));

        int replace = event.indexOf("replaceWearingBadges(");
        int synchronize = event.indexOf("synchronizeBadgeState(", replace);
        int reset = badge.indexOf("UPDATE users_badges SET slot_id = 0");
        int equip = badge.indexOf("UPDATE users_badges SET slot_id = ?", reset);
        int commit = badge.indexOf("connection.commit()", equip);

        assertTrue(replace > -1);
        assertTrue(synchronize > replace);
        assertFalse(event.contains("getThreading().run(badge)"));
        assertTrue(reset > -1);
        assertTrue(equip > reset);
        assertTrue(commit > equip);
        assertTrue(badge.contains("connection.rollback()"));
    }

    @Test
    void achievementBadgePersistsBeforeRealtimeInventoryPublication() throws Exception {
        String source = Files.readString(Path.of(
                "src/main/java/com/eu/habbo/habbohotel/achievements/AchievementManager.java"));
        int newCode = source.indexOf("String newBadgCode");
        int persist = source.indexOf("badge.run()", newCode);
        int synchronize = source.indexOf("synchronizeBadgeState(habbo)", persist);
        int unseenItem = source.indexOf("new AddHabboItemComposer", synchronize);

        assertTrue(newCode > -1);
        assertTrue(persist > newCode);
        assertTrue(synchronize > persist);
        assertTrue(unseenItem > synchronize);
        assertFalse(source.contains("hasBadge(newBadgCode)) return"));
    }
}
