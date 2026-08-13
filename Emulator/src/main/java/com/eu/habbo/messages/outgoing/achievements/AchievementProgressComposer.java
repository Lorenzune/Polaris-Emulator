package com.eu.habbo.messages.outgoing.achievements;

import com.eu.habbo.habbohotel.achievements.Achievement;
import com.eu.habbo.habbohotel.achievements.AchievementLevel;
import com.eu.habbo.habbohotel.achievements.AchievementManager;
import com.eu.habbo.habbohotel.users.Habbo;
import com.eu.habbo.messages.ServerMessage;
import com.eu.habbo.messages.outgoing.MessageComposer;
import com.eu.habbo.messages.outgoing.Outgoing;

public class AchievementProgressComposer extends MessageComposer {
    private final Habbo habbo;
    private final Achievement achievement;

    public AchievementProgressComposer(Habbo habbo, Achievement achievement) {
        this.habbo = habbo;
        this.achievement = achievement;
    }

    @Override
    protected ServerMessage composeInternal() {
        this.response.init(Outgoing.AchievementProgressComposer);

        int achievementProgress;
        AchievementLevel currentLevel;
        AchievementLevel nextLevel;

        achievementProgress = this.habbo.getHabboStats().getAchievementProgress(this.achievement);
        currentLevel = this.achievement.getLevelForProgress(achievementProgress);
        nextLevel = this.achievement.getNextLevel(currentLevel != null ? currentLevel.level : 0);

        AchievementLevel finalLevel = this.achievement.getLastLevel();

        if (currentLevel != null && finalLevel != null && currentLevel.level == finalLevel.level)
            nextLevel = null;

        int targetLevel = 1;

        if (nextLevel != null)
            targetLevel = nextLevel.level;

        if (currentLevel != null && finalLevel != null && currentLevel.level == finalLevel.level)
            targetLevel = currentLevel.level;

        AchievementLevel target = nextLevel != null ? nextLevel : currentLevel;
        int scoreAtStartOfLevel = currentLevel != null ? currentLevel.progress : 0;

        this.response.appendInt(this.achievement.id); //ID
        this.response.appendInt(targetLevel); //Target level
        this.response.appendString("ACH_" + this.achievement.name + targetLevel); //Target badge code
        this.response.appendInt(scoreAtStartOfLevel); //Score at start of current level
        this.response.appendInt(target != null ? target.progress : scoreAtStartOfLevel); //Score limit
        this.response.appendInt(target != null ? target.rewardAmount : 0); //Reward points
        this.response.appendInt(target != null ? target.rewardType : 0); //Reward point type
        this.response.appendInt(achievementProgress == -1 ? 0 : achievementProgress); //Current progress
        this.response.appendBoolean(AchievementManager.hasAchieved(this.habbo, this.achievement)); //Achieved? (Current Progress == MaxLevel.Progress)
        this.response.appendString(this.achievement.categoryCode); //Category
        this.response.appendString(this.achievement.subCategory); //Sub-category
        this.response.appendInt(this.achievement.levels.size()); //Count of total levels in this achievement
        this.response.appendInt(this.achievement.displayMethod); //SWF display method
        this.response.appendShort(this.achievement.state); //SWF lifecycle state

        return this.response;
    }

    public Habbo getHabbo() {
        return habbo;
    }

    public Achievement getAchievement() {
        return achievement;
    }
}
