package com.eu.habbo.habbohotel.items.interactions.wired.extra;

import com.eu.habbo.habbohotel.gameclients.GameClient;
import com.eu.habbo.habbohotel.items.Item;
import com.eu.habbo.habbohotel.items.interactions.wired.WiredSettings;
import com.eu.habbo.habbohotel.rooms.Room;
import com.eu.habbo.habbohotel.wired.core.WiredManager;
import com.eu.habbo.messages.incoming.wired.WiredSaveException;
import java.sql.ResultSet;
import java.sql.SQLException;

public class WiredExtraFilterUser extends WiredExtraVariableFilterBase {
    public static final int CODE = 57;

    public WiredExtraFilterUser(ResultSet set, Item item) throws SQLException {
        super(set, item);
    }

    public WiredExtraFilterUser(int id, int userId, Item item, String data, int stack, int sells) {
        super(id, userId, item, data, stack, sells);
        amountConstantValue = 0;
    }

    @Override
    protected int getVariableTargetType() {
        return TARGET_USER;
    }

    @Override
    protected int getCode() {
        return CODE;
    }

    @Override
    protected boolean isCountOnly() {
        return true;
    }

    public int getAmount() {
        return amountConstantValue;
    }

    @Override
    public void onPickUp() {
        super.onPickUp();
        amountConstantValue = 0;
    }

    @Override
    public boolean saveData(WiredSettings settings, GameClient client) throws WiredSaveException {
        if (!settings.isNativeVariableFormat() && settings.getIntParams().length <= 1) {
            int amount = settings.getIntParams().length == 0 ? 0 : settings.getIntParams()[0];
            if (amount == 0
                    && settings.getStringParam() != null
                    && !settings.getStringParam().isEmpty()) {
                try {
                    amount = Integer.parseInt(settings.getStringParam());
                } catch (NumberFormatException ignored) {
                    com.eu.habbo.WiredCompatibilityDiagnostics.record(
                            com.eu.habbo.WiredCompatibilityDiagnostics.FailurePoint.EXTRA_FILTER_USER_AMOUNT, ignored);
                }
            }
            onPickUp();
            amountConstantValue = Math.max(0, Math.min(10000, amount));
            return true;
        }
        return super.saveData(settings, client);
    }

    @Override
    public String getWiredData() {
        var json = com.google.gson.JsonParser.parseString(super.getWiredData()).getAsJsonObject();
        json.addProperty("amount", amountConstantValue);
        return WiredManager.getGson().toJson(json);
    }

    @Override
    public void loadWiredData(ResultSet set, Room room) throws SQLException {
        String data = set.getString("wired_data");
        if (data != null && !data.isEmpty() && !data.startsWith("{")) {
            onPickUp();
            try {
                amountConstantValue = Math.max(0, Math.min(10000, Integer.parseInt(data)));
            } catch (NumberFormatException ignored) {
                com.eu.habbo.WiredCompatibilityDiagnostics.record(
                        com.eu.habbo.WiredCompatibilityDiagnostics.FailurePoint.EXTRA_FILTER_USER_AMOUNT, ignored);
            }
            return;
        }
        super.loadWiredData(set, room);
        if (data != null && data.startsWith("{")) {
            var json = WiredExtraPayloadGuard.fromJson(data, LegacyAmount.class);
            if (json != null && json.amountConstantValue == null)
                amountConstantValue = Math.max(0, Math.min(10000, json.amount));
        }
    }

    private static class LegacyAmount {
        int amount;
        Integer amountConstantValue;
    }
}
