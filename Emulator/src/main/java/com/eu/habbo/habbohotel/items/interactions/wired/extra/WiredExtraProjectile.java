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
    private int[] furniIds = new int[0];

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
        String[] tokens = null;
        String text = settings.getStringParam();
        if (text != null && !text.isBlank()) {
            try {
                tokens = WiredManager.getGson().fromJson(text, String[].class);
            } catch (RuntimeException ignored) {
                return false;
            }
        }
        int[] params = settings.getIntParams();
        if (params == null || params.length < 19) return false;
        this.intParams = new int[23];
        for (int i = 0; i < Math.min(params.length, 23); i++) {
            this.intParams[i] = params[i];
        }

        normalizeParams();
        this.furniIds = settings.getFurniIds() == null
                ? new int[0]
                : java.util.Arrays.stream(settings.getFurniIds())
                        .filter(id -> id > 0)
                        .distinct()
                        .limit(5)
                        .toArray();
        int[] varIds = settings.getVariableIds();
        this.variableIds = new String[] {"", ""};
        if (varIds != null && varIds.length > 0) {
            for (int i = 0; i < Math.min(varIds.length, 2); i++) {
                this.variableIds[i] = String.valueOf(varIds[i]);
            }
        }

        if (tokens != null)
            for (int i = 0; i < Math.min(2, tokens.length); i++)
                this.variableIds[i] =
                        tokens[i] == null ? "" : tokens[i].substring(0, Math.min(200, tokens[i].length()));
        com.eu.habbo.habbohotel.wired.core.WiredProjectileSupport.cancelExtra(this);
        if (gameClient != null
                && gameClient.getHabbo() != null
                && gameClient.getHabbo().getHabboInfo().getCurrentRoom() != null)
            gameClient
                    .getHabbo()
                    .getHabboInfo()
                    .getCurrentRoom()
                    .getFurniVariableManager()
                    .requestBroadcast();
        return true;
    }

    private void normalizeParams() {
        for (int i : new int[] {0, 2, 6, 7, 8, 12, 13}) this.intParams[i] = this.intParams[i] == 0 ? 0 : 1;
        this.intParams[1] = Math.max(0, Math.min(3, this.intParams[1]));
        this.intParams[3] = this.intParams[3] == 1 ? 1 : 0;
        this.intParams[15] = this.intParams[15] == 1 ? 1 : 0;
        this.intParams[4] = Math.max(1, Math.min(100000, this.intParams[4]));
        this.intParams[9] = Math.max(0, Math.min(100000, this.intParams[9]));
        this.intParams[10] = Math.floorMod(this.intParams[10], 8);
        this.intParams[11] &= 127;
        this.intParams[14] = Math.max(0, Math.min(2, this.intParams[14]));
        this.intParams[16] = Math.max(-64, Math.min(64, this.intParams[16]));
        this.intParams[18] = Math.max(-1000, Math.min(1000, this.intParams[18]));
        for (int i : new int[] {5, 17})
            if (this.intParams[i] != 0
                    && this.intParams[i] != 1
                    && this.intParams[i] != -10
                    && this.intParams[i] != -20) this.intParams[i] = 0;
    }

    @Override
    public String getWiredData() {
        return WiredManager.getGson().toJson(new JsonData(this.intParams, this.variableIds, this.furniIds));
    }

    @Override
    public void serializeWiredData(ServerMessage message, Room room) {
        message.appendBoolean(false);
        message.appendInt(5); // furni limit
        message.appendInt(this.furniIds.length);
        for (int id : this.furniIds) message.appendInt(id);
        message.appendInt(this.getBaseItem().getSpriteId());
        message.appendInt(this.getId());
        message.appendString(
                java.util.Arrays.stream(this.variableIds).allMatch(v -> v == null || v.isBlank())
                        ? ""
                        : WiredManager.getGson().toJson(this.variableIds));
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
                this.furniIds = data.furniIds == null ? new int[0] : data.furniIds;
                if (data.intParams != null) {
                    this.intParams = new int[23];
                    System.arraycopy(data.intParams, 0, this.intParams, 0, Math.min(data.intParams.length, 23));
                    normalizeParams();
                }
                if (data.variableIds != null) {
                    this.variableIds = new String[] {"", ""};
                    for (int i = 0; i < Math.min(2, data.variableIds.length); i++)
                        this.variableIds[i] = data.variableIds[i] == null ? "" : data.variableIds[i];
                }
            }
        }
    }

    @Override
    public void onPickUp() {
        this.intParams = new int[19];
        this.variableIds = new String[] {"", ""};
        this.furniIds = new int[0];
        com.eu.habbo.habbohotel.wired.core.WiredProjectileSupport.cancelExtra(this);
    }

    public int[] getIntParams() {
        return this.intParams;
    }

    public String[] getVariableIds() {
        return this.variableIds;
    }

    public int[] getSelectedFurniIds() {
        return this.furniIds.clone();
    }

    private static class JsonData {
        private final int[] intParams;
        private final String[] variableIds;
        private final int[] furniIds;

        public JsonData(int[] intParams, String[] variableIds, int[] furniIds) {
            this.intParams = intParams;
            this.variableIds = variableIds;
            this.furniIds = furniIds == null || furniIds.length == 0 ? null : furniIds;
        }
    }
}
