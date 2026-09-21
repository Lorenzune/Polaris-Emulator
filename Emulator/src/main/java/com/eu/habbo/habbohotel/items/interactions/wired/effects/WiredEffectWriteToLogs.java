package com.eu.habbo.habbohotel.items.interactions.wired.effects;

import com.eu.habbo.Emulator;
import com.eu.habbo.WiredCompatibilityDiagnostics;
import com.eu.habbo.habbohotel.gameclients.GameClient;
import com.eu.habbo.habbohotel.items.Item;
import com.eu.habbo.habbohotel.items.interactions.InteractionWiredEffect;
import com.eu.habbo.habbohotel.items.interactions.wired.WiredSettings;
import com.eu.habbo.habbohotel.rooms.Room;
import com.eu.habbo.habbohotel.rooms.RoomUnit;
import com.eu.habbo.habbohotel.wired.WiredEffectType;
import com.eu.habbo.habbohotel.wired.core.WiredContext;
import com.eu.habbo.habbohotel.wired.core.WiredManager;
import com.eu.habbo.messages.ServerMessage;
import com.google.gson.JsonObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.ResultSet;
import java.sql.SQLException;

public class WiredEffectWriteToLogs extends InteractionWiredEffect {
    private static final Logger LOGGER = LoggerFactory.getLogger(WiredEffectWriteToLogs.class);
    public static final WiredEffectType type = WiredEffectType.WRITE_TO_LOGS;

    private int logLevel = 0; // 0=info, 1=warn, 2=error, 3=debug
    private String logMessage = "";

    public WiredEffectWriteToLogs(ResultSet set, Item baseItem) throws SQLException {
        super(set, baseItem);
    }

    public WiredEffectWriteToLogs(int id, int userId, Item item, String extradata, int limitedStack, int limitedSells) {
        super(id, userId, item, extradata, limitedStack, limitedSells);
    }

    @Override
    public WiredEffectType getType() {
        return type;
    }

    @Override
    public boolean execute(RoomUnit roomUnit, Room room, Object[] stuff) {
        String msg = "[WiredLog Room " + (room != null ? room.getId() : 0) + "] " + this.logMessage;
        switch (this.logLevel) {
            case 1 -> LOGGER.warn(msg);
            case 2 -> LOGGER.error(msg);
            case 3 -> LOGGER.debug(msg);
            default -> LOGGER.info(msg);
        }
        return true;
    }

    @Override
    public void execute(WiredContext ctx) {
        execute(null, ctx.room(), null);
    }

    @Override
    public String getWiredData() {
        return WiredManager.getGson().toJson(new JsonData(this.logLevel, this.logMessage, this.getDelay()));
    }

    @Override
    public void loadWiredData(ResultSet set, Room room) throws SQLException {
        String wiredData = extractWiredDataPayload(set);
        if (wiredData != null && wiredData.startsWith("{")) {
            try {
                JsonObject obj = WiredManager.getGson().fromJson(wiredData, JsonObject.class);
                if (obj.has("intParams")) {
                    int[] params = WiredManager.getGson().fromJson(obj.get("intParams"), int[].class);
                    if (params != null && params.length > 0) {
                        this.logLevel = params[0];
                    }
                }
                if (obj.has("stringParam")) {
                    this.logMessage = obj.get("stringParam").getAsString();
                } else if (obj.has("logMessage")) {
                    JsonData data = WiredManager.getGson().fromJson(wiredData, JsonData.class);
                    this.logLevel = data.logLevel;
                    this.logMessage = data.logMessage;
                    this.setDelay(data.delay);
                }
            } catch (Exception ignored) {
                WiredCompatibilityDiagnostics.record(
                        WiredCompatibilityDiagnostics.FailurePoint.EFFECT_LOG_LEGACY, ignored);
            }
        }
    }

    @Override
    public void onPickUp() {
        this.logLevel = 0;
        this.logMessage = "";
        this.setDelay(0);
    }

    @Override
    public boolean saveData(WiredSettings settings, GameClient gameClient) {
        int[] params = settings.getIntParams();
        this.logLevel = (params.length > 0) ? Math.max(0, Math.min(3, params[0])) : 0;
        this.logMessage = settings.getStringParam() != null ? settings.getStringParam() : "";
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
        message.appendString(this.logMessage);
        message.appendInt(1);
        message.appendInt(this.logLevel);
        message.appendInt(0);
        message.appendInt(this.getType().code);
        message.appendInt(this.getDelay());
        message.appendInt(0);
    }

    static class JsonData {
        int logLevel;
        String logMessage;
        int delay;

        public JsonData(int logLevel, String logMessage, int delay) {
            this.logLevel = logLevel;
            this.logMessage = logMessage;
            this.delay = delay;
        }
    }
}
