package com.eu.habbo.habbohotel.items.interactions.wired.conditions;

import com.eu.habbo.habbohotel.items.Item;
import com.eu.habbo.habbohotel.wired.core.WiredContext;
import com.eu.habbo.habbohotel.wired.core.WiredSourceUtil;
import java.sql.ResultSet;
import java.sql.SQLException;

/** Opt-in instance comparison whose referenced selector sources belong to its own stack. */
public class WiredConditionFurniAnchoredInstanceMatch extends WiredConditionFurniInstanceMatch {
    public WiredConditionFurniAnchoredInstanceMatch(ResultSet set, Item baseItem) throws SQLException {
        super(set, baseItem);
    }

    public WiredConditionFurniAnchoredInstanceMatch(int id, int userId, Item item, String data, int stack, int sells) {
        super(id, userId, item, data, stack, sells);
    }

    @Override
    public boolean evaluate(WiredContext context) {
        var trigger = context.triggerItem();
        if (trigger == null || trigger.getX() == getX() && trigger.getY() == getY()) {
            return super.evaluate(context);
        }
        var own = new WiredContext(
                context.event(), this, null, context.services(), context.state(), context.legacySettings());
        own.targets().setItems(WiredSourceUtil.resolveSelectorItems(own, context.includeWiredSelectorItems()));
        return super.evaluate(own);
    }
}
