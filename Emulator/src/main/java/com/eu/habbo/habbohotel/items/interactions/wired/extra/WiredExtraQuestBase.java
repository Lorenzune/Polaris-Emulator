package com.eu.habbo.habbohotel.items.interactions.wired.extra;

import com.eu.habbo.habbohotel.gameclients.GameClient;
import com.eu.habbo.habbohotel.items.Item;
import com.eu.habbo.habbohotel.items.interactions.InteractionWiredExtra;
import com.eu.habbo.habbohotel.items.interactions.wired.WiredSettings;
import com.eu.habbo.habbohotel.rooms.Room;
import com.eu.habbo.habbohotel.rooms.RoomUnit;
import com.eu.habbo.habbohotel.wired.core.WiredDerivedVariableBox;
import com.eu.habbo.habbohotel.wired.core.WiredManager;
import com.eu.habbo.messages.ServerMessage;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

/**
 * Base for the gen-3 Wired 2.0 QUEST boxes ({@code wf_var_quest} / {@code wf_var_quest_chain}). A quest
 * box is a variable-definition add-on (config-holder, like the level-up box): placed on the SAME stack as
 * a base counter variable, it exposes DERIVED read-only sub-variables (progress / is_complete / …)
 * computed from that counter against a configured {@code targetValue}. Completion is detected for free via
 * the existing {@code VARIABLE_CHANGED} event on the backing counter.
 *
 * <p>Subclasses define the dialog {@link #code()} and the derived sub-variable set + formulas. All
 * Legacy counter configurations retain their target and derived values. Native configurations use
 * a variable name and quest code; they are independent user definitions backed by the hotel QuestManager. Standard
 * give/change/remove variable effects start, advance and reset their durable progress.</p>
 */
public abstract class WiredExtraQuestBase extends InteractionWiredExtra implements WiredDerivedVariableBox {
    protected int targetValue = 0;
    private String variableName = "";
    private String questCode = "";
    private boolean nativeConfiguration;

    public String getVariableName() {
        return variableName;
    }

    public String getQuestCode() {
        return questCode;
    }

    public boolean isNativeConfiguration() {
        return nativeConfiguration;
    }

    protected WiredExtraQuestBase(ResultSet set, Item baseItem) throws SQLException {
        super(set, baseItem);
    }

    protected WiredExtraQuestBase(int id, int userId, Item item, String extradata, int limitedStack, int limitedSells) {
        super(id, userId, item, extradata, limitedStack, limitedSells);
    }

    /** Client {@code WiredActionLayoutCode} for this box's dialog (108 quest / 109 quest-chain). */
    protected abstract int code();

    /** Number of derived sub-variables this box exposes. */
    public abstract int subvariableCount();

    public int getTargetValue() {
        return this.targetValue;
    }

    @Override
    public List<Integer> getSelectedSubvariables() {
        List<Integer> result = new ArrayList<>();
        if (nativeConfiguration) return result;
        for (int i = 0; i < this.subvariableCount(); i++) result.add(i);
        return result;
    }

    @Override
    public boolean hasSubvariable(int subType) {
        return !nativeConfiguration && subType >= 0 && subType < this.subvariableCount();
    }

    @Override
    public boolean execute(RoomUnit roomUnit, Room room, Object[] stuff) {
        return false;
    }

    @Override
    public void onWalk(RoomUnit roomUnit, Room room, Object[] objects) {}

    @Override
    public boolean hasConfiguration() {
        return true;
    }

    @Override
    public boolean saveData(WiredSettings settings, GameClient gameClient)
            throws com.eu.habbo.messages.incoming.wired.WiredSaveException {
        String text = settings.getStringParam();
        if (text != null && text.contains("\t")) {
            String[] fields = text.split("\t", -1);
            if (fields.length != 2) return false;
            String name = WiredVariableNameValidator.normalizeForSave(fields[0]).toLowerCase(java.util.Locale.ROOT);
            String code = fields[1].trim();
            if (code.isEmpty() || code.length() > 500 || code.indexOf('\r') >= 0 || code.indexOf('\n') >= 0)
                return false;
            Room room = gameClient != null && gameClient.getHabbo() != null
                    ? gameClient.getHabbo().getHabboInfo().getCurrentRoom()
                    : null;
            WiredVariableNameValidator.validateDefinitionName(room, getId(), name);
            variableName = name;
            questCode = code;
            nativeConfiguration = true;
            targetValue = 0;
            if (room != null) room.getUserVariableManager().requestBroadcast();
            return true;
        }
        if (text != null && !text.isEmpty()) return false;
        nativeConfiguration = false;
        variableName = "";
        questCode = "";
        int[] params = settings.getIntParams();
        this.targetValue = (params.length > 0) ? Math.max(0, params[0]) : 0;
        return true;
    }

    @Override
    public String getWiredData() {
        return WiredManager.getGson()
                .toJson(new JsonData(this.targetValue, variableName, questCode, nativeConfiguration));
    }

    @Override
    public void loadWiredData(ResultSet set, Room room) throws SQLException {
        this.onPickUp();

        String wiredData = set.getString("wired_data");
        if (wiredData == null || !wiredData.startsWith("{")) return;

        JsonData data = WiredManager.getGson().fromJson(wiredData, JsonData.class);
        if (data != null) {
            this.targetValue = Math.max(0, data.targetValue);
            variableName = data.variableName == null ? "" : data.variableName;
            questCode = data.questCode == null ? "" : data.questCode;
            nativeConfiguration = data.nativeConfiguration;
        }
    }

    @Override
    public void serializeWiredData(ServerMessage message, Room room) {
        message.appendBoolean(false);
        message.appendInt(0);
        message.appendInt(0);
        message.appendInt(this.getBaseItem().getSpriteId());
        message.appendInt(this.getId());
        message.appendString(nativeConfiguration ? variableName + "\t" + questCode : "");
        message.appendInt(nativeConfiguration ? 0 : 1);
        if (!nativeConfiguration) message.appendInt(this.targetValue);
        message.appendInt(0);
        message.appendInt(this.code());
        message.appendInt(0);
        message.appendInt(0);
    }

    @Override
    public void onPickUp() {
        this.targetValue = 0;
        variableName = "";
        questCode = "";
        nativeConfiguration = false;
    }

    static class JsonData {
        int targetValue;
        String variableName, questCode;
        boolean nativeConfiguration;

        JsonData() {}

        JsonData(int targetValue, String variableName, String questCode, boolean nativeConfiguration) {
            this.variableName = variableName;
            this.questCode = questCode;
            this.nativeConfiguration = nativeConfiguration;
            this.targetValue = targetValue;
        }
    }
}
