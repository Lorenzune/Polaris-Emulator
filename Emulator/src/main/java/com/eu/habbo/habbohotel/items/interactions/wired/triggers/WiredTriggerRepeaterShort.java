package com.eu.habbo.habbohotel.items.interactions.wired.triggers;

import com.eu.habbo.habbohotel.items.Item;
import com.eu.habbo.habbohotel.items.interactions.InteractionWiredEffect;
import com.eu.habbo.habbohotel.items.interactions.wired.WiredNativeTimerSettings;
import com.eu.habbo.habbohotel.items.interactions.wired.WiredSettings;
import com.eu.habbo.habbohotel.items.interactions.wired.WiredTimerInputGuard;
import com.eu.habbo.habbohotel.rooms.Room;
import com.eu.habbo.habbohotel.wired.WiredTriggerType;
import com.eu.habbo.habbohotel.wired.core.WiredManager;
import com.eu.habbo.messages.ServerMessage;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

public class WiredTriggerRepeaterShort extends WiredTriggerRepeater {
    public static final WiredTriggerType type = WiredTriggerType.PERIODICALLY_SHORT;
    public static final int STEP_MS = 50;
    public static final int DEFAULT_DELAY = STEP_MS;
    public static final int MIN_DELAY = STEP_MS;
    public static final int MAX_DELAY = 10 * STEP_MS;

    public WiredTriggerRepeaterShort(ResultSet set, Item baseItem) throws SQLException {
        super(set, baseItem);
        this.repeatTime = DEFAULT_DELAY;
    }

    public WiredTriggerRepeaterShort(
            int id, int userId, Item item, String extradata, int limitedStack, int limitedSells) {
        super(id, userId, item, extradata, limitedStack, limitedSells);
        this.repeatTime = DEFAULT_DELAY;
    }

    @Override
    public void loadWiredData(ResultSet set, Room room) throws SQLException {
        try {
            String wiredData = set.getString("wired_data");
            Integer stored = wiredData != null && wiredData.startsWith("{")
                    ? WiredManager.getGson().fromJson(wiredData, JsonData.class).repeatTime
                    : wiredData == null || wiredData.isEmpty() ? null : Integer.valueOf(wiredData);
            this.repeatTime = WiredTimerInputGuard.normalizeStoredMillis(stored, MIN_DELAY, DEFAULT_DELAY, MAX_DELAY);
        } catch (RuntimeException ignored) {
            this.repeatTime = DEFAULT_DELAY;
        }
        this.resetTimer();
    }

    @Override
    public void onPickUp() {
        this.repeatTime = DEFAULT_DELAY;
        this.resetTimer();
    }

    @Override
    public WiredTriggerType getType() {
        return type;
    }

    @Override
    public void serializeWiredData(ServerMessage message, Room room) {
        message.appendBoolean(false);
        message.appendInt(5);
        message.appendInt(0);
        message.appendInt(this.getBaseItem().getSpriteId());
        message.appendInt(this.getId());
        message.appendString("");
        message.appendInt(1);
        message.appendInt(this.repeatTime / STEP_MS);
        message.appendInt(0);
        message.appendInt(this.getType().code);

        if (!this.isTriggeredByRoomUnit()) {
            List<Integer> invalidTriggers = new ArrayList<>();
            for (InteractionWiredEffect effect : room.getRoomSpecialTypes().getEffects(this.getX(), this.getY())) {
                if (effect.requiresTriggeringUser()) {
                    invalidTriggers.add(effect.getBaseItem().getSpriteId());
                }
            }
            message.appendInt(invalidTriggers.size());
            for (Integer i : invalidTriggers) {
                message.appendInt(i);
            }
        } else {
            message.appendInt(0);
        }
    }

    @Override
    public boolean saveData(WiredSettings settings) {
        settings = WiredNativeTimerSettings.normalize(this, settings);
        if (settings.getIntParams().length < 1) return false;

        this.repeatTime =
                WiredTimerInputGuard.fromClientUnits(settings.getIntParams()[0], STEP_MS, MIN_DELAY, MAX_DELAY);

        this.resetTimer();
        return true;
    }

    @Override
    public void onWiredTick(Room room, long tickCount, int tickIntervalMs) {
        if (this.getRoomId() == 0 || !room.isLoaded()) return;
        if (this.timerClock.poll(
                tickCount,
                tickIntervalMs,
                this.repeatTime,
                true,
                () -> WiredManager.isTriggerExecutionAllowed(room, this, System.currentTimeMillis()))) {
            WiredManager.triggerTimerRepeatShort(room, this);
        }
    }

    public int getRepeatTimeMs() {
        return this.repeatTime;
    }
}
