package com.eu.habbo.habbohotel.items.interactions.wired.effects;

import com.eu.habbo.Emulator;
import com.eu.habbo.habbohotel.gameclients.GameClient;
import com.eu.habbo.habbohotel.items.Item;
import com.eu.habbo.habbohotel.items.interactions.wired.WiredSettings;
import com.eu.habbo.habbohotel.rooms.Room;
import com.eu.habbo.habbohotel.rooms.RoomUnit;
import com.eu.habbo.habbohotel.wired.WiredEffectType;
import com.eu.habbo.habbohotel.wired.core.WiredContext;
import com.eu.habbo.habbohotel.wired.core.WiredManager;
import com.eu.habbo.habbohotel.wired.core.WiredSourceUtil;
import com.eu.habbo.messages.ServerMessage;
import com.google.gson.JsonObject;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Collections;

/**
 * Keep the historical whisper superclass: third-party plugins compiled against the released
 * emulator link against that exact superclass. The effect-specific state and execution remain
 * owned by this class.
 */
public class WiredEffectGiveEffect extends WiredEffectWhisper {
    private int effectId = 0;
    private int priority = 2;
    private int effectType = 0; // 0 = give, 1 = remove

    public WiredEffectGiveEffect(ResultSet set, Item baseItem) throws SQLException {
        super(set, baseItem);
    }

    public WiredEffectGiveEffect(int id, int userId, Item item, String extradata, int limitedStack, int limitedSells) {
        super(id, userId, item, extradata, limitedStack, limitedSells);
    }

    @Override
    public WiredEffectType getType() {
        return WiredEffectType.GIVE_EFFECT;
    }

    /**
     * The SWF action applies the effect to the user that triggered the stack.
     * Keep this explicit instead of inheriting the whisper implementation's
     * user-source state, since this action has no user-source selector.
     */
    @Override
    public boolean requiresTriggeringUser() {
        return true;
    }

    @Override
    public boolean execute(RoomUnit roomUnit, Room room, Object[] stuff) {
        if (roomUnit == null || room == null) return false;
        if (this.effectType == 0 && this.effectId >= 0) {
            room.giveEffect(roomUnit, this.effectId, Integer.MAX_VALUE);
        } else if (this.effectType == 1) {
            room.giveEffect(roomUnit, 0, -1);
        }
        return true;
    }

    @Override
    public void execute(WiredContext ctx) {
        Room room = ctx.room();
        if (room == null) return;

        for (RoomUnit roomUnit : WiredSourceUtil.resolveUsers(ctx, WiredSourceUtil.SOURCE_TRIGGER)) {
            execute(roomUnit, room, null);
        }
    }

    @Override
    public String getWiredData() {
        return WiredManager.getGson().toJson(new JsonData(this.effectId, this.priority, this.effectType, this.getDelay()));
    }

    @Override
    public void loadWiredData(ResultSet set, Room room) throws SQLException {
        String wiredData = extractWiredDataPayload(set);
        if (wiredData != null && wiredData.startsWith("{")) {
            try {
                JsonObject obj = WiredManager.getGson().fromJson(wiredData, JsonObject.class);
                if (obj.has("intParams")) {
                    int[] params = WiredManager.getGson().fromJson(obj.get("intParams"), int[].class);
                    if (params != null && params.length >= 3) {
                        this.effectId = params[0];
                        this.priority = params[1];
                        this.effectType = params[2];
                    }
                } else if (obj.has("effectId")) {
                    JsonData data = WiredManager.getGson().fromJson(wiredData, JsonData.class);
                    this.effectId = data.effectId;
                    this.priority = data.priority;
                    this.effectType = data.effectType;
                    this.setDelay(data.delay);
                }
            } catch (Exception ignored) {
                com.eu.habbo.WiredCompatibilityDiagnostics.record(
                        com.eu.habbo.WiredCompatibilityDiagnostics.FailurePoint.EFFECT_GIVE_EFFECT,
                        ignored);
            }
        } else if (wiredData != null && !wiredData.isEmpty()) {
            try {
                this.effectId = Integer.parseInt(wiredData.trim());
            } catch (Exception ignored) {
                com.eu.habbo.WiredCompatibilityDiagnostics.record(
                        com.eu.habbo.WiredCompatibilityDiagnostics.FailurePoint.EFFECT_GIVE_EFFECT,
                        ignored);
            }
        }
    }

    @Override
    public void onPickUp() {
        this.effectId = 0;
        this.priority = 2;
        this.effectType = 0;
        this.setDelay(0);
    }

    @Override
    public boolean saveData(WiredSettings settings, GameClient gameClient) {
        int[] params = settings.getIntParams();
        if (params.length >= 3) {
            this.effectId = Math.max(0, params[0]);
            this.priority = Math.max(0, Math.min(2, params[1]));
            this.effectType = params[2] == 1 ? 1 : 0;
        } else if (params.length > 0) {
            this.effectId = Math.max(0, params[0]);
        }
        this.setDelay(settings.getDelay());
        return true;
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
        message.appendInt(this.effectId);
        message.appendInt(this.priority);
        message.appendInt(this.effectType);
        message.appendInt(0);
        message.appendInt(WiredEffectType.GIVE_EFFECT.code);
        message.appendInt(this.getDelay());
        message.appendInt(0);
    }

    static class JsonData {
        int effectId;
        int priority;
        int effectType;
        int delay;

        public JsonData(int effectId, int priority, int effectType, int delay) {
            this.effectId = effectId;
            this.priority = priority;
            this.effectType = effectType;
            this.delay = delay;
        }
    }
}
