package com.eu.habbo.habbohotel.items.interactions.wired.effects;

import com.eu.habbo.Emulator;
import com.eu.habbo.habbohotel.gameclients.GameClient;
import com.eu.habbo.habbohotel.items.Item;
import com.eu.habbo.habbohotel.items.interactions.InteractionWired;
import com.eu.habbo.habbohotel.items.interactions.InteractionWiredEffect;
import com.eu.habbo.habbohotel.items.interactions.wired.WiredSettings;
import com.eu.habbo.habbohotel.rooms.Room;
import com.eu.habbo.habbohotel.rooms.RoomUnit;
import com.eu.habbo.habbohotel.wired.WiredEffectType;
import com.eu.habbo.habbohotel.wired.core.WiredContext;
import com.eu.habbo.habbohotel.wired.core.WiredManager;
import com.eu.habbo.messages.ServerMessage;
import java.sql.ResultSet;
import java.sql.SQLException;

public class WiredActionResetRewardTrack extends InteractionWiredEffect {
    public static final WiredEffectType type = WiredEffectType.RESET_REWARD_TRACK;

    private String trackId = "";

    public WiredActionResetRewardTrack(ResultSet set, Item baseItem) throws SQLException {
        super(set, baseItem);
    }

    public WiredActionResetRewardTrack(
            int id, int userId, Item item, String extradata, int limitedStack, int limitedSells) {
        super(id, userId, item, extradata, limitedStack, limitedSells);
    }

    @Override
    public void execute(WiredContext ctx) {
        if (ctx.room() == null || this.trackId.isEmpty()) return;

        ctx.actor().ifPresent(actor -> {
            com.eu.habbo.habbohotel.users.Habbo habbo = ctx.room().getHabbo(actor);
            if (habbo != null) {
                Emulator.getGameEnvironment()
                        .getRewardTrackManager()
                        .resetWiredProgress(habbo.getHabboInfo().getId(), this.trackId);
            }
        });
    }

    @Override
    public boolean execute(RoomUnit roomUnit, Room room, Object[] stuff) {
        if (roomUnit == null || room == null || this.trackId.isEmpty()) return false;

        com.eu.habbo.habbohotel.users.Habbo habbo = room.getHabbo(roomUnit);
        if (habbo == null) return false;

        Emulator.getGameEnvironment()
                .getRewardTrackManager()
                .resetWiredProgress(habbo.getHabboInfo().getId(), this.trackId);
        return true;
    }

    @Override
    public String getWiredData() {
        return WiredManager.getGson().toJson(new JsonData(this.trackId));
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
            }
        } else {
            this.trackId = wiredData;
        }
    }

    @Override
    public void onPickUp() {
        this.trackId = "";
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
        message.appendString(this.trackId != null ? this.trackId : "");
        message.appendInt(0);
        message.appendInt(0);
        message.appendInt(type.code);
        message.appendInt(this.getDelay());
        message.appendInt(0);
    }

    @Override
    public boolean saveData(WiredSettings settings, GameClient gameClient) {
        this.trackId = settings.getStringParam() != null ? settings.getStringParam() : "";
        this.setDelay(settings.getDelay());
        return true;
    }

    private static class JsonData {
        private final String trackId;

        public JsonData(String trackId) {
            this.trackId = trackId;
        }
    }
}
