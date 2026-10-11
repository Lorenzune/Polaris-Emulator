package com.eu.habbo.habbohotel.items.interactions.wired.conditions;

import com.eu.habbo.habbohotel.items.Item;
import com.eu.habbo.habbohotel.users.HabboItem;
import com.eu.habbo.habbohotel.wired.core.WiredContext;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.HashSet;
import java.util.Set;

/** Explicit instance comparison for presets that identify pieces using identical proxy furni. */
public class WiredConditionFurniInstanceMatch extends WiredConditionFurniTypeMatch {
    public WiredConditionFurniInstanceMatch(ResultSet set, Item baseItem) throws SQLException {
        super(set, baseItem);
    }

    public WiredConditionFurniInstanceMatch(int id, int userId, Item item, String data, int stack, int sells) {
        super(id, userId, item, data, stack, sells);
    }

    @Override
    protected Set<Integer> resolveCompareTypeIds(WiredContext ctx) {
        this.refresh();
        Set<Integer> ids = new HashSet<>();
        for (HabboItem item : this.resolveConfiguredItems(ctx, this.compareFurniSource)) {
            if (item != null) ids.add(item.getId());
        }
        return ids;
    }

    @Override
    protected boolean matchesType(HabboItem item, Set<Integer> ids) {
        return item != null && ids.contains(item.getId());
    }
}
