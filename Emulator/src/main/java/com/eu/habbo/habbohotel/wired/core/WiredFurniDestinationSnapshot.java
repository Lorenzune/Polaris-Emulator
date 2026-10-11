package com.eu.habbo.habbohotel.wired.core;

import com.eu.habbo.habbohotel.items.interactions.wired.effects.WiredEffectFurniToFurni;
import com.eu.habbo.habbohotel.users.HabboItem;
import com.eu.habbo.habbohotel.wired.api.IWiredEffect;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;

/** Sibling movements resolve destinations before any sibling moves them, including ordered swaps. */
public final class WiredFurniDestinationSnapshot implements AutoCloseable {
    private static final ThreadLocal<WiredFurniDestinationSnapshot> CURRENT = new ThreadLocal<>();

    public record Position(short x, short y, double z) {}

    private final WiredFurniDestinationSnapshot parent;
    private final WiredContext context;
    private final Map<HabboItem, Position> positions = new IdentityHashMap<>();

    private WiredFurniDestinationSnapshot(WiredContext context, List<IWiredEffect> effects, boolean unordered) {
        this.parent = CURRENT.get();
        this.context = context;
        if (unordered
                && context.room() != null
                && effects.stream()
                                .filter(effect -> effect instanceof WiredEffectFurniToFurni)
                                .count()
                        > 1) {
            for (var item : context.room().getFloorItems()) positions.put(item, current(item));
        }
        CURRENT.set(this);
    }

    public static WiredFurniDestinationSnapshot begin(
            WiredContext context, List<IWiredEffect> effects, boolean unordered) {
        return new WiredFurniDestinationSnapshot(context, effects, unordered);
    }

    public static Position position(WiredContext context, HabboItem item) {
        var scope = CURRENT.get();
        return scope != null && scope.context == context
                ? scope.positions.getOrDefault(item, current(item))
                : current(item);
    }

    private static Position current(HabboItem item) {
        return new Position(item.getX(), item.getY(), item.getZ());
    }

    @Override
    public void close() {
        if (parent == null) CURRENT.remove();
        else CURRENT.set(parent);
    }
}
