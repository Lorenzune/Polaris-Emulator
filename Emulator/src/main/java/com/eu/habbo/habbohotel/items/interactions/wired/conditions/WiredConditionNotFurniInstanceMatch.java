package com.eu.habbo.habbohotel.items.interactions.wired.conditions;

import com.eu.habbo.habbohotel.items.Item;
import com.eu.habbo.habbohotel.wired.WiredConditionType;
import com.eu.habbo.habbohotel.wired.core.WiredContext;
import java.sql.ResultSet;
import java.sql.SQLException;

public class WiredConditionNotFurniInstanceMatch extends WiredConditionFurniInstanceMatch {
    public WiredConditionNotFurniInstanceMatch(ResultSet set, Item baseItem) throws SQLException {
        super(set, baseItem);
    }

    public WiredConditionNotFurniInstanceMatch(int id, int userId, Item item, String data, int stack, int sells) {
        super(id, userId, item, data, stack, sells);
    }

    @Override
    public boolean evaluate(WiredContext ctx) {
        if (ctx == null) return false;
        return this.getQuantifier() == QUANTIFIER_ANY ? !this.evaluateAllMatches(ctx) : !this.evaluateAnyMatches(ctx);
    }

    @Override
    public WiredConditionType getType() {
        return WiredConditionType.NOT_STUFF_IS;
    }
}
