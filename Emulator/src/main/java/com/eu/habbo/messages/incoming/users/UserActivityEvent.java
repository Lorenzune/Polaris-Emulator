package com.eu.habbo.messages.incoming.users;

import com.eu.habbo.Emulator;
import com.eu.habbo.habbohotel.achievements.AchievementManager;
import com.eu.habbo.habbohotel.achievements.AchievementInteractions;
import com.eu.habbo.messages.incoming.MessageHandler;

public class UserActivityEvent extends MessageHandler {
    @Override
    public int getRatelimit() {
        return 250;
    }

    @Override
    public void handle() throws Exception {
        String type = this.packet.readString();
        String value = this.packet.readString();
        String action = this.packet.readString();

        if ("settings.terms.and.conditions.seen".equals(action)
                || "settings.help.terms.and.conditions.seen".equals(action)
                || "settings.help.terms_of_service.seen".equals(action)) {
            AchievementInteractions.progress(this.client.getHabbo(), "FairPlay");
        }

        if (type.equals("Quiz")) {
            if (value.equalsIgnoreCase("7")) {
                AchievementManager.progressAchievement(this.client.getHabbo(), Emulator.getGameEnvironment().getAchievementManager().getAchievement("SafetyQuizGraduate"));
            }
        }

        switch (action) {
            case "achievements.archive.opened":
                AchievementInteractions.progress(this.client.getHabbo(), "VicGar");
                break;
            case "forum.can.read.seen":
                AchievementManager.progressAchievement(this.client.getHabbo(), Emulator.getGameEnvironment().getAchievementManager().getAchievement("SelfModForumCanReadSeen"));
                break;
            case "forum.can.post.seen":
                AchievementManager.progressAchievement(this.client.getHabbo(), Emulator.getGameEnvironment().getAchievementManager().getAchievement("SelfModForumCanPostSeen"));
                break;
            case "forum.can.start.thread.seen":
                AchievementManager.progressAchievement(this.client.getHabbo(), Emulator.getGameEnvironment().getAchievementManager().getAchievement("SelfModForumCanPostThrdSeen"));
                break;
            case "forum.can.moderate.seen":
                AchievementManager.progressAchievement(this.client.getHabbo(), Emulator.getGameEnvironment().getAchievementManager().getAchievement("SelfModForumCanModerateSeen"));
                break;
            case "room.settings.doormode.seen":
                AchievementManager.progressAchievement(this.client.getHabbo(), Emulator.getGameEnvironment().getAchievementManager().getAchievement("SelfModDoorModeSeen"));
                break;
            case "room.settings.walkthrough.seen":
                AchievementManager.progressAchievement(this.client.getHabbo(), Emulator.getGameEnvironment().getAchievementManager().getAchievement("SelfModWalkthroughSeen"));
                break;
            case "room.settings.chat.scrollspeed.seen":
                AchievementManager.progressAchievement(this.client.getHabbo(), Emulator.getGameEnvironment().getAchievementManager().getAchievement("SelfModChatScrollSpeedSeen"));
                break;
            case "room.settings.chat.hearrange.seen":
                AchievementManager.progressAchievement(this.client.getHabbo(), Emulator.getGameEnvironment().getAchievementManager().getAchievement("SelfModChatHearRangeSeen"));
                break;
            case "room.settings.chat.floodfilter.seen":
                AchievementManager.progressAchievement(this.client.getHabbo(), Emulator.getGameEnvironment().getAchievementManager().getAchievement("SelfModChatFloodFilterSeen"));
                break;
        }
    }
}
