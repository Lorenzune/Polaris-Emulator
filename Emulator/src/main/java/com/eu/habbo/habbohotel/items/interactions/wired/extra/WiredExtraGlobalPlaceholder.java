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

public class WiredExtraGlobalPlaceholder extends InteractionWiredExtra {
    public static final int CODE = 2000;

    private int sourceType = 0; // 0 = from value, 1 = from another room
    private int targetRoomId = 0;
    private String placeholderName = "";
    private String value = "";

    public WiredExtraGlobalPlaceholder(ResultSet set, Item baseItem) throws SQLException {
        super(set, baseItem);
    }

    public WiredExtraGlobalPlaceholder(int id, int userId, Item item, String extradata, int limitedStack, int limitedSells) {
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
        int[] params = settings.getIntParams();
        this.sourceType = params.length > 0 ? params[0] : 0;
        this.targetRoomId = params.length > 2 ? params[2] : 0;

        String stringParam = settings.getStringParam();
        if (stringParam != null) {
            String[] parts = stringParam.split("\t", -1);
            this.placeholderName = parts.length > 0 ? parts[0] : "";
            this.value = parts.length > 1 ? parts[1] : "";
        }

        return true;
    }

    @Override
    public String getWiredData() {
        return WiredManager.getGson().toJson(new JsonData(this.sourceType, this.targetRoomId, this.placeholderName, this.value));
    }

    @Override
    public void serializeWiredData(ServerMessage message, Room room) {
        message.appendBoolean(false);
        message.appendInt(0);
        message.appendInt(0);
        message.appendInt(this.getBaseItem().getSpriteId());
        message.appendInt(this.getId());
        message.appendString(this.placeholderName + "\t" + this.value);
        message.appendInt(3);
        message.appendInt(this.sourceType);
        message.appendInt(0);
        message.appendInt(this.targetRoomId);
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
                this.sourceType = data.sourceType;
                this.targetRoomId = data.targetRoomId;
                this.placeholderName = data.placeholderName != null ? data.placeholderName : "";
                this.value = data.value != null ? data.value : "";
            }
        }
    }

    @Override
    public void onPickUp() {
        this.sourceType = 0;
        this.targetRoomId = 0;
        this.placeholderName = "";
        this.value = "";
    }

    public String getPlaceholderName() {
        return this.placeholderName;
    }

    public String getValue() {
        return this.value;
    }

    public int getSourceType() {
        return this.sourceType;
    }

    public int getTargetRoomId() {
        return this.targetRoomId;
    }

    private static class JsonData {
        private final int sourceType;
        private final int targetRoomId;
        private final String placeholderName;
        private final String value;

        public JsonData(int sourceType, int targetRoomId, String placeholderName, String value) {
            this.sourceType = sourceType;
            this.targetRoomId = targetRoomId;
            this.placeholderName = placeholderName;
            this.value = value;
        }
    }
}
