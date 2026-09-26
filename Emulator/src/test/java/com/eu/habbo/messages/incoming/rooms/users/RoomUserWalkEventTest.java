package com.eu.habbo.messages.incoming.rooms.users;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.eu.habbo.habbohotel.items.interactions.InteractionRoller;
import com.eu.habbo.habbohotel.rooms.RoomTile;
import com.eu.habbo.habbohotel.rooms.RoomTileState;
import com.eu.habbo.habbohotel.rooms.RoomUnit;
import org.junit.jupiter.api.Test;

class RoomUserWalkEventTest {

    private static final long SLIDE_STARTED_AT = 1_000L;
    private static final long NOW_DURING_SLIDE = 1_200L;

    @Test
    void clickOnSlideOriginDoesNotInterruptTheSlide() {
        RoomUnit unit = slidingUnit();
        RoomTile origin = unit.getRollerSlideOrigin();

        assertTrue(RoomUserWalkEvent.shouldIgnoreRollerSlideClick(unit, origin.x, origin.y, NOW_DURING_SLIDE));
    }

    @Test
    void clickOnSlideDestinationDoesNotInterruptTheSlide() {
        RoomUnit unit = slidingUnit();

        assertTrue(RoomUserWalkEvent.shouldIgnoreRollerSlideClick(unit, unit.getX(), unit.getY(), NOW_DURING_SLIDE));
    }

    @Test
    void clickOnAnotherCoordinateCanInterruptTheSlide() {
        RoomUnit unit = slidingUnit();

        assertFalse(RoomUserWalkEvent.shouldIgnoreRollerSlideClick(unit, 4, 6, NOW_DURING_SLIDE));
    }

    @Test
    void clickAfterSlideWindowDoesNotUseTheSlideGuard() {
        RoomUnit unit = slidingUnit();

        assertFalse(RoomUserWalkEvent.shouldIgnoreRollerSlideClick(
                unit, 5, 5, SLIDE_STARTED_AT + InteractionRoller.DELAY));
    }

    private static RoomUnit slidingUnit() {
        RoomTile origin = new RoomTile((short) 5, (short) 5, (short) 0, RoomTileState.OPEN, true);
        RoomTile destination = new RoomTile((short) 5, (short) 6, (short) 0, RoomTileState.OPEN, true);

        RoomUnit unit = new RoomUnit();
        unit.setId(1);
        unit.setLocation(destination);
        unit.setLastRollerTime(SLIDE_STARTED_AT);
        unit.setRollerSlideOrigin(origin);
        return unit;
    }
}
