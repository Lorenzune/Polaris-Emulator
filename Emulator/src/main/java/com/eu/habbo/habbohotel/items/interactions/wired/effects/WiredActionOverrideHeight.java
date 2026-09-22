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
import com.eu.habbo.habbohotel.wired.core.WiredSourceUtil;
import com.eu.habbo.messages.ServerMessage;
import com.eu.habbo.messages.outgoing.rooms.users.RoomUserStatusComposer;
import java.sql.ResultSet;
import java.sql.SQLException;

public class WiredActionOverrideHeight extends InteractionWiredEffect {
    public static final WiredEffectType type = WiredEffectType.OVERRIDE_HEIGHT;

    private int height = 0;
    private int overrideType = 0;

    public WiredActionOverrideHeight(ResultSet set, Item baseItem) throws SQLException {
        super(set, baseItem);
    }

    public WiredActionOverrideHeight(
            int id, int userId, Item item, String extradata, int limitedStack, int limitedSells) {
        super(id, userId, item, extradata, limitedStack, limitedSells);
    }

    @Override
    public void execute(WiredContext ctx) {
        if (ctx.room() == null) {
            return;
        }

        Room room = ctx.room();
        for (RoomUnit actor : WiredSourceUtil.resolveUsers(ctx, WiredSourceUtil.SOURCE_TRIGGER)) {
            this.apply(actor, room);
        }
    }

    @Override
    public boolean execute(RoomUnit roomUnit, Room room, Object[] stuff) {
        if (roomUnit == null || room == null) return false;
        this.apply(roomUnit, room);
        return true;
    }

    private void apply(RoomUnit roomUnit, Room room) {
        double targetZ = this.overrideType == 1
                ? room.getStackHeight(roomUnit.getX(), roomUnit.getY(), false)
                : this.height / 100.0;

        roomUnit.setZ(targetZ);
        roomUnit.setPreviousLocationZ(targetZ);
        room.sendComposer(new RoomUserStatusComposer(roomUnit).compose());
    }

    @Override
    public String getWiredData() {
        return WiredManager.getGson().toJson(new JsonData(this.height, this.overrideType));
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
                this.height = data.height;
                this.overrideType = data.overrideType;
            }
        }
    }

    @Override
    public void onPickUp() {
        this.height = 0;
        this.overrideType = 0;
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
        message.appendString("");
        message.appendInt(2);
        message.appendInt(this.height);
        message.appendInt(this.overrideType);
        message.appendInt(0);
        message.appendInt(type.code);
        message.appendInt(this.getDelay());
        message.appendInt(0);
    }

    @Override
    public boolean saveData(WiredSettings settings, GameClient gameClient) {
        int[] params = settings.getIntParams();
        this.height = params.length > 0 ? params[0] : 0;
        this.overrideType = params.length > 1 ? params[1] : 0;

        this.setDelay(settings.getDelay());
        return true;
    }

    private static class JsonData {
        private final int height;
        private final int overrideType;

        public JsonData(int height, int overrideType) {
            this.height = height;
            this.overrideType = overrideType;
        }
    }
}
