package com.eu.habbo.habbohotel.users.levels;

/** Habbo avatar levels and Ducket rewards; index zero is level one. */
public final class AvatarLevels {
    public static final int MAX_LEVEL = 30;
    public static final long HOSTING_DIVISOR = 60_000L * 1_000_000L;
    private static final int[] SCORES = {
        0, 30, 35, 45, 60, 75, 95, 115, 140, 165, 190, 215, 240, 270, 300, 340, 390, 450, 520, 600, 700, 800, 900, 1100,
        1300, 1600, 2000, 2500, 3200, 4000
    };
    private static final int[] DUCKETS = {
        0, 40, 40, 45, 45, 50, 100, 110, 110, 200, 120, 120, 120, 130, 130, 130, 140, 140, 140, 300, 225, 225, 225, 240,
        240, 240, 270, 270, 270, 300
    };

    private AvatarLevels() {}

    public static int level(int score) {
        int level = 1;
        while (level < MAX_LEVEL && score >= threshold(level + 1)) level++;
        return level;
    }

    public static int threshold(int level) {
        return SCORES[Math.clamp(level, 1, MAX_LEVEL) - 1];
    }

    public static int reward(int level) {
        return DUCKETS[Math.clamp(level, 1, MAX_LEVEL) - 1];
    }

    public static int pantheonPoints(int highestScore) {
        return Math.max(0, highestScore - threshold(MAX_LEVEL));
    }

    public static int hostingPoints(long weightedMillis) {
        return (int) Math.min(Integer.MAX_VALUE, Math.max(0, weightedMillis) / HOSTING_DIVISOR);
    }
}
