package com.eu.habbo.habbohotel.achievements;

import java.sql.ResultSet;
import java.sql.SQLException;

public class AchievementLevel {

    public final int level;


    public final int rewardAmount;


    public final int rewardType;


    public final int points;


    public final int progress;

    public AchievementLevel(ResultSet set) throws SQLException {
        this.level = set.getInt("level");
        this.rewardAmount = set.getInt("reward_amount");
        this.rewardType = set.getInt("reward_type");
        this.points = set.getInt("points");
        this.progress = set.getInt("progress_needed");
    }

    public AchievementLevel(int level, int rewardAmount, int rewardType, int points, int progress) {
        this.level = level;
        this.rewardAmount = rewardAmount;
        this.rewardType = rewardType;
        this.points = points;
        this.progress = progress;
    }
}
