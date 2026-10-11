package com.eu.habbo.habbohotel.users.levels;

import com.eu.habbo.habbohotel.achievements.Achievement;
import com.eu.habbo.habbohotel.achievements.AchievementLevel;
import java.util.Map;

/** Avatar progression counts earned levels, not the legacy achievement reward points. */
public record AvatarLevelScore(int achievements, int badges, int hosting, int furni) {
    public AvatarLevelScore {
        achievements = Math.max(0, achievements);
        badges = Math.max(0, badges);
        hosting = Math.max(0, hosting);
        furni = Math.max(0, furni);
    }

    public int[] raw() {
        return new int[] {achievements, badges, hosting, furni};
    }

    public int[] counted() {
        int[] result = raw();
        long previous = result[0];
        for (int index = 1; index < result.length; index++) {
            result[index] = (int) Math.min(previous, result[index]);
            previous += result[index];
        }
        return result;
    }

    public int total() {
        long total = 0;
        for (int count : counted()) total += count;
        return (int) Math.min(Integer.MAX_VALUE, total);
    }

    public static int achievementPoints(Map<Achievement, Integer> progress) {
        long points = 0;
        for (var entry : progress.entrySet()) {
            Achievement achievement = entry.getKey();
            if (achievement == null || achievement.state <= 0 || entry.getValue() == null) continue;
            AchievementLevel level = achievement.getLevelForProgress(entry.getValue());
            if (level != null) points += level.level;
        }
        return (int) Math.min(Integer.MAX_VALUE, points);
    }
}
