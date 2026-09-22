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

public class WiredExtraJumpStrength extends InteractionWiredExtra {
    public static final int CODE = 2022;

    private int option = 0;
    private int numberValue = 80;
    private int target = 0;
    private String variableId = "";

    public WiredExtraJumpStrength(ResultSet set, Item baseItem) throws SQLException {
        super(set, baseItem);
    }

    public WiredExtraJumpStrength(int id, int userId, Item item, String extradata, int limitedStack, int limitedSells) {
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
        this.option = (params.length > 0) ? params[0] : 0;
        this.numberValue = (params.length > 1) ? params[1] : 80;
        this.target = (params.length > 2) ? params[2] : 0;

        int[] varIds = settings.getVariableIds();
        this.variableId = (varIds != null && varIds.length > 0) ? String.valueOf(varIds[0]) : "";

        return true;
    }

    @Override
    public String getWiredData() {
        return WiredManager.getGson().toJson(new JsonData(this.option, this.numberValue, this.target, this.variableId));
    }

    @Override
    public void serializeWiredData(ServerMessage message, Room room) {
        message.appendBoolean(false);
        message.appendInt(0);
        message.appendInt(0);
        message.appendInt(this.getBaseItem().getSpriteId());
        message.appendInt(this.getId());
        message.appendString("");
        message.appendInt(3);
        message.appendInt(this.option);
        message.appendInt(this.numberValue);
        message.appendInt(this.target);
        message.appendInt(0);
        message.appendInt(CODE);
        message.appendInt(0);
        message.appendInt(0);
        message.appendInt(1);
        message.appendString(this.variableId != null ? this.variableId : "");
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
                this.option = data.option;
                this.numberValue = data.numberValue;
                this.target = data.target;
                this.variableId = data.variableId != null ? data.variableId : "";
            }
        }
    }

    @Override
    public void onPickUp() {
        this.option = 0;
        this.numberValue = 80;
        this.target = 0;
        this.variableId = "";
    }

    public int getOption() {
        return this.option;
    }

    public int getNumberValue() {
        return this.numberValue;
    }

    public int getTarget() {
        return this.target;
    }

    public String getVariableId() {
        return this.variableId;
    }

    private static class JsonData {
        private final int option;
        private final int numberValue;
        private final int target;
        private final String variableId;

        public JsonData(int option, int numberValue, int target, String variableId) {
            this.option = option;
            this.numberValue = numberValue;
            this.target = target;
            this.variableId = variableId;
        }
    }
}
