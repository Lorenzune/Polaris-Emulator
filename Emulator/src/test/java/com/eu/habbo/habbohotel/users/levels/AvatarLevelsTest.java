package com.eu.habbo.habbohotel.users.levels;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class AvatarLevelsTest {
    @Test
    void everyThresholdAndRewardMatchesThePublishedSchedule() {
        int[] thresholds = {
            0, 30, 35, 45, 60, 75, 95, 115, 140, 165, 190, 215, 240, 270, 300, 340, 390, 450, 520, 600, 700, 800, 900,
            1100, 1300, 1600, 2000, 2500, 3200, 4000
        };
        int[] rewards = {
            0, 40, 40, 45, 45, 50, 100, 110, 110, 200, 120, 120, 120, 130, 130, 130, 140, 140, 140, 300, 225, 225, 225,
            240, 240, 240, 270, 270, 270, 300
        };
        for (int level = 1; level <= 30; level++) {
            assertEquals(thresholds[level - 1], AvatarLevels.threshold(level));
            assertEquals(rewards[level - 1], AvatarLevels.reward(level));
            assertEquals(level, AvatarLevels.level(thresholds[level - 1]));
            if (level > 1) assertEquals(level - 1, AvatarLevels.level(thresholds[level - 1] - 1));
        }
        assertEquals(1, AvatarLevels.level(-10));
        assertEquals(30, AvatarLevels.level(Integer.MAX_VALUE));
    }

    @Test
    void capsEachCategoryAgainstPreviousContributionsAndSaturatesTotals() {
        var score = new AvatarLevelScore(1046, 484, 1, 1681);
        assertArrayEquals(new int[] {1046, 484, 1, 1531}, score.counted());
        assertEquals(3062, score.total());
        assertArrayEquals(new int[] {0, 0, 0, 0}, new AvatarLevelScore(-1, 500, 300, 100).counted());
        assertEquals(
                Integer.MAX_VALUE,
                new AvatarLevelScore(Integer.MAX_VALUE, Integer.MAX_VALUE, Integer.MAX_VALUE, Integer.MAX_VALUE)
                        .total());
        int[] copy = score.raw();
        copy[0] = 0;
        assertEquals(1046, score.achievements());
    }

    @Test
    void sellingCollectionsRetainsLevelAndPantheonHighWaterMark() {
        var retained = AvatarLevelSnapshot.advance(7, 28, 3062, new AvatarLevelScore(28, 0, 0, 0));
        assertEquals(28, retained.level());
        assertEquals(3062, retained.highestScore());
        assertEquals(29, retained.nextLevel());
        assertEquals(3200, retained.nextScore());
        assertEquals(270, retained.nextReward());
        assertEquals(0, AvatarLevels.pantheonPoints(3999));
        assertEquals(2, AvatarLevels.pantheonPoints(4002));
        var maximum = AvatarLevelSnapshot.advance(7, 29, 3200, new AvatarLevelScore(4002, 0, 0, 0));
        assertEquals(30, maximum.level());
        assertEquals(0, maximum.nextLevel());
        assertEquals(0, maximum.nextScore());
        assertEquals(0, maximum.nextReward());
    }

    @Test
    void hostingRetainsFractionalProgressAndClampsNegativeInput() {
        assertEquals(0, AvatarLevels.hostingPoints(-1));
        assertEquals(0, AvatarLevels.hostingPoints(AvatarLevels.HOSTING_DIVISOR - 1));
        assertEquals(1, AvatarLevels.hostingPoints(AvatarLevels.HOSTING_DIVISOR));
        assertEquals(2, AvatarLevels.hostingPoints(AvatarLevels.HOSTING_DIVISOR * 2 + 5));
    }
}
