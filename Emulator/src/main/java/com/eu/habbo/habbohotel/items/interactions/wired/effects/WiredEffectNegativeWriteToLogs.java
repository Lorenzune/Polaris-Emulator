package com.eu.habbo.habbohotel.items.interactions.wired.effects;

import com.eu.habbo.habbohotel.items.Item;
import com.eu.habbo.habbohotel.wired.WiredEffectType;
import java.sql.ResultSet;
import java.sql.SQLException;

/** Executes the SWF write-to-logs action on the failed-condition branch. */
public class WiredEffectNegativeWriteToLogs extends WiredEffectWriteToLogs {
    public static final WiredEffectType type = WiredEffectType.NEG_WRITE_TO_LOGS;

    public WiredEffectNegativeWriteToLogs(ResultSet set, Item baseItem) throws SQLException {
        super(set, baseItem);
    }

    public WiredEffectNegativeWriteToLogs(
            int id, int userId, Item item, String extradata, int limitedStack, int limitedSells) {
        super(id, userId, item, extradata, limitedStack, limitedSells);
    }

    @Override
    public WiredEffectType getType() {
        return type;
    }
}
