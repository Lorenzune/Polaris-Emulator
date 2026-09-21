package com.eu.habbo.habbohotel.items.interactions.wired.effects;

import com.eu.habbo.Emulator;
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

public class WiredActionProgressRewardTrack extends InteractionWiredEffect {
    public static final WiredEffectType type = WiredEffectType.PROGRESS_REWARD_TRACK;

    private String trackId = "";
    private String taskId = "";
    private boolean addToExistingScore = false;
    private int option = 0;
    private int score = 1;
    private int target = 0;
    private String variableId = "";

    public WiredActionProgressRewardTrack(ResultSet set, Item baseItem) throws SQLException {
        super(set, baseItem);
    }

    public WiredActionProgressRewardTrack(int id, int userId, Item item, String extradata, int limitedStack, int limitedSells) {
        super(id, userId, item, extradata, limitedStack, limitedSells);
    }

    @Override
    public void execute(WiredContext ctx) {
        if (ctx.room() == null || this.trackId.isEmpty() || this.taskId.isEmpty()) {
            return;
        }

        ctx.actor().ifPresent(actor -> {
            Habbo habbo = ctx.room().getHabbo(actor);
            if (habbo != null) {
                Emulator.getGameEnvironment().getRewardTrackManager().applyWiredProgress(
                        habbo.getHabboInfo().getId(),
                        this.trackId,
                        this.taskId,
                        this.resolveScore(ctx),
                        this.addToExistingScore);
            }
        });
    }

    @Override
    public boolean execute(RoomUnit roomUnit, Room room, Object[] stuff) {
        if (roomUnit == null || room == null || this.trackId.isEmpty() || this.taskId.isEmpty()) return false;

        Habbo habbo = room.getHabbo(roomUnit);
        if (habbo == null) return false;

        Emulator.getGameEnvironment().getRewardTrackManager().applyWiredProgress(
                habbo.getHabboInfo().getId(),
                this.trackId,
                this.taskId,
                Math.max(1, this.score),
                this.addToExistingScore);
        return true;
    }

    private int resolveScore(WiredContext ctx) {
        if (this.option != 1) return Math.max(1, this.score);

        int variableItemId;
        try {
            variableItemId = Integer.parseInt(this.variableId);
        } catch (NumberFormatException e) {
            return 1;
        }

        Integer value;
        if (this.target == -20) {
            value = WiredContextVariableSupport.getCurrentValue(ctx, variableItemId);
        } else if (this.target == -10) {
            value = ctx.room().getRoomVariableManager().getCurrentValue(variableItemId);
        } else {
            return 1;
        }

        return value == null ? 1 : Math.max(1, value);
    }

    @Override
    public String getWiredData() {
        return WiredManager.getGson().toJson(new JsonData(
                this.trackId,
                this.taskId,
                this.addToExistingScore,
                this.option,
                this.score,
                this.target,
                this.variableId
        ));
    }

    @Override
    public void loadWiredData(ResultSet set, Room room) throws SQLException {
        this.onPickUp();
        String wiredData = InteractionWired.extractWiredDataPayload(set);
        if (wiredData == null || wiredData.isEmpty()) {
            return;
        }

        if (wiredData.startsWith("{")) {
            JsonData data = WiredManager.getGson().fromJson(wiredData, JsonData.class);
            if (data != null) {
                this.trackId = data.trackId != null ? data.trackId : "";
                this.taskId = data.taskId != null ? data.taskId : "";
                this.addToExistingScore = data.addToExistingScore;
                this.option = data.option;
                this.score = data.score;
                this.target = data.target;
                this.variableId = data.variableId != null ? data.variableId : "";
            }
        }
    }

    @Override
    public void onPickUp() {
        this.trackId = "";
        this.taskId = "";
        this.addToExistingScore = false;
        this.option = 0;
        this.score = 1;
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
        message.appendString(this.trackId + "\t" + this.taskId);
        message.appendInt(4);
        message.appendInt(this.addToExistingScore ? 1 : 0);
        message.appendInt(this.option);
        message.appendInt(this.score);
        message.appendInt(this.target);
        message.appendInt(0);
        message.appendInt(type.code);
        message.appendInt(this.getDelay());
        message.appendInt(0);
        message.appendInt(1);
        message.appendString(this.variableId != null ? this.variableId : "");
    }

    @Override
    public boolean saveData(WiredSettings settings, GameClient gameClient) {
        String stringParam = settings.getStringParam();
        if (stringParam != null) {
            String[] parts = stringParam.split("\t", -1);
            this.trackId = parts.length > 0 ? parts[0] : "";
            this.taskId = parts.length > 1 ? parts[1] : "";
        }

        int[] params = settings.getIntParams();
        this.addToExistingScore = params.length > 0 && params[0] == 1;
        this.option = params.length > 1 ? params[1] : 0;
        this.score = params.length > 2 ? Math.max(1, params[2]) : 1;
        this.target = params.length > 3 ? params[3] : 0;

        int[] varIds = settings.getVariableIds();
        this.variableId = (varIds != null && varIds.length > 0) ? String.valueOf(varIds[0]) : "";

        this.setDelay(settings.getDelay());
        return true;
    }

    private static class JsonData {
        private final String trackId;
        private final String taskId;
        private final boolean addToExistingScore;
        private final int option;
        private final int score;
        private final int target;
        private final String variableId;

        public JsonData(String trackId, String taskId, boolean addToExistingScore, int option, int score, int target, String variableId) {
            this.trackId = trackId;
            this.taskId = taskId;
            this.addToExistingScore = addToExistingScore;
            this.option = option;
            this.score = score;
            this.target = target;
            this.variableId = variableId;
        }
    }
}
