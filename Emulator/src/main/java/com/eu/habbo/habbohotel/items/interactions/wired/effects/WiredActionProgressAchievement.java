package com.eu.habbo.habbohotel.items.interactions.wired.effects;

import com.eu.habbo.Emulator;
import com.eu.habbo.habbohotel.achievements.Achievement;
import com.eu.habbo.habbohotel.achievements.AchievementManager;
import com.eu.habbo.habbohotel.gameclients.GameClient;
import com.eu.habbo.habbohotel.items.Item;
import com.eu.habbo.habbohotel.items.interactions.InteractionWired;
import com.eu.habbo.habbohotel.items.interactions.InteractionWiredEffect;
import com.eu.habbo.habbohotel.items.interactions.wired.WiredSettings;
import com.eu.habbo.habbohotel.rooms.Room;
import com.eu.habbo.habbohotel.rooms.RoomUnit;
import com.eu.habbo.habbohotel.users.Habbo;
import com.eu.habbo.habbohotel.wired.WiredEffectType;
import com.eu.habbo.habbohotel.wired.core.WiredContext;
import com.eu.habbo.habbohotel.wired.core.WiredContextVariableSupport;
import com.eu.habbo.habbohotel.wired.core.WiredManager;
import com.eu.habbo.messages.ServerMessage;
import java.sql.ResultSet;
import java.sql.SQLException;

/** Implements the SWF wf_act_progress_achievement action (action code 51). */
public class WiredActionProgressAchievement extends InteractionWiredEffect {
    public static final WiredEffectType type = WiredEffectType.PROGRESS_ACHIEVEMENT;

    private String achievementName = "";
    private int progressionMode = 0;
    private int option = 0;
    private int score = 0;
    private int target = 0;
    private String variableId = "";

    public WiredActionProgressAchievement(ResultSet set, Item baseItem) throws SQLException {
        super(set, baseItem);
    }

    public WiredActionProgressAchievement(
            int id, int userId, Item item, String extradata, int limitedStack, int limitedSells) {
        super(id, userId, item, extradata, limitedStack, limitedSells);
    }

    @Override
    public void execute(WiredContext ctx) {
        if (ctx == null || ctx.room() == null || this.achievementName.isEmpty()) return;

        ctx.actor().ifPresent(actor -> {
            Habbo habbo = ctx.room().getHabbo(actor);
            if (habbo != null) apply(habbo, resolveScore(ctx));
        });
    }

    @Override
    public boolean execute(RoomUnit roomUnit, Room room, Object[] stuff) {
        if (roomUnit == null || room == null || this.achievementName.isEmpty()) return false;

        Habbo habbo = room.getHabbo(roomUnit);
        if (habbo == null) return false;

        apply(habbo, Math.max(0, this.score));
        return true;
    }

    private void apply(Habbo habbo, int requestedValue) {
        if (habbo == null || requestedValue <= 0) return;

        Achievement achievement =
                Emulator.getGameEnvironment().getAchievementManager().getAchievement(this.achievementName);
        if (achievement == null) return;

        int amount = requestedValue;

        // Mode 0 adds the selected amount. Mode 1 treats it as the desired
        // cumulative progress, as exposed by the SWF progression radio group.
        if (this.progressionMode == 1) {
            int current = habbo.getHabboStats().getAchievementProgress(achievement);
            if (current < 0) current = 0;
            amount = requestedValue - current;
        }

        if (amount > 0) AchievementManager.progressAchievement(habbo, achievement, amount);
    }

    private int resolveScore(WiredContext ctx) {
        if (this.option != 1) return Math.max(0, this.score);

        int variableItemId;
        try {
            variableItemId = Integer.parseInt(this.variableId);
        } catch (NumberFormatException exception) {
            return 0;
        }

        Integer value;
        if (this.target == -20) {
            value = WiredContextVariableSupport.getCurrentValue(ctx, variableItemId);
        } else if (this.target == -10 && ctx.room() != null) {
            value = ctx.room().getRoomVariableManager().getCurrentValue(variableItemId);
        } else {
            return 0;
        }

        return value == null ? 0 : Math.max(0, value);
    }

    @Override
    public String getWiredData() {
        return WiredManager.getGson()
                .toJson(new JsonData(
                        this.achievementName,
                        this.progressionMode,
                        this.option,
                        this.score,
                        this.target,
                        this.variableId));
    }

    @Override
    public void loadWiredData(ResultSet set, Room room) throws SQLException {
        this.onPickUp();
        String wiredData = InteractionWired.extractWiredDataPayload(set);
        if (wiredData == null || wiredData.isEmpty() || !wiredData.startsWith("{")) return;

        JsonData data = WiredManager.getGson().fromJson(wiredData, JsonData.class);
        if (data == null) return;

        this.achievementName = data.achievementName != null ? data.achievementName : "";
        this.progressionMode = data.progressionMode == 1 ? 1 : 0;
        this.option = data.option == 1 ? 1 : 0;
        this.score = Math.max(0, data.score);
        this.target = data.target;
        this.variableId = data.variableId != null ? data.variableId : "";
    }

    @Override
    public void onPickUp() {
        this.achievementName = "";
        this.progressionMode = 0;
        this.option = 0;
        this.score = 0;
        this.target = 0;
        this.variableId = "";
        this.setDelay(0);
    }

    @Override
    public WiredEffectType getType() {
        return type;
    }

    @Override
    public void serializeWiredData(ServerMessage message, Room room) {
        message.appendBoolean(false);
        message.appendInt(0);
        message.appendInt(0);
        message.appendInt(this.getBaseItem().getSpriteId());
        message.appendInt(this.getId());
        message.appendString(this.achievementName);
        message.appendInt(4);
        message.appendInt(this.progressionMode);
        message.appendInt(this.option);
        message.appendInt(this.score);
        message.appendInt(this.target);
        message.appendInt(0);
        message.appendInt(type.code);
        message.appendInt(this.getDelay());
        message.appendInt(0);
        message.appendInt(WIRED_EXTENDED_SETTINGS_MARKER);
        message.appendInt(0);
        message.appendInt(0);
        if (this.variableId != null && !this.variableId.isEmpty()) {
            try {
                message.appendInt(1);
                message.appendInt(Integer.parseInt(this.variableId));
            } catch (NumberFormatException exception) {
                message.appendInt(0);
            }
        } else {
            message.appendInt(0);
        }
        message.appendInt(0);
    }

    @Override
    public boolean saveData(WiredSettings settings, GameClient gameClient) {
        String nextAchievement = settings.getStringParam();
        this.achievementName = nextAchievement == null ? "" : nextAchievement.trim();
        if (this.achievementName.isEmpty()) return false;

        int[] params = settings.getIntParams();
        this.progressionMode = params.length > 0 && params[0] == 1 ? 1 : 0;
        this.option = params.length > 1 && params[1] == 1 ? 1 : 0;
        this.score = params.length > 2 ? Math.max(0, params[2]) : 0;
        this.target = params.length > 3 ? params[3] : 0;

        int[] variableIds = settings.getVariableIds();
        this.variableId = variableIds != null && variableIds.length > 0 ? String.valueOf(variableIds[0]) : "";
        this.setDelay(settings.getDelay());
        return true;
    }

    private static class JsonData {
        private final String achievementName;
        private final int progressionMode;
        private final int option;
        private final int score;
        private final int target;
        private final String variableId;

        public JsonData(
                String achievementName, int progressionMode, int option, int score, int target, String variableId) {
            this.achievementName = achievementName;
            this.progressionMode = progressionMode;
            this.option = option;
            this.score = score;
            this.target = target;
            this.variableId = variableId;
        }
    }
}
