package com.eu.habbo.habbohotel.items.interactions.wired.extra;

import com.eu.habbo.habbohotel.gameclients.GameClient;
import com.eu.habbo.habbohotel.items.Item;
import com.eu.habbo.habbohotel.items.interactions.InteractionWiredExtra;
import com.eu.habbo.habbohotel.items.interactions.wired.WiredSettings;
import com.eu.habbo.habbohotel.rooms.Room;
import com.eu.habbo.habbohotel.rooms.RoomUnit;
import com.eu.habbo.habbohotel.wired.core.WiredManager;
import com.eu.habbo.messages.ServerMessage;

import java.sql.ResultSet;
import java.sql.SQLException;

/**
 * Time-utilities configuration add-on (furni classname {@code wf_xtra_var_time_util}). A configuration-holder
 * add-on in the {@link WiredExtraAnimationTime} family: it stores the time unit used when a wired
 * stack reads/writes time-based values and exposes it via {@link #getTimeUnit()}. Previously this
 * furni had {@code interaction_type='default'} and loaded as InteractionDefault (inert); this makes
 * it open + persist its dialog. The UI-builder mask/source configuration is also preserved;
 * date/time subvariables are computed by the variable runtime in UTC.
 */
public class WiredExtraTimeUtilities extends InteractionWiredExtra implements com.eu.habbo.habbohotel.wired.core.WiredDerivedVariableBox {
    public static final int CODE = 98;

    public static final int UNIT_MILLISECONDS = 0;
    public static final int UNIT_SECONDS = 1;
    public static final int UNIT_MINUTES = 2;
    public static final int UNIT_HOURS = 3;
    private static final int UNIT_MIN = UNIT_MILLISECONDS;
    private static final int UNIT_MAX = UNIT_HOURS;

    private int timeUnit = UNIT_SECONDS;
    private Integer subvariableMask;
    private int sourceMode;
    private static final int SUPPORTED_MASK = 0x07f007fe;

    public WiredExtraTimeUtilities(ResultSet set, Item baseItem) throws SQLException {
        super(set, baseItem);
    }

    public WiredExtraTimeUtilities(int id, int userId, Item item, String extradata, int limitedStack, int limitedSells) {
        super(id, userId, item, extradata, limitedStack, limitedSells);
    }

    @Override
    public boolean execute(RoomUnit roomUnit, Room room, Object[] stuff) {
        return false;
    }

    @Override
    public boolean saveData(WiredSettings settings, GameClient gameClient) {
        if (settings.getIntParams().length >= 2) {
            this.subvariableMask = settings.getIntParams()[0] & SUPPORTED_MASK;
            this.sourceMode = Math.max(0, Math.min(2, settings.getIntParams()[1]));
            return true;
        }
        this.subvariableMask = null;
        int value = (settings.getIntParams().length > 0) ? settings.getIntParams()[0] : this.timeUnit;

        if (value == this.timeUnit && settings.getStringParam() != null && !settings.getStringParam().isEmpty()) {
            try {
                value = Integer.parseInt(settings.getStringParam());
            } catch (NumberFormatException ignored) {
                value = this.timeUnit;
            }
        }

        this.timeUnit = normalizeUnit(value);
        return true;
    }

    @Override
    public String getWiredData() {
        return this.subvariableMask == null
                ? WiredManager.getGson().toJson(java.util.Collections.singletonMap("timeUnit", this.timeUnit))
                : WiredManager.getGson().toJson(new JsonData(this.timeUnit, this.subvariableMask, this.sourceMode));
    }

    @Override
    public void serializeWiredData(ServerMessage message, Room room) {
        message.appendBoolean(false);
        message.appendInt(0);
        message.appendInt(0);
        message.appendInt(this.getBaseItem().getSpriteId());
        message.appendInt(this.getId());
        message.appendString("");
        message.appendInt(this.subvariableMask == null ? 1 : 2);
        message.appendInt(this.subvariableMask == null ? this.timeUnit : this.subvariableMask);
        if (this.subvariableMask != null) message.appendInt(this.sourceMode);
        message.appendInt(0);
        message.appendInt(CODE);
        message.appendInt(0);
        message.appendInt(0);
    }

    @Override
    public void loadWiredData(ResultSet set, Room room) throws SQLException {
        this.onPickUp();

        String wiredData = set.getString("wired_data");
        if (wiredData == null || wiredData.isEmpty()) {
            return;
        }

        if (wiredData.startsWith("{")) {
            JsonData data = WiredExtraPayloadGuard.fromJson(wiredData, JsonData.class);
            this.timeUnit = normalizeUnit((data != null) ? data.timeUnit : UNIT_SECONDS);
            if (data != null && data.subvariableMask != null) {
                this.subvariableMask = data.subvariableMask & SUPPORTED_MASK;
                this.sourceMode = Math.max(0, Math.min(2, data.sourceMode));
            }
            return;
        }

        try {
            this.timeUnit = normalizeUnit(Integer.parseInt(wiredData));
        } catch (NumberFormatException ignored) {
            this.timeUnit = UNIT_SECONDS;
        }
    }

    @Override
    public void onPickUp() {
        this.timeUnit = UNIT_SECONDS;
        this.subvariableMask = null;
        this.sourceMode = 0;
    }

    @Override
    public void onWalk(RoomUnit roomUnit, Room room, Object[] objects) throws Exception {

    }

    @Override
    public boolean hasConfiguration() {
        return true;
    }

    public int getTimeUnit() {
        return this.timeUnit;
    }

    public int getSourceMode() { return sourceMode; }
    @Override public java.util.List<Integer> getSelectedSubvariables() {
        var result = new java.util.ArrayList<Integer>();
        for (int id = 1; id <= 26; id++) if (hasSubvariable(id)) result.add(id);
        return result;
    }
    @Override public boolean hasSubvariable(int id) {
        return id >= 1 && id <= 26 && subvariableMask != null && (SUPPORTED_MASK & (1 << id)) != 0 && (subvariableMask & (1 << id)) != 0;
    }
    @Override public String subvariableKey(int id) {
        return switch(id) {
            case 1 -> "milliseconds_of_seconds"; case 2 -> "seconds_of_minute";
            case 3 -> "minute_of_hour"; case 4 -> "hour_of_day"; case 5 -> "day_of_week";
            case 6 -> "day_of_month"; case 7 -> "day_of_year"; case 8 -> "week_of_year";
            case 9 -> "month_of_year"; case 10 -> "year";
            case 20 -> "millisecond"; case 21 -> "second"; case 22 -> "minute";
            case 23 -> "hour"; case 24 -> "day"; case 25 -> "week"; case 26 -> "month";
            default -> "";
        };
    }
    // Polaris exposes Unix timestamps in seconds, including @current_time.
    @Override public Integer derive(int id, Integer value) { return value == null ? null : deriveTime(id, value.longValue() * 1000L); }
    public Integer deriveTime(int id, long milliseconds) {
        if (!hasSubvariable(id)) return null;
        var date = java.time.Instant.ofEpochMilli(milliseconds).atZone(java.time.ZoneOffset.UTC);
        long result = switch(id) {
            case 1 -> Math.floorMod(milliseconds, 1000); case 2 -> date.getSecond();
            case 3 -> date.getMinute(); case 4 -> date.getHour(); case 5 -> date.getDayOfWeek().getValue();
            case 6 -> date.getDayOfMonth(); case 7 -> date.getDayOfYear();
            case 8 -> date.get(java.time.temporal.IsoFields.WEEK_OF_WEEK_BASED_YEAR);
            case 9 -> date.getMonthValue(); case 10 -> date.getYear();
            case 20 -> milliseconds; case 21 -> Math.floorDiv(milliseconds, 1000);
            case 22 -> Math.floorDiv(milliseconds, 60000); case 23 -> Math.floorDiv(milliseconds, 3600000);
            case 24 -> Math.floorDiv(milliseconds, 86400000); case 25 -> Math.floorDiv(milliseconds, 604800000);
            case 26 -> (date.getYear() - 1970L) * 12 + date.getMonthValue() - 1;
            default -> 0;
        };
        return (int)Math.max(Integer.MIN_VALUE, Math.min(Integer.MAX_VALUE, result));
    }

    private static int normalizeUnit(int value) {
        return Math.max(UNIT_MIN, Math.min(UNIT_MAX, value));
    }

    static class JsonData {
        int timeUnit;

        Integer subvariableMask;
        int sourceMode;
        JsonData(int timeUnit, Integer subvariableMask, int sourceMode) {
            this.timeUnit = timeUnit;
            this.subvariableMask = subvariableMask;
            this.sourceMode = sourceMode;
        }
    }
}
