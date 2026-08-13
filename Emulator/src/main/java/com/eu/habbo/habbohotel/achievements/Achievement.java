package com.eu.habbo.habbohotel.achievements;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.HashMap;
import java.util.Map;
import java.util.Locale;

public class Achievement {

    public final int id;


    public final String name;


    public final AchievementCategories category;

    /** The exact category code consumed by the SWF client. */
    public final String categoryCode;

    /** SWF achievement lifecycle state: enabled, archived, off-season, etc. */
    public final short state;

    public final String subCategory;

    public final int displayMethod;


    public final Map<Integer, AchievementLevel> levels;


    public Achievement(ResultSet set) throws SQLException {
        this.levels = new HashMap<>();

        this.id = set.getInt("id");
        this.name = set.getString("name");
        this.categoryCode = normalizeCategory(set.getString("category"));
        this.category = toCategory(this.categoryCode);
        this.state = 1;
        this.subCategory = "";
        this.displayMethod = 0;

        this.addLevel(new AchievementLevel(set));
    }

    public Achievement(int id, String name, String categoryCode, short state, String subCategory, int displayMethod) {
        this.id = id;
        this.name = name;
        this.categoryCode = normalizeCategory(categoryCode);
        this.category = toCategory(this.categoryCode);
        this.state = state;
        this.subCategory = subCategory == null ? "" : subCategory;
        this.displayMethod = displayMethod;
        this.levels = new HashMap<>();
    }

    private static String normalizeCategory(String category) {
        if (category == null || category.isBlank()) return "other";
        return category.toLowerCase(Locale.ROOT);
    }

    private static AchievementCategories toCategory(String category) {
        try {
            return AchievementCategories.valueOf(category.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException exception) {
            return AchievementCategories.OTHER;
        }
    }


    public void addLevel(AchievementLevel level) {
        synchronized (this.levels) {
            this.levels.put(level.level, level);
        }
    }


    public AchievementLevel getLevelForProgress(int progress) {
        AchievementLevel l = null;
        if (progress > 0) {
            for (AchievementLevel level : this.levels.values()) {
                if (progress >= level.progress) {
                    if (l != null) {
                        if (l.level > level.level) {
                            continue;
                        }
                    }

                    l = level;
                }
            }
        }
        return l;
    }


    public AchievementLevel getNextLevel(int currentLevel) {

        for (AchievementLevel level : this.levels.values()) {
            if (level.level == (currentLevel + 1))
                return level;
        }

        return null;
    }

    public AchievementLevel firstLevel() {
        return this.levels.get(1);
    }

    public void clearLevels() {
        this.levels.clear();
    }

    public AchievementLevel getLastLevel() {
        AchievementLevel result = null;
        for (AchievementLevel level : this.levels.values()) {
            if (result == null || level.level > result.level) result = level;
        }
        return result;
    }
}
