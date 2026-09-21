package com.eu.habbo.habbohotel.items.interactions.wired.conditions;

import com.eu.habbo.habbohotel.items.Item;
import com.eu.habbo.habbohotel.items.interactions.InteractionWired;
import com.eu.habbo.habbohotel.items.interactions.InteractionWiredCondition;
import com.eu.habbo.habbohotel.items.interactions.wired.WiredSettings;
import com.eu.habbo.habbohotel.rooms.Room;
import com.eu.habbo.habbohotel.rooms.RoomUnit;
import com.eu.habbo.habbohotel.users.Habbo;
import com.eu.habbo.habbohotel.wired.WiredConditionType;
import com.eu.habbo.habbohotel.wired.core.WiredContext;
import com.eu.habbo.habbohotel.wired.core.WiredManager;
import com.eu.habbo.messages.ServerMessage;

import java.sql.ResultSet;
import java.sql.SQLException;

public class WiredConditionUserLevel extends InteractionWiredCondition {
    public static final WiredConditionType type = WiredConditionType.USER_LEVEL;

    private int level = 1;
    private int comparison = 0; // 0 = equals, 1 = greater or equal, 2 = less or equal

    public WiredConditionUserLevel(ResultSet set, Item baseItem) throws SQLException {
        super(set, baseItem);
    }

    public WiredConditionUserLevel(int id, int userId, Item item, String extradata, int limitedStack, int limitedSells) {
        super(id, userId, item, extradata, limitedStack, limitedSells);
    }

    @Override
    public WiredConditionType getType() {
        return type;
    }

    @Override
    public boolean evaluate(WiredContext ctx) {
        if (ctx.room() == null) {
            return false;
        }

        RoomUnit unit = ctx.actor().orElse(null);
        if (unit == null) {
            return false;
        }

        Habbo habbo = ctx.room().getHabbo(unit);
        if (habbo == null) {
            return false;
        }

        int userLevel = Math.max(1, Math.min(30, (habbo.getHabboStats().getAchievementScore() / 100) + 1));

        switch (this.comparison) {
            case 0:
                return userLevel == this.level;
            case 1:
                return userLevel >= this.level;
            case 2:
                return userLevel <= this.level;
            default:
                return false;
        }
    }

    @Override
    public boolean execute(RoomUnit roomUnit, Room room, Object[] stuff) {
        return false;
    }

    @Override
    public void serializeWiredData(ServerMessage message, Room room) {
        message.appendBoolean(false);
        message.appendInt(5);
        message.appendInt(0);
        message.appendInt(this.getBaseItem().getSpriteId());
        message.appendInt(this.getId());
        message.appendString("");
        message.appendInt(2);
        message.appendInt(this.level);
        message.appendInt(this.comparison);
        message.appendInt(0);
        message.appendInt(this.getType().code);
        message.appendInt(0);
        message.appendInt(0);
    }

    @Override
    public boolean saveData(WiredSettings settings) {
        int[] params = settings.getIntParams();
        this.level = params.length > 0 ? Math.max(1, Math.min(30, params[0])) : 1;
        this.comparison = params.length > 1 ? params[1] : 0;
        return true;
    }

    @Override
    public String getWiredData() {
        return WiredManager.getGson().toJson(new JsonData(this.level, this.comparison));
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
                this.level = data.level;
                this.comparison = data.comparison;
            }
        }
    }

    @Override
    public void onPickUp() {
        this.level = 1;
        this.comparison = 0;
    }

    private static class JsonData {
        private final int level;
        private final int comparison;

        public JsonData(int level, int comparison) {
            this.level = level;
            this.comparison = comparison;
        }
    }
}
