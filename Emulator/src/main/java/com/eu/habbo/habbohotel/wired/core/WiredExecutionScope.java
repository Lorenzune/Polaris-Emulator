package com.eu.habbo.habbohotel.wired.core;

import com.eu.habbo.habbohotel.wired.api.IWiredEffect;
import com.eu.habbo.habbohotel.rooms.RoomUnit;
import com.eu.habbo.habbohotel.users.HabboItem;
import java.util.LinkedHashSet;
import java.util.List;

/** Internal current-execution metadata without widening the plugin-facing API. */
final class WiredExecutionScope {
    private static final ThreadLocal<WiredContext> CURRENT = new ThreadLocal<>();

    private WiredExecutionScope() {}

    static void execute(IWiredEffect effect, WiredContext context) {
        WiredContext previous = CURRENT.get();
        // Independent selectors contribute to one selection; deferred selectors filter that union.
        boolean combine = effect.isSelector() && !effect.usesExistingSelectorTargets();
        var previousItems = combine && context.targets().isItemsModifiedBySelector()
                ? List.copyOf(context.targets().items()) : List.<HabboItem>of();
        var previousUsers = combine && context.targets().isUsersModifiedBySelector()
                ? List.copyOf(context.targets().users()) : List.<RoomUnit>of();
        CURRENT.set(context);
        try (var ignored = WiredVariableChangeOrigin.begin(WiredVariableChangeOrigin.THIS_ROOM)) {
            effect.execute(context);
            if (combine) {
                if (!previousItems.isEmpty()) {
                    var items = new LinkedHashSet<>(previousItems);
                    items.addAll(context.targets().items());
                    context.targets().setItems(items);
                }
                if (!previousUsers.isEmpty()) {
                    var users = new LinkedHashSet<>(previousUsers);
                    users.addAll(context.targets().users());
                    context.targets().setUsers(users);
                }
            }
        } finally {
            if (previous == null) {
                CURRENT.remove();
            } else {
                CURRENT.set(previous);
            }
        }
    }

    static void clearForCurrentThread() {
        CURRENT.remove();
    }
}
