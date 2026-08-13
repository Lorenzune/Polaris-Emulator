package com.eu.habbo.messages.outgoing.achievements;

import com.eu.habbo.Emulator;
import com.eu.habbo.habbohotel.achievements.Achievement;
import com.eu.habbo.habbohotel.achievements.AchievementLevel;
import com.eu.habbo.habbohotel.achievements.AchievementManager;
import com.eu.habbo.habbohotel.users.Habbo;
import com.eu.habbo.messages.ServerMessage;
import com.eu.habbo.messages.outgoing.MessageComposer;
import com.eu.habbo.messages.outgoing.Outgoing;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class AchievementListComposer extends MessageComposer {
    private static final Logger LOGGER = LoggerFactory.getLogger(AchievementListComposer.class);

    private final Habbo habbo;

    public AchievementListComposer(Habbo habbo) {
        this.habbo = habbo;
    }

    @Override
    protected ServerMessage composeInternal() {
        this.response.init(Outgoing.AchievementListComposer);

        try {
            this.response.appendInt(Emulator.getGameEnvironment().getAchievementManager().getAchievements().size());

            for (Achievement achievement : Emulator.getGameEnvironment().getAchievementManager().getAchievements().values()) {
                int achievementProgress;
                AchievementLevel currentLevel;
                AchievementLevel nextLevel;

                achievementProgress = this.habbo.getHabboStats().getAchievementProgress(achievement);
                currentLevel = achievement.getLevelForProgress(achievementProgress);
                nextLevel = achievement.getNextLevel(currentLevel != null ? currentLevel.level : 0);
                boolean achieved = AchievementManager.hasAchieved(this.habbo, achievement);
                AchievementLevel targetLevel = nextLevel != null ? nextLevel : currentLevel;
                int targetLevelNumber = targetLevel != null ? targetLevel.level : 1;
                int scoreAtStartOfLevel = currentLevel != null ? currentLevel.progress : 0;

                this.response.appendInt(achievement.id); //ID
                this.response.appendInt(targetLevelNumber); // Target level
                this.response.appendString("ACH_" + achievement.name + targetLevelNumber); // Target badge code
                this.response.appendInt(scoreAtStartOfLevel); // Score at start of current level
                this.response.appendInt(targetLevel != null ? targetLevel.progress : scoreAtStartOfLevel); // Score limit
                this.response.appendInt(targetLevel != null ? targetLevel.rewardAmount : 0); // Reward points
                this.response.appendInt(targetLevel != null ? targetLevel.rewardType : 0); // Reward point type
                this.response.appendInt(Math.max(achievementProgress, 0)); //Current progress
                this.response.appendBoolean(achieved); // Final level
                this.response.appendString(achievement.categoryCode); // Category
                this.response.appendString(achievement.subCategory); // Sub-category
                this.response.appendInt(achievement.levels.size()); //Count of total levels in this achievement
                this.response.appendInt(achievement.displayMethod); // SWF display method
                this.response.appendShort(achievement.state); // SWF lifecycle state
            }

            this.response.appendString("");
        } catch (Exception e) {
            LOGGER.error("Caught exception", e);
        }

        return this.response;
    }

    public Habbo getHabbo() {
        return habbo;
    }
}
