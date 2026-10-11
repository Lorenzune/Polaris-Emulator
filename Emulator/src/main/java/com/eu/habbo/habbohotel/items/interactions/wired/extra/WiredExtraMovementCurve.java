package com.eu.habbo.habbohotel.items.interactions.wired.extra;

import com.eu.habbo.habbohotel.gameclients.GameClient;
import com.eu.habbo.habbohotel.items.Item;
import com.eu.habbo.habbohotel.items.interactions.InteractionWiredExtra;
import com.eu.habbo.habbohotel.items.interactions.wired.WiredSettings;
import com.eu.habbo.habbohotel.rooms.Room;
import com.eu.habbo.habbohotel.rooms.RoomUnit;
import com.eu.habbo.habbohotel.users.HabboItem;
import com.eu.habbo.habbohotel.wired.core.WiredContext;
import com.eu.habbo.habbohotel.wired.core.WiredContextVariableSupport;
import com.eu.habbo.habbohotel.wired.core.WiredInternalVariableSupport;
import com.eu.habbo.habbohotel.wired.core.WiredManager;
import com.eu.habbo.habbohotel.wired.core.WiredSourceUtil;
import com.eu.habbo.messages.ServerMessage;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;

/** Native Habbo signed movement parabola, configured by a literal or variable.
 * The former easing constants/getters and JSON fields remain readable for compatibility.
 */
public class WiredExtraMovementCurve extends InteractionWiredExtra {
    public static final int CODE = 97;

    public static final int CURVE_LINEAR = 0;
    public static final int CURVE_EASE_IN = 1;
    public static final int CURVE_EASE_OUT = 2;
    public static final int CURVE_EASE_IN_OUT = 3;
    public static final int CURVE_BOUNCE = 4;
    public static final int CURVE_ELASTIC = 5;
    public static final int CURVE_DROP = 6;
    private static final int CURVE_MIN = CURVE_LINEAR;
    private static final int CURVE_MAX = CURVE_DROP;
    public static final int INTENSITY_MIN = 0;
    public static final int INTENSITY_MAX = 100;
    public static final int INTENSITY_DEFAULT = 100;

    private int curveType = CURVE_LINEAR;
    private int intensity = INTENSITY_DEFAULT;
    private CurveConfig config;

    public WiredExtraMovementCurve(ResultSet set, Item baseItem) throws SQLException {
        super(set, baseItem);
    }

    public WiredExtraMovementCurve(
            int id, int userId, Item item, String extradata, int limitedStack, int limitedSells) {
        super(id, userId, item, extradata, limitedStack, limitedSells);
    }

    @Override
    public boolean execute(RoomUnit roomUnit, Room room, Object[] stuff) {
        return false;
    }

    @Override
    public boolean saveData(WiredSettings settings, GameClient gameClient) {
        if (settings.getIntParams().length >= 3) {
            int[] params = settings.getIntParams();
            int[] variableIds = settings.getVariableIds();
            String token = settings.getStringParam();
            if ((token == null || token.isBlank()) && variableIds != null && variableIds.length > 0)
                token = String.valueOf(variableIds[0]);
            this.config = new CurveConfig(
                    params[0] == 1 ? 1 : 0,
                    clampStrength(params[1]),
                    params[2],
                    params.length > 3 ? params[3] : 0,
                    token == null ? "" : token,
                    settings.getFurniIds());
            return true;
        }
        this.config = null;
        int value = (settings.getIntParams().length > 0) ? settings.getIntParams()[0] : this.curveType;

        if (value == this.curveType
                && settings.getStringParam() != null
                && !settings.getStringParam().isEmpty()) {
            try {
                value = Integer.parseInt(settings.getStringParam());
            } catch (NumberFormatException ignored) {
                value = this.curveType;
            }
        }

        this.curveType = normalizeCurve(value);
        if (settings.getIntParams().length > 1) {
            this.intensity = normalizeIntensity(settings.getIntParams()[1]);
        }
        return true;
    }

    @Override
    public String getWiredData() {
        return WiredManager.getGson().toJson(new JsonData(this.curveType, this.intensity, this.config));
    }

    @Override
    public void serializeWiredData(ServerMessage message, Room room) {
        message.appendBoolean(false);
        int[] selected = this.config == null || this.config.furniIds == null ? new int[0] : this.config.furniIds;
        message.appendInt(5);
        message.appendInt(selected.length);
        for (int id : selected) message.appendInt(id);
        message.appendInt(this.getBaseItem().getSpriteId());
        message.appendInt(this.getId());
        message.appendString(this.config == null ? "" : this.config.token);
        message.appendInt(4);
        message.appendInt(this.config == null ? 0 : this.config.mode);
        message.appendInt(this.config == null ? legacyStrength() : clampStrength(this.config.strength));
        message.appendInt(this.config == null ? 0 : this.config.target);
        message.appendInt(this.config == null ? 0 : this.config.source);
        message.appendInt(0);
        message.appendInt(CODE);
        message.appendInt(0);
        message.appendInt(0);
        message.appendInt(this.config != null && this.config.mode == 1 ? 1 : 0);
        if (this.config != null && this.config.mode == 1) message.appendString(this.config.token);
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
            this.config = data != null ? data.config : null;
            this.curveType = normalizeCurve((data != null) ? data.curveType : CURVE_LINEAR);
            this.intensity =
                    normalizeIntensity((data != null && data.intensity != null) ? data.intensity : INTENSITY_DEFAULT);
            return;
        }

        try {
            this.curveType = normalizeCurve(Integer.parseInt(wiredData));
        } catch (NumberFormatException ignored) {
            this.curveType = CURVE_LINEAR;
        }
    }

    @Override
    public void onPickUp() {
        this.curveType = CURVE_LINEAR;
        this.intensity = INTENSITY_DEFAULT;
        this.config = null;
    }

    @Override
    public void onWalk(RoomUnit roomUnit, Room room, Object[] objects) throws Exception {}

    @Override
    public boolean hasConfiguration() {
        return true;
    }

    public int getCurveType() {
        return this.curveType;
    }

    public int getIntensity() {
        return this.intensity;
    }

    /** Native Habbo movement parabola strength; absent variables leave movement linear. */
    public Integer resolveCurveStrength(WiredContext ctx, HabboItem movingItem) {
        if (this.config == null) return legacyStrength();
        if (this.config.mode != 1) return clampStrength(this.config.strength);
        if (ctx == null || ctx.room() == null || this.config.token == null) return null;
        Room room = ctx.room();
        String token = this.config.token;
        boolean internal = token.startsWith("internal:");
        String key = internal ? token.substring(9) : "";
        int variableId = 0;
        if (!internal) {
            try {
                variableId = Integer.parseInt(token.replace("custom:", ""));
            } catch (NumberFormatException ignored) {
                return null;
            }
        }
        Integer value = null;
        switch (this.config.target) {
            case -10 ->
                value = internal
                        ? WiredInternalVariableSupport.readRoomValue(room, key)
                        : (room.getRoomVariableManager().hasVariable(variableId)
                                ? room.getRoomVariableManager().getCurrentValue(variableId)
                                : null);
            case -20 ->
                value = internal
                        ? WiredInternalVariableSupport.readContextValue(ctx, key)
                        : WiredContextVariableSupport.getCurrentValue(ctx, variableId);
            case 1 -> {
                for (RoomUnit unit : WiredSourceUtil.resolveUsers(ctx, this.config.source)) {
                    var habbo = room.getHabbo(unit);
                    value = internal
                            ? WiredInternalVariableSupport.readUserValue(room, unit, key)
                            : habbo == null
                                            || !room.getUserVariableManager()
                                                    .hasVariable(
                                                            habbo.getHabboInfo().getId(), variableId)
                                    ? null
                                    : room.getUserVariableManager()
                                            .getCurrentValue(
                                                    habbo.getHabboInfo().getId(), variableId);
                    if (value != null) break;
                }
            }
            case 0 -> {
                var selected = new ArrayList<HabboItem>();
                if (this.config.furniIds != null)
                    for (int id : this.config.furniIds) {
                        HabboItem item = room.getHabboItem(id);
                        if (item != null) selected.add(item);
                    }
                for (HabboItem item : WiredSourceUtil.resolveItems(ctx, this.config.source, selected)) {
                    value = internal
                            ? WiredInternalVariableSupport.readFurniValue(room, item, key)
                            : (room.getFurniVariableManager().hasVariable(item.getId(), variableId)
                                    ? room.getFurniVariableManager().getCurrentValue(item.getId(), variableId)
                                    : null);
                    if (value != null) break;
                }
            }
            default -> {
                return null;
            }
        }
        return value == null ? null : clampStrength(value);
    }

    private int legacyStrength() {
        return this.curveType == CURVE_LINEAR ? 0 : this.intensity;
    }

    private static int clampStrength(int value) {
        return Math.max(-1000, Math.min(1000, value));
    }

    private static class CurveConfig {
        int mode, strength, target, source;
        String token;
        int[] furniIds;

        CurveConfig(int mode, int strength, int target, int source, String token, int[] furniIds) {
            this.mode = mode;
            this.strength = strength;
            this.target = target;
            this.source = source;
            this.token = token;
            this.furniIds = furniIds == null ? new int[0] : furniIds.clone();
        }
    }

    private static int normalizeCurve(int value) {
        return Math.max(CURVE_MIN, Math.min(CURVE_MAX, value));
    }

    private static int normalizeIntensity(int value) {
        return Math.max(INTENSITY_MIN, Math.min(INTENSITY_MAX, value));
    }

    static class JsonData {
        int curveType;
        // Wrapper so payloads saved before the intensity field existed load as "default", not 0.
        Integer intensity;
        CurveConfig config;

        JsonData(int curveType, int intensity, CurveConfig config) {
            this.config = config;
            this.curveType = curveType;
            this.intensity = intensity;
        }
    }
}
