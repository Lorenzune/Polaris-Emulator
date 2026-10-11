package com.eu.habbo.habbohotel.items.interactions.wired.triggers;

import com.eu.habbo.habbohotel.items.Item;
import com.eu.habbo.habbohotel.items.interactions.InteractionWiredEffect;
import com.eu.habbo.habbohotel.items.interactions.InteractionWiredTrigger;
import com.eu.habbo.habbohotel.items.interactions.wired.WiredNativeTimerSettings;
import com.eu.habbo.habbohotel.items.interactions.wired.WiredSettings;
import com.eu.habbo.habbohotel.items.interactions.wired.WiredTimerInputGuard;
import com.eu.habbo.habbohotel.items.interactions.wired.WiredTriggerReset;
import com.eu.habbo.habbohotel.rooms.Room;
import com.eu.habbo.habbohotel.rooms.RoomUnit;
import com.eu.habbo.habbohotel.users.HabboItem;
import com.eu.habbo.habbohotel.wired.WiredTriggerType;
import com.eu.habbo.habbohotel.wired.core.WiredEvent;
import com.eu.habbo.habbohotel.wired.core.WiredManager;
import com.eu.habbo.habbohotel.wired.tick.WiredTickService;
import com.eu.habbo.habbohotel.wired.tick.WiredTickable;
import com.eu.habbo.habbohotel.wired.tick.WiredTimerClock;
import com.eu.habbo.messages.ServerMessage;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

/**
 * One-shot timer wired trigger that fires once after a set time.
 * <p>
 * Uses the new 50ms tick system via {@link WiredTickable} for accurate
 * timing. After firing, the timer waits for an explicit reset.
 * </p>
 */
public class WiredTriggerAtSetTime extends InteractionWiredTrigger implements WiredTickable, WiredTriggerReset {
    public static final WiredTriggerType type = WiredTriggerType.AT_GIVEN_TIME;
    private static final int STEP_MS = 500;
    private static final int MIN_DELAY = STEP_MS;

    /** The time in milliseconds until the trigger fires */
    public volatile int executeTime = MIN_DELAY;

    private final WiredTimerClock timerClock = new WiredTimerClock();

    public WiredTriggerAtSetTime(ResultSet set, Item baseItem) throws SQLException {
        super(set, baseItem);
    }

    public WiredTriggerAtSetTime(int id, int userId, Item item, String extradata, int limitedStack, int limitedSells) {
        super(id, userId, item, extradata, limitedStack, limitedSells);
    }

    @Override
    public boolean matches(HabboItem triggerItem, WiredEvent event) {
        // Only match if this timer is the one that actually fired
        return event.getSourceItem().map(item -> item.getId() == this.getId()).orElse(false);
    }

    @Deprecated
    @Override
    public boolean execute(RoomUnit roomUnit, Room room, Object[] stuff) {
        return false;
    }

    @Override
    public String getWiredData() {
        return WiredManager.getGson().toJson(new JsonData(this.executeTime));
    }

    @Override
    public void loadWiredData(ResultSet set, Room room) throws SQLException {
        String wiredData = set.getString("wired_data");

        Integer storedExecuteTime = null;
        try {
            if (wiredData != null && wiredData.startsWith("{")) {
                JsonData data = WiredManager.getGson().fromJson(wiredData, JsonData.class);
                storedExecuteTime = data != null ? data.executeTime : null;
            } else if (wiredData != null && wiredData.length() >= 1) {
                storedExecuteTime = Integer.parseInt(wiredData);
            }
        } catch (RuntimeException ignored) {
            storedExecuteTime = null;
        }

        this.executeTime = WiredTimerInputGuard.normalizeStoredMillis(storedExecuteTime, MIN_DELAY, MIN_DELAY);

        // Initialize for tick system - will be registered by RoomItemManager
        this.resetTimer();
    }

    @Override
    public void onPickUp() {
        this.executeTime = MIN_DELAY;
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
        message.appendInt(this.executeTime / 500);
        message.appendInt(1);
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
        this.executeTime = WiredTimerInputGuard.fromClientUnits(settings.getIntParams()[0], STEP_MS, MIN_DELAY);

        this.resetTimer();

        return true;
    }

    // ========== WiredTickable Implementation ==========

    @Override
    public void onWiredTick(Room room, long tickCount, int tickIntervalMs) {
        if (this.getRoomId() == 0 || !room.isLoaded()) return;
        if (this.timerClock.poll(
                tickCount,
                tickIntervalMs,
                this.executeTime,
                false,
                () -> WiredManager.isTriggerExecutionAllowed(room, this, System.currentTimeMillis()))) {
            WiredManager.triggerTimerTick(room, this);
        }
    }

    @Override
    public void resetTimer() {
        this.timerClock.reset(WiredTickService.getInstance().getTickCount());
    }

    @Override
    public void onRegistered(Room room, long currentTimeMillis) {
        this.resetTimer();
    }

    @Override
    public void onUnregistered(Room room) {
        this.resetTimer();
    }

    @Override
    public boolean isOneShot() {
        return true; // One-shot timer, fires once then waits for reset
    }

    // ========== JSON Data ==========

    static class JsonData {
        int executeTime;

        public JsonData(int executeTime) {
            this.executeTime = executeTime;
        }
    }
}
