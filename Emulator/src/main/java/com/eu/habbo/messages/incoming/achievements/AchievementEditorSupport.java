package com.eu.habbo.messages.incoming.achievements;

import com.eu.habbo.Emulator;
import com.eu.habbo.habbohotel.achievements.Achievement;
import com.eu.habbo.habbohotel.permissions.Permission;
import com.eu.habbo.habbohotel.gameclients.GameClient;
import com.eu.habbo.messages.outgoing.achievements.AchievementEditorStateComposer;

import java.util.ArrayList;
import java.util.Comparator;

final class AchievementEditorSupport {
    private AchievementEditorSupport() { }

    static boolean canEdit(GameClient client) {
        return client != null && client.getHabbo() != null
                && client.getHabbo().hasPermission(Permission.ACC_ACHIEVEMENT_EDITOR);
    }

    static void sendState(GameClient client) {
        if (client == null || client.getHabbo() == null) return;

        ArrayList<Achievement> achievements = new ArrayList<>(
                Emulator.getGameEnvironment().getAchievementManager().getAchievements().values());
        achievements.sort(Comparator.comparing(achievement -> achievement.name.toLowerCase(java.util.Locale.ROOT)));
        client.sendResponse(new AchievementEditorStateComposer(canEdit(client), achievements));
    }
}
