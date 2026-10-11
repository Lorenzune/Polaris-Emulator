package com.eu.habbo.habbohotel.collectibles;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.Test;

class CollectibleRewardTest {
    @Test
    void acceptsBadgeRewardsAndRejectsInvalidCodesOrDuplicateQuantities() {
        assertDoesNotThrow(
                () -> new CollectibleReward("Badge", "", "badge", 0, 105, 1, 50, 200, List.of(), null, "DUCKET_01")
                        .validate(100));
        for (String code : List.of("", "../BADGE", "a".repeat(51)))
            assertThrows(
                    IllegalArgumentException.class,
                    () -> new CollectibleReward("Badge", "", "badge", 0, 105, 1, 50, 200, List.of(), null, code)
                            .validate(100));
        assertThrows(
                IllegalArgumentException.class,
                () -> new CollectibleReward("Badge", "", "badge", 0, 105, 2, 50, 200, List.of(), null, "DUCKET")
                        .validate(100));
    }

    @Test
    void categoryRewardMayHaveNoRedemptionDeadline() {
        var reward = new CollectibleReward("P", "C", "currency", 0, -1, 1, 0, 0, List.of());
        assertDoesNotThrow(() -> reward.validate(100));
        assertTrue(reward.availableTo(7, 9999999999L, true, false));
        assertFalse(reward.availableTo(7, 100, true, true));
    }

    @Test
    void levelAudienceMatchesExactlyAndIncludesLevelZero() {
        var reward = new CollectibleReward("P", "C", "currency", 0, -1, 1, 0, 200, List.of(), 2);
        assertTrue(reward.availableTo(7, 2, 100, true, false));
        assertFalse(reward.availableTo(7, 1, 100, true, false));
        assertFalse(reward.availableTo(7, 3, 100, true, false));
        var zero = new CollectibleReward("P", "C", "currency", 0, -1, 1, 0, 200, List.of(), 0);
        assertTrue(zero.availableTo(7, 0, 100, true, false));
        assertFalse(zero.availableTo(7, 1, 100, true, false));
        assertThrows(
                IllegalArgumentException.class,
                () -> new CollectibleReward("P", "C", "currency", 0, -1, 1, 0, 200, List.of(), -1).validate(100));
        assertThrows(
                IllegalArgumentException.class,
                () -> new CollectibleReward("P", "C", "currency", 0, -1, 1, 0, 200, List.of(7), 2).validate(100));
    }

    @Test
    void existingRewardJsonKeepsAnUnrestrictedLevelAudience() {
        var reward = new com.google.gson.Gson()
                .fromJson(
                        "{\"title\":\"P\",\"collection\":\"C\",\"kind\":\"currency\",\"currencyType\":-1,\"amount\":1,\"expiresAt\":200,\"recipients\":[]}",
                        CollectibleReward.class);
        assertNull(reward.requiredLevel());
        assertTrue(reward.availableTo(7, 3, 100, true, false));
    }

    private CollectibleReward reward(List<Integer> recipients) {
        return new CollectibleReward("Premio", "Ottobre", "currency", 0, -1, 250, 100, 200, recipients);
    }

    @Test
    void expiresAtTheExactDeadlineAndCannotBeClaimedTwice() {
        var reward = reward(List.of());
        assertTrue(reward.availableTo(7, 199, true, false));
        assertFalse(reward.availableTo(7, 200, true, false));
        assertFalse(reward.availableTo(7, 100, false, false));
        assertFalse(reward.availableTo(7, 100, true, true));
    }

    @Test
    void targetedRewardsAreNotClaimableByOtherUsers() {
        assertTrue(reward(List.of(7, 8)).availableTo(7, 100, true, false));
        assertFalse(reward(List.of(7, 8)).availableTo(9, 100, true, false));
    }

    @Test
    void rejectsExpiredRewardsInvalidAmountsAndDuplicateRecipients() {
        assertDoesNotThrow(() -> reward(List.of()).validate(100));
        assertThrows(IllegalArgumentException.class, () -> reward(List.of()).validate(200));
        assertThrows(IllegalArgumentException.class, () -> reward(List.of(7, 7)).validate(100));
        assertThrows(
                IllegalArgumentException.class,
                () -> new CollectibleReward("P", "C", "item", 1, 0, 101, 0, 200, List.of()).validate(100));
        assertThrows(
                IllegalArgumentException.class,
                () -> new CollectibleReward("P", "C", "currency", 0, -1, -1, 0, 200, List.of()).validate(100));
    }

    @Test
    void levelProgressionUsesCumulativeXpAndHandlesBoundaries() {
        assertEquals(0, CollectibleReward.level(99));
        assertEquals(1, CollectibleReward.level(100));
        assertEquals(1, CollectibleReward.level(299));
        assertEquals(2, CollectibleReward.level(300));
        assertEquals(3, CollectibleReward.level(600));
        assertEquals(100, CollectibleReward.nextLevelXp(0));
        assertEquals(300, CollectibleReward.nextLevelXp(100));
        assertEquals(1000, CollectibleReward.nextLevelXp(600));
    }
}
