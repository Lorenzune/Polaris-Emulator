package com.eu.habbo.habbohotel.items.interactions.wired.extra;

import com.eu.habbo.Emulator;
import com.eu.habbo.WiredCompatibilityDiagnostics;
import com.eu.habbo.habbohotel.gameclients.GameClient;
import com.eu.habbo.habbohotel.items.Item;
import com.eu.habbo.habbohotel.items.interactions.InteractionWiredExtra;
import com.eu.habbo.habbohotel.items.interactions.wired.WiredSettings;
import com.eu.habbo.habbohotel.rooms.Room;
import com.eu.habbo.habbohotel.rooms.RoomUnit;
import com.eu.habbo.habbohotel.wired.core.WiredManager;
import com.eu.habbo.messages.ServerMessage;
import com.google.gson.annotations.SerializedName;
import java.sql.ResultSet;
import java.sql.SQLException;

public abstract class WiredExtraVariableFxBase extends InteractionWiredExtra {

    protected int sourceType = 0;
    protected int visibility = 2;
    protected int showMode = 0;
    protected int changeFilter = 0;
    protected boolean showOnMouseHover = false;
    protected int showDuration = 2000;
    protected int styleId = 0;
    protected int colorId = 1;
    protected int widthId = 0;
    protected int rendererId = 0;
    protected int defaultMinValue = 0;
    protected int defaultMaxValue = 100;
    protected boolean overrideMinEnabled = false;
    protected boolean overrideMaxEnabled = false;
    protected int overrideMinTarget = 0;
    protected int overrideMaxTarget = 0;
    protected int audienceVariableValue = 0;
    protected int segments = 0;
    protected int subRendererId = 0;
    protected int iconAlignment = 0;
    protected String icon = "";
    protected String variableName = "";
    protected boolean hasValue = true;
    protected String overrideMinVariableId = "";
    protected String overrideMaxVariableId = "";
    protected String audienceVariableId = "";

    public WiredExtraVariableFxBase(ResultSet set, Item baseItem) throws SQLException {
        super(set, baseItem);
    }

    public WiredExtraVariableFxBase(
            int id, int userId, Item item, String extradata, int limitedStack, int limitedSells) {
        super(id, userId, item, extradata, limitedStack, limitedSells);
    }

    public abstract int getCode();

    public abstract int getCategoryId();

    public String getDefaultVariableName() {
        switch (getCategoryId()) {
            case 0:
                return "hp";
            case 1:
                return "prog";
            case 2:
                return "level";
            case 3:
                return "status";
            case 4:
                return "boss_hp";
            case 5:
                return "number";
            default:
                return "varfx";
        }
    }

    public String getVariableName() {
        return (this.variableName != null && !this.variableName.isEmpty())
                ? this.variableName
                : this.getDefaultVariableName();
    }

    public void setVariableName(String variableName) {
        this.variableName = variableName;
    }

    public boolean hasValue() {
        return this.hasValue;
    }

    public void setHasValue(boolean hasValue) {
        this.hasValue = hasValue;
    }

    public boolean isPermanentAvailability() {
        return false;
    }

    public boolean isSharedAvailability() {
        return false;
    }

    public int getStyleId() {
        return this.styleId;
    }

    public int getColorId() {
        return this.colorId;
    }

    public int getWidthId() {
        return this.widthId;
    }

    public int getRendererId() {
        return this.rendererId;
    }

    public int getDefaultMinValue() {
        return this.defaultMinValue;
    }

    public int getDefaultMaxValue() {
        return this.defaultMaxValue;
    }

    public int getSegments() {
        return this.segments;
    }

    public String getIcon() {
        return this.icon != null ? this.icon : "";
    }

    public int getSourceType() {
        return this.sourceType;
    }

    public int getVisibility() {
        return this.visibility;
    }

    public int getSubRendererId() {
        return this.subRendererId;
    }

    public int getIconAlignment() {
        return this.iconAlignment;
    }

    public int getShowMode() {
        return this.showMode;
    }

    public int getChangeFilter() {
        return this.changeFilter;
    }

    public boolean isShowOnMouseHover() {
        return this.showOnMouseHover;
    }

    public int getShowDuration() {
        return this.showDuration;
    }

    public String getAudienceVariableId() {
        return this.audienceVariableId;
    }

    public int getAudienceVariableValue() {
        return this.audienceVariableValue;
    }

    public boolean isOverrideMinEnabled() {
        return this.overrideMinEnabled;
    }

    public boolean isOverrideMaxEnabled() {
        return this.overrideMaxEnabled;
    }

    public int getOverrideMinTarget() {
        return this.overrideMinTarget;
    }

    public int getOverrideMaxTarget() {
        return this.overrideMaxTarget;
    }

    public String getOverrideMinVariableId() {
        return this.overrideMinVariableId;
    }

    public String getOverrideMaxVariableId() {
        return this.overrideMaxVariableId;
    }

    @Override
    public boolean execute(RoomUnit roomUnit, Room room, Object[] stuff) {
        return false;
    }

    @Override
    public boolean saveData(WiredSettings settings, GameClient gameClient) {
        int[] intParams = settings.getIntParams();
        if (intParams != null && intParams.length >= 18) {
            this.sourceType = intParams[0];
            this.visibility = intParams[1];
            this.showMode = intParams[2];
            this.changeFilter = intParams[3];
            this.showOnMouseHover = intParams[4] != 0;
            this.showDuration = Math.max(1500, Math.min(20000, intParams[5]));
            this.styleId = intParams[6];
            this.colorId = intParams[7];
            this.widthId = intParams[8];
            this.rendererId = intParams[9];
            this.defaultMinValue = intParams[11];
            this.defaultMaxValue = intParams[13];
            this.overrideMinEnabled = intParams[14] != 0;
            this.overrideMaxEnabled = intParams[15] != 0;
            this.overrideMinTarget = intParams[16];
            this.overrideMaxTarget = intParams[17];
            if (intParams.length > 19) {
                this.audienceVariableValue = intParams[19];
            }
            if (intParams.length > 20) {
                this.segments = Math.max(0, Math.min(100, intParams[20]));
            }
            if (intParams.length > 21) {
                if (getCategoryId() == 2) {
                    this.subRendererId = intParams[21];
                } else if (getCategoryId() == 5) {
                    this.iconAlignment = intParams[21];
                }
            }

            sanitizeConfiguration();
        }

        String rawParam = settings.getStringParam() != null ? settings.getStringParam() : "";
        if (rawParam.contains("\t")) {
            String[] parts = rawParam.split("\t", 2);
            this.variableName = (parts[0] != null) ? parts[0].trim() : "";
            this.icon = (parts.length > 1 && parts[1] != null) ? parts[1].trim() : "";
        } else if (!rawParam.isEmpty() && !isKnownIcon(rawParam)) {
            this.variableName = rawParam.trim();
        } else {
            this.icon = rawParam.trim();
        }

        int[] variableIds = settings.getVariableIds();
        if (variableIds != null) {
            if (variableIds.length > 0) this.overrideMinVariableId = String.valueOf(variableIds[0]);
            if (variableIds.length > 1) this.overrideMaxVariableId = String.valueOf(variableIds[1]);
            if (variableIds.length > 2) this.audienceVariableId = String.valueOf(variableIds[2]);
        }
        if (this.visibility != 3 && this.visibility != 4) {
            this.audienceVariableId = "";
            this.audienceVariableValue = 0;
        }

        String wiredJson = this.getWiredData();
        this.needsUpdate(true);

        try (java.sql.Connection connection =
                        Emulator.getDatabase().getDataSource().getConnection();
                java.sql.PreparedStatement statement =
                        connection.prepareStatement("UPDATE items SET wired_data = ? WHERE id = ?")) {
            statement.setString(1, wiredJson);
            statement.setInt(2, this.getId());
            statement.execute();
        } catch (SQLException ignored) {
            WiredCompatibilityDiagnostics.record(
                    WiredCompatibilityDiagnostics.FailurePoint.EXTRA_VARIABLE_FX_PERSISTENCE, ignored);
        }

        Room room = (gameClient != null && gameClient.getHabbo() != null)
                ? gameClient.getHabbo().getHabboInfo().getCurrentRoom()
                : null;
        if (room != null) {
            room.updateItem(this);
            if (room.getUserVariableManager() != null) {
                room.getUserVariableManager().broadcastSnapshot();
            }
            if (room.getFurniVariableManager() != null) {
                room.getFurniVariableManager().broadcastSnapshot();
            }
        }
        return true;
    }

    private static boolean isKnownIcon(String str) {
        if (str == null || str.isEmpty()) return false;
        return str.equals("health")
                || str.equals("mana")
                || str.equals("energy")
                || str.equals("poison")
                || str.equals("burning")
                || str.equals("freezing")
                || str.equals("shield")
                || str.equals("honor")
                || str.equals("reputation")
                || str.equals("stamina")
                || str.equals("gold")
                || str.equals("gems")
                || str.equals("cash")
                || str.equals("droplet")
                || str.equals("eye")
                || str.equals("magic")
                || str.equals("cooldown")
                || str.equals("timeleft")
                || str.equals("battery")
                || str.equals("star_power")
                || str.equals("stealth")
                || str.equals("repairing")
                || str.equals("upgrading")
                || str.equals("wooden_logs")
                || str.equals("food")
                || str.equals("fish")
                || str.startsWith("ranch.")
                || str.startsWith("ranch_")
                || str.startsWith("misc_");
    }

    @Override
    public String getWiredData() {
        return WiredManager.getGson()
                .toJson(new JsonData(
                        this.sourceType,
                        this.visibility,
                        this.showMode,
                        this.changeFilter,
                        this.showOnMouseHover,
                        this.showDuration,
                        this.styleId,
                        this.colorId,
                        this.widthId,
                        this.rendererId,
                        this.defaultMinValue,
                        this.defaultMaxValue,
                        this.overrideMinEnabled,
                        this.overrideMaxEnabled,
                        this.overrideMinTarget,
                        this.overrideMaxTarget,
                        this.audienceVariableValue,
                        this.segments,
                        this.subRendererId,
                        this.iconAlignment,
                        this.icon,
                        this.getVariableName(),
                        this.overrideMinVariableId,
                        this.overrideMaxVariableId,
                        this.audienceVariableId));
    }

    public String resolveStackVariableName(Room room) {
        if (room == null) return (this.variableName != null) ? this.variableName : "";
        for (com.eu.habbo.habbohotel.users.HabboItem item : room.getFloorItems()) {
            if (item != null
                    && item.getX() == this.getX()
                    && item.getY() == this.getY()
                    && item.getId() != this.getId()) {
                if (item instanceof WiredExtraUserVariable userVar
                        && userVar.getVariableName() != null
                        && !userVar.getVariableName().isEmpty()) {
                    return userVar.getVariableName();
                }
                if (item instanceof WiredExtraFurniVariable furniVar
                        && furniVar.getVariableName() != null
                        && !furniVar.getVariableName().isEmpty()) {
                    return furniVar.getVariableName();
                }
                if (item instanceof WiredExtraContextVariable ctxVar
                        && ctxVar.getVariableName() != null
                        && !ctxVar.getVariableName().isEmpty()) {
                    return ctxVar.getVariableName();
                }
            }
        }
        return (this.variableName != null) ? this.variableName : "";
    }

    @Override
    public void serializeWiredData(ServerMessage message, Room room) {
        message.appendBoolean(false);
        message.appendInt(0);
        message.appendInt(0);
        message.appendInt(this.getBaseItem().getSpriteId());
        message.appendInt(this.getId());

        String targetVar = resolveStackVariableName(room);
        String fullParam = (!targetVar.isEmpty())
                ? (targetVar + "\t" + (this.icon != null ? this.icon : ""))
                : (this.icon != null ? this.icon : "");
        message.appendString(fullParam);

        int paramCount = 21;
        if (getCategoryId() == 2 || getCategoryId() == 5) {
            paramCount = 22;
        }

        message.appendInt(paramCount);
        message.appendInt(this.sourceType);
        message.appendInt(this.visibility);
        message.appendInt(this.showMode);
        message.appendInt(this.changeFilter);
        message.appendInt(this.showOnMouseHover ? 1 : 0);
        message.appendInt(this.showDuration);
        message.appendInt(this.styleId);
        message.appendInt(this.colorId);
        message.appendInt(this.widthId);
        message.appendInt(this.rendererId);
        // defaultMinValue as 64-bit int
        message.appendInt(this.defaultMinValue < 0 ? -1 : 0);
        message.appendInt(this.defaultMinValue);
        // defaultMaxValue as 64-bit int
        message.appendInt(this.defaultMaxValue < 0 ? -1 : 0);
        message.appendInt(this.defaultMaxValue);
        message.appendInt(this.overrideMinEnabled ? 1 : 0);
        message.appendInt(this.overrideMaxEnabled ? 1 : 0);
        message.appendInt(this.overrideMinTarget);
        message.appendInt(this.overrideMaxTarget);
        // audienceVariableValue as 64-bit int
        message.appendInt(this.audienceVariableValue < 0 ? -1 : 0);
        message.appendInt(this.audienceVariableValue);
        message.appendInt(this.segments);
        if (getCategoryId() == 2) {
            message.appendInt(this.subRendererId);
        } else if (getCategoryId() == 5) {
            message.appendInt(this.iconAlignment);
        }

        message.appendInt(0);
        message.appendInt(getCode());
        message.appendInt(0);

        int minVarId = parseVarId(this.overrideMinVariableId);
        int maxVarId = parseVarId(this.overrideMaxVariableId);
        int audVarId = parseVarId(this.audienceVariableId);

        message.appendInt(3);
        message.appendInt(minVarId);
        message.appendInt(maxVarId);
        message.appendInt(audVarId);
    }

    private static int parseVarId(String varId) {
        if (varId == null || varId.isEmpty()) return 0;
        try {
            return Integer.parseInt(varId);
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    public void applyJsonData(String rawJson) {
        if (rawJson == null || rawJson.trim().isEmpty() || !rawJson.trim().startsWith("{")) {
            return;
        }

        try {
            JsonData data = WiredExtraPayloadGuard.fromJson(rawJson.trim(), JsonData.class);
            if (data != null) {
                this.sourceType = data.sourceType;
                this.visibility = data.visibility;
                this.showMode = data.showMode;
                this.changeFilter = data.changeFilter;
                this.showOnMouseHover = data.showOnMouseHover;
                this.showDuration = data.showDuration > 0 ? data.showDuration : 2000;
                this.styleId = data.styleId;
                this.colorId = data.colorId;
                this.widthId = data.widthId;
                this.rendererId = data.rendererId;
                this.defaultMinValue = data.defaultMinValue;
                this.defaultMaxValue = data.defaultMaxValue;
                this.overrideMinEnabled = data.overrideMinEnabled;
                this.overrideMaxEnabled = data.overrideMaxEnabled;
                this.overrideMinTarget = data.overrideMinTarget;
                this.overrideMaxTarget = data.overrideMaxTarget;
                this.audienceVariableValue = data.audienceVariableValue;
                this.segments = data.segments;
                this.subRendererId = data.subRendererId;
                this.iconAlignment = data.iconAlignment;
                this.icon = data.icon != null ? data.icon : "";
                this.variableName = data.variableName != null ? data.variableName : "";
                this.overrideMinVariableId = data.overrideMinVariableId != null ? data.overrideMinVariableId : "";
                this.overrideMaxVariableId = data.overrideMaxVariableId != null ? data.overrideMaxVariableId : "";
                this.audienceVariableId = data.audienceVariableId != null ? data.audienceVariableId : "";
                sanitizeConfiguration();
            }
        } catch (Exception ignored) {
            WiredCompatibilityDiagnostics.record(
                    WiredCompatibilityDiagnostics.FailurePoint.EXTRA_VARIABLE_FX_JSON, ignored);
        }
    }

    /** Mirrors the option matrix exposed by the Flash metadata. */
    private void sanitizeConfiguration() {
        this.sourceType = (this.sourceType == 0 || this.sourceType == 1) ? this.sourceType : 1;
        this.visibility = Math.max(0, Math.min(4, this.visibility));
        if (this.sourceType == 0 && this.visibility != 3 && this.visibility != 4) this.visibility = 2;
        this.showMode = Math.max(0, Math.min(2, this.showMode));
        this.changeFilter = Math.max(0, Math.min(15, this.changeFilter));
        this.showDuration = Math.max(1500, Math.min(20000, this.showDuration));
        this.iconAlignment = Math.max(0, Math.min(2, this.iconAlignment));

        switch (getCategoryId()) {
            case 0 -> {
                this.styleId = Math.max(0, Math.min(3, this.styleId));
                if (this.styleId == 0) {
                    this.rendererId = 10;
                    this.colorId = 1000;
                    this.widthId = Math.max(1, Math.min(3, this.widthId));
                } else if (this.styleId == 1) {
                    this.rendererId = 12;
                    if (this.widthId != 2 && this.widthId != 3) this.widthId = 2;
                } else if (this.styleId == 2) {
                    this.rendererId = 13;
                    this.widthId = Math.max(0, Math.min(4, this.widthId));
                } else {
                    this.rendererId = 11;
                    this.widthId = -1;
                }
                if (this.styleId == 0) this.colorId = 1000;
                else if (this.styleId == 2) this.colorId = -1;
                else if (!isAllOrTeamColor(this.colorId)) this.colorId = 5;
            }
            case 1 -> {
                this.styleId = Math.max(0, Math.min(3, this.styleId));
                this.rendererId = this.styleId == 0 ? 0 : this.styleId + 1;
                this.widthId = Math.max(0, Math.min(4, this.widthId));
                if (!isAllOrTeamColor(this.colorId)) this.colorId = 1;
            }
            case 2 -> {
                this.styleId = Math.max(0, Math.min(1, this.styleId));
                this.rendererId = this.styleId == 0 ? 20 : 21;
                this.widthId = this.styleId == 0
                        ? Math.max(1, Math.min(3, this.widthId))
                        : Math.max(1, Math.min(4, this.widthId));
                if (this.subRendererId < 2 || this.subRendererId > 4) this.subRendererId = 2;
                if (!(isNormalColor(this.colorId) || this.colorId == 1001 || this.colorId == 1002)) this.colorId = 1002;
            }
            case 3 -> {
                this.styleId = Math.max(0, Math.min(21, this.styleId));
                this.rendererId = Math.max(2, Math.min(4, this.rendererId));
                this.widthId = Math.max(0, Math.min(4, this.widthId));
                this.colorId = this.styleId == 16 ? 1000 : -1;
            }
            case 4 -> {
                this.styleId = Math.max(0, Math.min(1, this.styleId));
                this.rendererId = 100;
                if (this.styleId == 0) this.colorId = 5;
                else if (!(isAllOrTeamColor(this.colorId) || this.colorId == 1000)) this.colorId = 5;
                if (this.widthId != 3 && this.widthId != 4 && this.widthId != 100) this.widthId = 3;
            }
            case 5 -> {
                this.styleId = Math.max(0, Math.min(2, this.styleId));
                this.rendererId = this.styleId == 0 ? 201 : 200;
                this.widthId = -1;
                if (this.styleId == 0
                        && !(this.colorId == 1
                                || this.colorId == 3
                                || this.colorId == 5
                                || this.colorId == 7
                                || this.colorId == 19
                                || this.colorId == 1001)) this.colorId = 1;
                else if (this.styleId != 0 && !isAllOrTeamColor(this.colorId)) this.colorId = 1;
            }
            default -> {}
        }

        int segmentRenderer = (getCategoryId() == 2) ? this.subRendererId : this.rendererId;
        if (segmentRenderer != 2 && segmentRenderer != 4 && segmentRenderer != 13) this.segments = 0;
        else this.segments = Math.max(0, Math.min(100, this.segments));
        if (this.defaultMaxValue < this.defaultMinValue) {
            int swap = this.defaultMinValue;
            this.defaultMinValue = this.defaultMaxValue;
            this.defaultMaxValue = swap;
        }
    }

    private static boolean isNormalColor(int colorId) {
        return (colorId >= 1 && colorId <= 19) || (colorId >= 100 && colorId <= 104);
    }

    private static boolean isAllOrTeamColor(int colorId) {
        return isNormalColor(colorId) || colorId == 1001;
    }

    @Override
    public void loadWiredData(ResultSet set, Room room) throws SQLException {
        this.onPickUp();
        String wiredData = set.getString("wired_data");
        if (wiredData != null && !wiredData.isEmpty()) {
            this.applyJsonData(wiredData);
        }
    }

    @Override
    public void onPickUp() {
        this.sourceType = 0;
        this.visibility = 2;
        this.showMode = 0;
        this.changeFilter = 0;
        this.showOnMouseHover = false;
        this.showDuration = 2000;
        this.styleId = 0;
        this.colorId = 1;
        this.widthId = 0;
        this.rendererId = 0;
        this.defaultMinValue = 0;
        this.defaultMaxValue = 100;
        this.overrideMinEnabled = false;
        this.overrideMaxEnabled = false;
        this.overrideMinTarget = 0;
        this.overrideMaxTarget = 0;
        this.audienceVariableValue = 0;
        this.segments = 0;
        this.subRendererId = 0;
        this.iconAlignment = 0;
        this.icon = "";
        this.variableName = "";
        this.overrideMinVariableId = "";
        this.overrideMaxVariableId = "";
        this.audienceVariableId = "";
    }

    @Override
    public boolean hasConfiguration() {
        return true;
    }

    public static class JsonData {
        @SerializedName("src")
        public int sourceType;

        @SerializedName("vis")
        public int visibility;

        @SerializedName("mode")
        public int showMode;

        @SerializedName("flt")
        public int changeFilter;

        @SerializedName("hov")
        public boolean showOnMouseHover;

        @SerializedName("dur")
        public int showDuration;

        @SerializedName("stl")
        public int styleId;

        @SerializedName("clr")
        public int colorId;

        @SerializedName("wid")
        public int widthId;

        @SerializedName("rnd")
        public int rendererId;

        @SerializedName("min")
        public int defaultMinValue;

        @SerializedName("max")
        public int defaultMaxValue;

        @SerializedName("omin_e")
        public boolean overrideMinEnabled;

        @SerializedName("omax_e")
        public boolean overrideMaxEnabled;

        @SerializedName("omin_t")
        public int overrideMinTarget;

        @SerializedName("omax_t")
        public int overrideMaxTarget;

        @SerializedName("aud_val")
        public int audienceVariableValue;

        @SerializedName("seg")
        public int segments;

        @SerializedName("sub_rnd")
        public int subRendererId;

        @SerializedName("icn_alg")
        public int iconAlignment;

        @SerializedName("icn")
        public String icon;

        @SerializedName("var_name")
        public String variableName;

        @SerializedName("omin_v")
        public String overrideMinVariableId;

        @SerializedName("omax_v")
        public String overrideMaxVariableId;

        @SerializedName("aud_v")
        public String audienceVariableId;

        public JsonData() {}

        public JsonData(
                int sourceType,
                int visibility,
                int showMode,
                int changeFilter,
                boolean showOnMouseHover,
                int showDuration,
                int styleId,
                int colorId,
                int widthId,
                int rendererId,
                int defaultMinValue,
                int defaultMaxValue,
                boolean overrideMinEnabled,
                boolean overrideMaxEnabled,
                int overrideMinTarget,
                int overrideMaxTarget,
                int audienceVariableValue,
                int segments,
                int subRendererId,
                int iconAlignment,
                String icon,
                String variableName,
                String overrideMinVariableId,
                String overrideMaxVariableId,
                String audienceVariableId) {
            this.sourceType = sourceType;
            this.visibility = visibility;
            this.showMode = showMode;
            this.changeFilter = changeFilter;
            this.showOnMouseHover = showOnMouseHover;
            this.showDuration = showDuration;
            this.styleId = styleId;
            this.colorId = colorId;
            this.widthId = widthId;
            this.rendererId = rendererId;
            this.defaultMinValue = defaultMinValue;
            this.defaultMaxValue = defaultMaxValue;
            this.overrideMinEnabled = overrideMinEnabled;
            this.overrideMaxEnabled = overrideMaxEnabled;
            this.overrideMinTarget = overrideMinTarget;
            this.overrideMaxTarget = overrideMaxTarget;
            this.audienceVariableValue = audienceVariableValue;
            this.segments = segments;
            this.subRendererId = subRendererId;
            this.iconAlignment = iconAlignment;
            this.icon = icon;
            this.variableName = variableName;
            this.overrideMinVariableId = overrideMinVariableId;
            this.overrideMaxVariableId = overrideMaxVariableId;
            this.audienceVariableId = audienceVariableId;
        }
    }
}
