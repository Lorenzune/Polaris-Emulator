package com.eu.habbo.habbohotel.wired.core;

import com.eu.habbo.habbohotel.items.interactions.InteractionWiredExtra;
import com.eu.habbo.habbohotel.items.interactions.wired.extra.WiredExtraExecutionLimit;
import com.eu.habbo.habbohotel.rooms.Room;
import com.eu.habbo.habbohotel.users.HabboItem;
import java.util.Collection;
import java.util.List;

/** All execution-limit accessories on a tile contribute their own slots and time windows. */
record WiredExecutionLimitSupport(List<WiredExtraExecutionLimit> limits) {
    private static final WiredExecutionLimitSupport UNLIMITED = new WiredExecutionLimitSupport(List.of());

    static WiredExecutionLimitSupport forStack(Room room, HabboItem triggerItem) {
        if (room == null || triggerItem == null || room.getRoomSpecialTypes() == null) {
            return UNLIMITED;
        }
        Collection<InteractionWiredExtra> extras =
                room.getRoomSpecialTypes().getExtras(triggerItem.getX(), triggerItem.getY());
        if (extras == null || extras.isEmpty()) {
            return UNLIMITED;
        }
        return new WiredExecutionLimitSupport(extras.stream()
                .filter(WiredExtraExecutionLimit.class::isInstance)
                .map(WiredExtraExecutionLimit.class::cast)
                .toList());
    }

    boolean canExecuteAt(long timestamp) {
        if (this.limits.isEmpty()) return true;
        for (WiredExtraExecutionLimit limit : this.limits) {
            if (limit.canExecuteAt(timestamp)) return true;
        }
        return false;
    }

    boolean tryAcquireExecutionSlot(long timestamp) {
        if (this.limits.isEmpty()) return true;
        // Consume one accessory's slot, rather than charging every accessory for the same execution.
        for (WiredExtraExecutionLimit limit : this.limits) {
            if (limit.tryAcquireExecutionSlot(timestamp)) return true;
        }
        return false;
    }

    long maxExecutions() {
        return this.limits.stream().mapToLong(WiredExtraExecutionLimit::getMaxExecutions).sum();
    }
}
