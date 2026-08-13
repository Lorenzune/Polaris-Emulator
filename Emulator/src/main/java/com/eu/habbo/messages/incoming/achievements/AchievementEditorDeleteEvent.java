package com.eu.habbo.messages.incoming.achievements;

import com.eu.habbo.Emulator;
import com.eu.habbo.habbohotel.achievements.Achievement;
import com.eu.habbo.habbohotel.permissions.Permission;
import com.eu.habbo.messages.incoming.MessageHandler;
import com.eu.habbo.messages.outgoing.achievements.AchievementEditorResultComposer;

import java.sql.Connection;
import java.sql.PreparedStatement;

public class AchievementEditorDeleteEvent extends MessageHandler {
    @Override
    public void handle() throws Exception {
        if (!this.client.getHabbo().hasPermission(Permission.ACC_ACHIEVEMENT_EDITOR)) return;

        int achievementId = this.packet.readInt();
        Achievement achievement = Emulator.getGameEnvironment().getAchievementManager().getAchievement(achievementId);
        if (achievement == null) {
            this.client.sendResponse(new AchievementEditorResultComposer(false, "Traguardo non trovato", achievementId));
            return;
        }

        try (Connection connection = Emulator.getDatabase().getDataSource().getConnection()) {
            connection.setAutoCommit(false);
            try (PreparedStatement statement = connection.prepareStatement("DELETE FROM achievements WHERE name = ?")) {
                statement.setString(1, achievement.name);
                statement.executeUpdate();
            }
            try (PreparedStatement statement = connection.prepareStatement("DELETE FROM users_achievements WHERE achievement_name = ?")) {
                statement.setString(1, achievement.name);
                statement.executeUpdate();
            }
            connection.commit();
        }

        Emulator.getGameEnvironment().getAchievementManager().reload();
        this.client.sendResponse(new AchievementEditorResultComposer(true, "Traguardo eliminato", achievementId));
        AchievementEditorSupport.sendState(this.client);
    }
}
