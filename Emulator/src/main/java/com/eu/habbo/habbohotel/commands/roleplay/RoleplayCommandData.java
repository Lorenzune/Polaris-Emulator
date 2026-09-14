package com.eu.habbo.habbohotel.commands.roleplay;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Random;

public class RoleplayCommandData {

    private static final Random RANDOM = new Random();

    private final int id;
    private final String name;
    private final String category;
    private final int sortOrder;
    private final String[] keys;
    private final int minRank;
    private final String permission;
    private final boolean enabled;
    private final boolean requiresTarget;
    private final boolean allowSelf;
    private final int maxDistance;
    private final boolean faceToFace;
    private final int knockbackTiles;
    private final int senderEffect;
    private final int senderEffectDuration;
    private final int targetEffect;
    private final int targetEffectDuration;
    private final int senderHanditem;
    private final int targetHanditem;
    private final String senderPosture;
    private final String targetPosture;
    private final int bubbleId;
    private final List<String> senderMessages;
    private final List<String> targetMessages;
    private final String errorForgotTarget;
    private final String errorNotFound;
    private final String errorSelf;
    private final String errorCantReach;
    private final String errorDisabled;
    private final int cooldownSeconds;
    private final String senderAchievement;
    private final int senderAchievementProgress;
    private final String targetAchievement;
    private final int targetAchievementProgress;

    public RoleplayCommandData(ResultSet set) throws SQLException {
        this.id = set.getInt("id");
        this.name = set.getString("name");
        this.category = set.getString("category") != null ? set.getString("category") : "";
        this.sortOrder = set.getInt("sort_order");
        this.keys = set.getString("keys") != null
                ? Arrays.stream(set.getString("keys").split(";"))
                .map(String::trim)
                .filter(key -> !key.isEmpty())
                .toArray(String[]::new)
                : new String[0];
        this.minRank = set.getInt("min_rank");
        this.permission = set.getString("permission");
        this.enabled = set.getInt("enabled") == 1;
        this.requiresTarget = set.getInt("requires_target") == 1;
        this.allowSelf = set.getInt("allow_self") == 1;
        this.maxDistance = set.getInt("max_distance");
        this.faceToFace = set.getInt("face_to_face") == 1;
        this.knockbackTiles = set.getInt("knockback_tiles");
        this.senderEffect = set.getInt("sender_effect");
        this.senderEffectDuration = set.getInt("sender_effect_duration");
        this.targetEffect = set.getInt("target_effect");
        this.targetEffectDuration = set.getInt("target_effect_duration");
        this.senderHanditem = set.getInt("sender_handitem");
        this.targetHanditem = set.getInt("target_handitem");
        this.senderPosture = set.getString("sender_posture") != null ? set.getString("sender_posture") : "none";
        this.targetPosture = set.getString("target_posture") != null ? set.getString("target_posture") : "none";
        this.bubbleId = set.getInt("bubble_id");

        this.senderMessages = new ArrayList<>();
        String rawSender = set.getString("sender_messages");
        if (rawSender != null && !rawSender.isEmpty()) {
            for (String m : rawSender.split("\\|")) {
                if (!m.trim().isEmpty()) {
                    this.senderMessages.add(m.trim());
                }
            }
        }

        this.targetMessages = new ArrayList<>();
        String rawTarget = set.getString("target_messages");
        if (rawTarget != null && !rawTarget.isEmpty()) {
            for (String m : rawTarget.split("\\|")) {
                if (!m.trim().isEmpty()) {
                    this.targetMessages.add(m.trim());
                }
            }
        }

        this.errorForgotTarget = set.getString("error_forgot_target") != null ? set.getString("error_forgot_target") : "Specifica il nome dell'utente!";
        this.errorNotFound = set.getString("error_not_found") != null ? set.getString("error_not_found") : "Utente %target% non trovato in questa stanza!";
        this.errorSelf = set.getString("error_self") != null ? set.getString("error_self") : "Non puoi fare questa azione su te stesso!";
        this.errorCantReach = set.getString("error_cant_reach") != null ? set.getString("error_cant_reach") : "Sei troppo lontano da %target%!";
        this.errorDisabled = set.getString("error_disabled") != null ? set.getString("error_disabled") : "%target% ha disattivato le interazioni roleplay!";
        this.cooldownSeconds = set.getInt("cooldown_seconds");
        this.senderAchievement = set.getString("sender_achievement");
        this.senderAchievementProgress = set.getInt("sender_achievement_progress");
        this.targetAchievement = set.getString("target_achievement");
        this.targetAchievementProgress = set.getInt("target_achievement_progress");
    }

    public int getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public String[] getKeys() {
        return keys;
    }

    public int getMinRank() {
        return minRank;
    }

    public String getPermission() {
        return permission;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public boolean isRequiresTarget() {
        return requiresTarget;
    }

    public boolean isAllowSelf() {
        return allowSelf;
    }

    public int getMaxDistance() {
        return maxDistance;
    }

    public boolean isFaceToFace() {
        return faceToFace;
    }

    public int getKnockbackTiles() {
        return knockbackTiles;
    }

    public int getSenderEffect() {
        return senderEffect;
    }

    public int getSenderEffectDuration() {
        return senderEffectDuration;
    }

    public int getTargetEffect() {
        return targetEffect;
    }

    public int getTargetEffectDuration() {
        return targetEffectDuration;
    }

    public int getSenderHanditem() {
        return senderHanditem;
    }

    public int getTargetHanditem() {
        return targetHanditem;
    }

    public String getSenderPosture() {
        return senderPosture;
    }

    public String getTargetPosture() {
        return targetPosture;
    }

    public int getBubbleId() {
        return bubbleId;
    }

    public List<String> getSenderMessages() {
        return senderMessages;
    }

    public List<String> getTargetMessages() {
        return targetMessages;
    }

    public String getRandomSenderMessage() {
        if (senderMessages.isEmpty()) return null;
        return senderMessages.get(RANDOM.nextInt(senderMessages.size()));
    }

    public String getRandomTargetMessage() {
        if (targetMessages.isEmpty()) return null;
        return targetMessages.get(RANDOM.nextInt(targetMessages.size()));
    }

    public String getErrorForgotTarget() {
        return errorForgotTarget;
    }

    public String getErrorNotFound() {
        return errorNotFound;
    }

    public String getErrorSelf() {
        return errorSelf;
    }

    public String getErrorCantReach() {
        return errorCantReach;
    }

    public String getErrorDisabled() {
        return errorDisabled;
    }

    public int getCooldownSeconds() {
        return cooldownSeconds;
    }

    public String getSenderAchievement() {
        return senderAchievement;
    }

    public int getSenderAchievementProgress() {
        return senderAchievementProgress > 0 ? senderAchievementProgress : 1;
    }

    public String getTargetAchievement() {
        return targetAchievement;
    }

    public int getTargetAchievementProgress() {
        return targetAchievementProgress > 0 ? targetAchievementProgress : 1;
    }

    public String getCategory() {
        return category;
    }

    public int getSortOrder() {
        return sortOrder;
    }
}
