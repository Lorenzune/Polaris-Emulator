package com.eu.habbo.habbohotel.items.interactions.wired.extra;

import com.eu.habbo.habbohotel.gameclients.GameClient;
import com.eu.habbo.habbohotel.items.Item;
import com.eu.habbo.habbohotel.items.interactions.InteractionWired;
import com.eu.habbo.habbohotel.items.interactions.InteractionWiredExtra;
import com.eu.habbo.habbohotel.items.interactions.wired.WiredSettings;
import com.eu.habbo.habbohotel.rooms.Room;
import com.eu.habbo.habbohotel.rooms.RoomUnit;
import com.eu.habbo.habbohotel.wired.core.WiredManager;
import com.eu.habbo.messages.ServerMessage;

import java.sql.ResultSet;
import java.sql.SQLException;

public class WiredExtraAchievementEnabler extends InteractionWiredExtra {
    public static final int CODE = 2001;

    private String achievementIds = "";

    public WiredExtraAchievementEnabler(ResultSet set, Item baseItem) throws SQLException {
        super(set, baseItem);
    }

    public WiredExtraAchievementEnabler(int id, int userId, Item item, String extradata, int limitedStack, int limitedSells) {
        super(id, userId, item, extradata, limitedStack, limitedSells);
    }

    @Override
    public boolean execute(RoomUnit roomUnit, Room room, Object[] stuff) {
        return true;
    }

    @Override
    public boolean hasConfiguration() {
        return true;
    }

    @Override
    public boolean saveData(WiredSettings settings, GameClient gameClient) {
        this.achievementIds = settings.getStringParam() != null ? settings.getStringParam() : "";
        return true;
    }

    @Override
    public String getWiredData() {
        return WiredManager.getGson().toJson(new JsonData(this.achievementIds));
    }

    @Override
    public void serializeWiredData(ServerMessage message, Room room) {
        message.appendBoolean(false);
        message.appendInt(0);
        message.appendInt(0);
        message.appendInt(this.getBaseItem().getSpriteId());
        message.appendInt(this.getId());
        message.appendString(this.achievementIds != null ? this.achievementIds : "");
        message.appendInt(0);
        message.appendInt(0);
        message.appendInt(CODE);
        message.appendInt(0);
        message.appendInt(0);
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
                this.achievementIds = data.achievementIds != null ? data.achievementIds : "";
            }
        } else {
            this.achievementIds = wiredData;
        }
    }

    @Override
    public void onPickUp() {
        this.achievementIds = "";
    }

    public String getAchievementIds() {
        return this.achievementIds;
    }

    private static class JsonData {
        private final String achievementIds;

        public JsonData(String achievementIds) {
            this.achievementIds = achievementIds;
        }
    }
}
