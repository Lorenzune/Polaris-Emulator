package com.eu.habbo.habbohotel.items.interactions.games;

import com.eu.habbo.habbohotel.rooms.Room;

/** Changes the remaining countdown without starting, stopping or resetting its game. */
public final class WiredGameTimerAdjustment {
    private WiredGameTimerAdjustment() {}

    public static void adjust(InteractionGameTimer timer, Room room, int operator, int minutes, int halfSecondSteps) {
        int amount = Math.max(0, minutes) * 60000 + Math.max(0, halfSecondSteps) * 500;
        int remaining = timer.getClockTimeInMs();
        int next = Math.max(
                0,
                switch (operator) {
                    case 0 -> remaining + amount;
                    case 1 -> remaining - amount;
                    default -> amount;
                });
        // A positive temporary display makes the half-second phase observable even at zero.
        timer.setTimeNow(1);
        boolean currentHalf = timer.getClockTimeInMs() == 500;
        boolean desiredHalf = next % 1000 == 500;
        if (currentHalf != desiredHalf) timer.advanceClockHalfSecond();
        timer.setTimeNow((next + 999) / 1000);
        timer.setExtradata(timer.getTimeNow() + "\t" + timer.getBaseTime());
        timer.needsUpdate(true);
        room.updateItem(timer);
    }
}
