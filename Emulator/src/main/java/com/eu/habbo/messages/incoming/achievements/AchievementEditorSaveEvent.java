package com.eu.habbo.messages.incoming.achievements;

import com.eu.habbo.Emulator;
import com.eu.habbo.habbohotel.achievements.Achievement;
import com.eu.habbo.habbohotel.permissions.Permission;
import com.eu.habbo.messages.incoming.MessageHandler;
import com.eu.habbo.messages.outgoing.achievements.AchievementEditorResultComposer;
import com.google.gson.JsonArray;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.HashSet;
import java.util.Set;
import java.util.regex.Pattern;

public class AchievementEditorSaveEvent extends MessageHandler {
    private static final int MAX_LEVELS = 50;
    private static final Pattern SAFE_NAME = Pattern.compile("[A-Za-z0-9_.-]{1,64}");
    private static final Pattern SAFE_CATEGORY = Pattern.compile("[a-z0-9_]{1,64}");

    @Override
    public void handle() throws Exception {
        if (!this.client.getHabbo().hasPermission(Permission.ACC_ACHIEVEMENT_EDITOR)) return;

        int achievementId = this.packet.readInt();
        String name = this.packet.readString();
        String category = this.packet.readString();
        short state = (short) this.packet.readShort();
        int levelCount = this.packet.readInt();

        if (achievementId < 0 || !validName(name) || !validCategory(category)
                || state < -1 || state > 4 || levelCount < 1 || levelCount > MAX_LEVELS) {
            fail("Dati traguardo non validi", achievementId);
            return;
        }

        JsonArray levels = new JsonArray();
        Set<Integer> seenLevels = new HashSet<>();
        int previousProgress = -1;
        for (int i = 0; i < levelCount; i++) {
            int level = this.packet.readInt();
            int progress = this.packet.readInt();
            int rewardAmount = this.packet.readInt();
            int rewardType = this.packet.readInt();
            int points = this.packet.readInt();

            if (level < 1 || progress < 0 || rewardAmount < 0 || rewardType < 0 || points < 0
                    || !seenLevels.add(level) || progress < previousProgress) {
                fail("Livelli traguardo non validi", achievementId);
                return;
            }
            previousProgress = progress;

            com.google.gson.JsonObject levelObject = new com.google.gson.JsonObject();
            levelObject.addProperty("level", level);
            levelObject.addProperty("progress", progress);
            levelObject.addProperty("rewardAmount", rewardAmount);
            levelObject.addProperty("rewardType", rewardType);
            levelObject.addProperty("points", points);
            levels.add(levelObject);
        }

        Achievement existing = achievementId > 0
                ? Emulator.getGameEnvironment().getAchievementManager().getAchievement(achievementId)
                : null;
        String oldName = existing == null ? null : existing.name;
        int savedId = achievementId;

        try (Connection connection = Emulator.getDatabase().getDataSource().getConnection()) {
            connection.setAutoCommit(false);
            try {
                if (savedId <= 0) {
                    try (PreparedStatement statement = connection.prepareStatement(
                            "SELECT COALESCE(MAX(id), 0) + 1 FROM achievements");
                         ResultSet result = statement.executeQuery()) {
                        if (!result.next()) throw new IllegalStateException("id");
                        savedId = result.getInt(1);
                    }
                } else if (oldName == null) {
                    fail("Traguardo non trovato", achievementId);
                    connection.rollback();
                    return;
                }

                if (oldName != null) {
                    try (PreparedStatement statement = connection.prepareStatement(
                            "DELETE FROM achievements WHERE name = ?")) {
                        statement.setString(1, oldName);
                        statement.executeUpdate();
                    }
                }

                int nextId = savedId;
                try (PreparedStatement statement = connection.prepareStatement("SELECT COALESCE(MAX(id), 0) FROM achievements");
                     ResultSet result = statement.executeQuery()) {
                    if (result.next()) nextId = Math.max(nextId, result.getInt(1));
                }
                try (PreparedStatement statement = connection.prepareStatement(
                        "INSERT INTO achievements (id, name, category, state, level, reward_amount, reward_type, points, progress_needed) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)")) {
                    for (int i = 0; i < levels.size(); i++) {
                        com.google.gson.JsonObject level = levels.get(i).getAsJsonObject();
                        int rowId = i == 0 ? savedId : ++nextId;
                        statement.setInt(1, rowId);
                        statement.setString(2, name.trim());
                        statement.setString(3, category.trim());
                        if (state < 0) statement.setNull(4, java.sql.Types.SMALLINT);
                        else statement.setShort(4, state);
                        statement.setInt(5, level.get("level").getAsInt());
                        statement.setInt(6, level.get("rewardAmount").getAsInt());
                        statement.setInt(7, level.get("rewardType").getAsInt());
                        statement.setInt(8, level.get("points").getAsInt());
                        statement.setInt(9, level.get("progress").getAsInt());
                        statement.addBatch();
                    }
                    statement.executeBatch();
                }

                if (oldName != null && !oldName.equals(name.trim())) {
                    try (PreparedStatement statement = connection.prepareStatement(
                            "UPDATE users_achievements SET achievement_name = ? WHERE achievement_name = ?")) {
                        statement.setString(1, name.trim());
                        statement.setString(2, oldName);
                        statement.executeUpdate();
                    }
                }

                connection.commit();
            } catch (Exception exception) {
                connection.rollback();
                throw exception;
            }
        } catch (Exception exception) {
            fail("Salvataggio traguardo fallito", achievementId);
            return;
        }

        Emulator.getGameEnvironment().getAchievementManager().reload();
        this.client.sendResponse(new AchievementEditorResultComposer(true, "Traguardo salvato", savedId));
        AchievementEditorSupport.sendState(this.client);
    }

    private static boolean validName(String value) {
        return value != null && SAFE_NAME.matcher(value.trim()).matches();
    }

    private static boolean validCategory(String value) {
        return value != null && SAFE_CATEGORY.matcher(value.trim()).matches();
    }

    private void fail(String message, int achievementId) {
        this.client.sendResponse(new AchievementEditorResultComposer(false, message, achievementId));
    }
}
