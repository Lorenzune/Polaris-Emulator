package com.eu.habbo.habbohotel.items.interactions.wired.effects;

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
import java.util.ArrayList;
import java.util.List;

public class WiredActionClickSettings extends InteractionWiredEffect {
    public static final WiredEffectType type = WiredEffectType.CLICK_SETTINGS;

    private int userClickSetting = 0;
    private int furniClickSetting = 0;
    private final List<Integer> furniIds = new ArrayList<>();

    public WiredActionClickSettings(ResultSet set, Item baseItem) throws SQLException {
        super(set, baseItem);
    }

    public WiredActionClickSettings(int id, int userId, Item item, String extradata, int limitedStack, int limitedSells) {
        super(id, userId, item, extradata, limitedStack, limitedSells);
    }

    @Override
    public void execute(WiredContext ctx) {
        // Click settings configuration
    }

    @Override
    public boolean execute(RoomUnit roomUnit, Room room, Object[] stuff) {
        return true;
    }

    @Override
    public String getWiredData() {
        return WiredManager.getGson().toJson(new JsonData(this.userClickSetting, this.furniClickSetting, this.furniIds));
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
                this.userClickSetting = data.userClickSetting;
                this.furniClickSetting = data.furniClickSetting;
                if (data.furniIds != null) {
                    this.furniIds.addAll(data.furniIds);
                }
            }
        }
    }

    @Override
    public void onPickUp() {
        this.userClickSetting = 0;
        this.furniClickSetting = 0;
        this.furniIds.clear();
        this.setDelay(0);
    }

    @Override
    public WiredEffectType getType() {
        return type;
    }

    @Override
    public void serializeWiredData(ServerMessage message, Room room) {
        message.appendBoolean(false);
        message.appendInt(5);
        message.appendInt(this.furniIds.size());
        for (int id : this.furniIds) {
            message.appendInt(id);
        }
        message.appendInt(this.getBaseItem().getSpriteId());
        message.appendInt(this.getId());
        message.appendString("");
        message.appendInt(2);
        message.appendInt(this.userClickSetting);
        message.appendInt(this.furniClickSetting);
        message.appendInt(0);
        message.appendInt(type.code);
        message.appendInt(this.getDelay());
        message.appendInt(0);
    }

    @Override
    public boolean saveData(WiredSettings settings, GameClient gameClient) {
        int[] params = settings.getIntParams();
        this.userClickSetting = params.length > 0 ? params[0] : 0;
        this.furniClickSetting = params.length > 1 ? params[1] : 0;

        this.furniIds.clear();
        int[] items = settings.getFurniIds();
        if (items != null) {
            for (int id : items) {
                this.furniIds.add(id);
            }
        }

        this.setDelay(settings.getDelay());
        return true;
    }

    public int getUserClickSetting() {
        return this.userClickSetting;
    }

    public int getFurniClickSetting() {
        return this.furniClickSetting;
    }

    private static class JsonData {
        private final int userClickSetting;
        private final int furniClickSetting;
        private final List<Integer> furniIds;

        public JsonData(int userClickSetting, int furniClickSetting, List<Integer> furniIds) {
            this.userClickSetting = userClickSetting;
            this.furniClickSetting = furniClickSetting;
            this.furniIds = furniIds;
        }
    }
}
