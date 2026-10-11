package com.eu.habbo.habbohotel.items.interactions.wired.effects;

import com.eu.habbo.habbohotel.users.HabboItem;
import com.eu.habbo.habbohotel.wired.core.WiredContext;
import com.eu.habbo.habbohotel.wired.core.WiredSourceUtil;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

final class WiredMovementTargets {
    private WiredMovementTargets() {}

    static List<HabboItem> items(WiredContext ctx, int source, Collection<HabboItem> selected) {
        if (ctx == null || ctx.room() == null) return new ArrayList<>();
        var room = ctx.room();
        var result = new ArrayList<>(WiredSourceUtil.resolveItems(ctx, source, selected));
        result.removeIf(item -> item == null || room.getHabboItem(item.getId()) != item);
        return result;
    }
}
