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

    private int userSource = 0;
    private int userClickSetting = 0;
    private int furniClickSetting = 0;
    private final List<Integer> furniIds = new ArrayList<>();

    public WiredActionClickSettings(ResultSet set, Item baseItem) throws SQLException {
        super(set, baseItem);
    }

    public WiredActionClickSettings(
            int id, int userId, Item item, String extradata, int limitedStack, int limitedSells) {
        super(id, userId, item, extradata, limitedStack, limitedSells);
    }

    @Override
    public void execute(WiredContext ctx) {
        if (ctx == null || ctx.room() == null) return;
        for (RoomUnit target : com.eu.habbo.habbohotel.wired.core.WiredSourceUtil.resolveUsers(ctx, this.userSource)) execute(target, ctx.room(), new Object[0]);
    }

    @Override
    public boolean execute(RoomUnit roomUnit, Room room, Object[] stuff) {
        if (room == null || roomUnit == null) return false;
        var habbo = room.getHabbo(roomUnit);
        if (habbo == null || habbo.getClient() == null) return false;
        habbo.getClient().sendResponse(new com.eu.habbo.messages.outgoing.wired.WiredClickSettingsComposer(
                room.getId(), this.userClickSetting, this.furniClickSetting, room.canModifyWired(habbo)));
        return true;
    }

    @Override
    public String getWiredData() {
        return WiredManager.getGson()
                .toJson(new JsonData(this.userClickSetting, this.furniClickSetting, this.furniIds, this.userSource));
    }

    @Override
    public void loadWiredData(ResultSet set, Room room) throws SQLException {
        this.onPickUp();
        String wiredData = InteractionWired.extractWiredDataPayload(set);
        if (wiredData == null || wiredData.isEmpty()) {
            return;
        }

        if (wiredData.startsWith("{")) {
            JsonData data;
            try { data = WiredManager.getGson().fromJson(wiredData, JsonData.class); }
            catch (com.google.gson.JsonParseException ignored) { return; }
            if (data != null) {
                this.userSource = data.userSource == null ? 0 : WiredUtilityPayloadGuard.userSource(data.userSource);
                this.userClickSetting = Math.max(0, Math.min(2, data.userClickSetting));
                this.furniClickSetting = Math.max(0, Math.min(1, data.furniClickSetting));
                if (data.furniIds != null) {
                    this.furniIds.addAll(data.furniIds);
                }
            }
        }
    }

    @Override
    public void onPickUp() {
        this.userSource = 0;
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
        message.appendInt(3);
        message.appendInt(this.userClickSetting);
        message.appendInt(this.furniClickSetting);
        message.appendInt(this.userSource);
        message.appendInt(0);
        message.appendInt(type.code);
        message.appendInt(this.getDelay());
        message.appendInt(0);
    }

    @Override
    public boolean saveData(WiredSettings settings, GameClient gameClient) {
        int[] params = settings.getIntParams();
        this.userSource = params.length > 2 ? WiredUtilityPayloadGuard.userSource(params[2]) : 0;
        this.userClickSetting = params.length > 0 ? Math.max(0, Math.min(2, params[0])) : 0;
        this.furniClickSetting = params.length > 1 ? Math.max(0, Math.min(1, params[1])) : 0;

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

    @Override
    public boolean requiresTriggeringUser() {
        return this.userSource == 0;
    }

    public int getUserClickSetting() {
        return this.userClickSetting;
    }

    public int getFurniClickSetting() {
        return this.furniClickSetting;
    }

    private static class JsonData {
        private final Integer userSource;
        private final int userClickSetting;
        private final int furniClickSetting;
        private final List<Integer> furniIds;

        public JsonData(int userClickSetting, int furniClickSetting, List<Integer> furniIds, int userSource) {
            this.userSource = userSource == 0 ? null : userSource;
            this.userClickSetting = userClickSetting;
            this.furniClickSetting = furniClickSetting;
            this.furniIds = furniIds;
        }
    }
}
