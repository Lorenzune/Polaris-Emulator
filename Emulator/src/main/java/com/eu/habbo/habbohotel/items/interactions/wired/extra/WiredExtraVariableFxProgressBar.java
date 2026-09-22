package com.eu.habbo.habbohotel.items.interactions.wired.extra;

import com.eu.habbo.habbohotel.items.Item;
import java.sql.ResultSet;
import java.sql.SQLException;

public class WiredExtraVariableFxProgressBar extends WiredExtraVariableFxBase {
    public static final int CODE = 1201;
    public static final int CATEGORY_ID = 1;

    public WiredExtraVariableFxProgressBar(ResultSet set, Item baseItem) throws SQLException {
        super(set, baseItem);
    }

    public WiredExtraVariableFxProgressBar(
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
