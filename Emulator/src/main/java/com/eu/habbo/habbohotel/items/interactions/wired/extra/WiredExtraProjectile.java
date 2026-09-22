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

public class WiredExtraProjectile extends InteractionWiredExtra {
    public static final int CODE = 2021;

    private int[] intParams = new int[19];
    private String[] variableIds = new String[] {"", ""};

    public WiredExtraProjectile(ResultSet set, Item baseItem) throws SQLException {
        super(set, baseItem);
    }

    public WiredExtraProjectile(int id, int userId, Item item, String extradata, int limitedStack, int limitedSells) {
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
        this.intParams = new int[19];
        for (int i = 0; i < Math.min(params.length, 19); i++) {
            this.intParams[i] = params[i];
        }

        int[] varIds = settings.getVariableIds();
        this.variableIds = new String[] {"", ""};
        if (varIds != null && varIds.length > 0) {
            for (int i = 0; i < Math.min(varIds.length, 2); i++) {
                this.variableIds[i] = String.valueOf(varIds[i]);
            }
        }

        return true;
    }

    @Override
    public String getWiredData() {
        return WiredManager.getGson().toJson(new JsonData(this.intParams, this.variableIds));
    }

    @Override
    public void serializeWiredData(ServerMessage message, Room room) {
        message.appendBoolean(false);
        message.appendInt(5); // furni limit
        message.appendInt(0); // furni ids count
        message.appendInt(this.getBaseItem().getSpriteId());
        message.appendInt(this.getId());
        message.appendString("");
        message.appendInt(this.intParams.length);
        for (int param : this.intParams) {
            message.appendInt(param);
        }
        message.appendInt(0);
        message.appendInt(CODE);
        message.appendInt(0);
        message.appendInt(0);
        // Extended payload: variable ids
        message.appendInt(this.variableIds.length);
        for (String varId : this.variableIds) {
            message.appendString(varId != null ? varId : "");
        }
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
                if (data.intParams != null) {
                    this.intParams = new int[19];
                    System.arraycopy(data.intParams, 0, this.intParams, 0, Math.min(data.intParams.length, 19));
                }
                if (data.variableIds != null) {
                    this.variableIds = data.variableIds;
                }
            }
        }
    }

    @Override
    public void onPickUp() {
        this.intParams = new int[19];
        this.variableIds = new String[] {"", ""};
    }

    public int[] getIntParams() {
        return this.intParams;
    }

    public String[] getVariableIds() {
        return this.variableIds;
    }

    private static class JsonData {
        private final int[] intParams;
        private final String[] variableIds;

        public JsonData(int[] intParams, String[] variableIds) {
            this.intParams = intParams;
            this.variableIds = variableIds;
        }
    }
}
