package com.eu.habbo.habbohotel.items.interactions.wired.extra;

import com.eu.habbo.habbohotel.items.Item;
import java.sql.ResultSet;
import java.sql.SQLException;

public class WiredExtraVariableFxNumberDisplay extends WiredExtraVariableFxBase {
    public static final int CODE = 1205;
    public static final int CATEGORY_ID = 5;

    public WiredExtraVariableFxNumberDisplay(ResultSet set, Item baseItem) throws SQLException {
        super(set, baseItem);
    }

    public WiredExtraVariableFxNumberDisplay(
            int id, int userId, Item item, String extradata, int limitedStack, int limitedSells) {
        super(id, userId, item, extradata, limitedStack, limitedSells);
    }

    @Override
    public int getCode() {
        return CODE;
    }

    @Override
    public int getCategoryId() {
        return CATEGORY_ID;
    }
}
