package com.eu.habbo.habbohotel.wired.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.*;

import com.eu.habbo.habbohotel.items.interactions.wired.WiredSettings;
import com.eu.habbo.habbohotel.items.interactions.wired.extra.WiredExtraAnimationTime;
import com.eu.habbo.habbohotel.items.interactions.wired.triggers.WiredTriggerRepeaterShort;
import com.eu.habbo.habbohotel.rooms.FurnitureMovementError;
import com.eu.habbo.habbohotel.rooms.Room;
import com.eu.habbo.habbohotel.rooms.RoomTile;
import com.eu.habbo.habbohotel.rooms.RoomTileState;
import com.eu.habbo.habbohotel.users.HabboItem;
import com.eu.habbo.messages.ServerMessage;
import com.eu.habbo.messages.outgoing.rooms.WiredMovementsComposer;
import io.netty.buffer.ByteBuf;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;

class WiredShortMovementTimingTest {
    @ParameterizedTest
    @ValueSource(ints = {1, 2, 3, 5, 10})
    void savedShortIntervalReachesBatchedMovementPacket(int units) {
        WiredTriggerRepeaterShort timer = mock(WiredTriggerRepeaterShort.class, CALLS_REAL_METHODS);
        WiredSettings settings = mock(WiredSettings.class);
        when(settings.getIntParams()).thenReturn(new int[] {units});
        assertTrue(timer.saveData(settings));
        assertEquals(units * 50, timer.getRepeatTimeMs());
        Fixture fixture = fixture(WiredEvent.Type.TIMER_REPEAT_SHORT, timer);

        WiredMoveCarryHelper.beginMovementCollection();
        try {
            assertEquals(FurnitureMovementError.NONE, move(fixture));
            assertDuration(WiredMoveCarryHelper.finishMovementCollection(), units * 50);
            verify(fixture.room.getWiredRuntime()).markFurnitureMoving(fixture.item, units * 50);
        } finally {
            WiredMoveCarryHelper.clearThreadLocalsForCurrentThread();
        }
    }

    @Test
    void shortMovementWithoutBatchAlsoCarriesItsDuration() {
        WiredTriggerRepeaterShort timer = mock(WiredTriggerRepeaterShort.class);
        when(timer.getRepeatTimeMs()).thenReturn(100);
        Fixture fixture = fixture(WiredEvent.Type.TIMER_REPEAT_SHORT, timer);
        assertEquals(FurnitureMovementError.NONE, move(fixture));
        ArgumentCaptor<ServerMessage> sent = ArgumentCaptor.forClass(ServerMessage.class);
        verify(fixture.room).sendComposer(sent.capture());
        assertDuration(sent.getValue(), 100);
    }

    @Test
    void explicitAnimationExtraTakesPriorityOverTheShortInterval() {
        WiredTriggerRepeaterShort timer = mock(WiredTriggerRepeaterShort.class);
        when(timer.getRepeatTimeMs()).thenReturn(100);
        Fixture fixture = fixture(WiredEvent.Type.TIMER_REPEAT_SHORT, timer);
        WiredExtraAnimationTime extra = mock(WiredExtraAnimationTime.class);
        when(extra.getDurationMs()).thenReturn(750);
        when(fixture.room.getRoomSpecialTypes().getExtras((short) 0, (short) 0))
                .thenReturn(Set.of(extra));
        when(fixture.room.furnitureFitsAtWithPhysics(
                fixture.target, fixture.item, 0, false, WiredMovementPhysics.NONE))
                .thenReturn(FurnitureMovementError.NONE);
        when(fixture.room.moveFurniToWithPhysics(
                fixture.item, fixture.target, 0, null, false, false, WiredMovementPhysics.NONE))
                .thenReturn(FurnitureMovementError.NONE);
        WiredMoveCarryHelper.beginMovementCollection();
        try {
            assertEquals(FurnitureMovementError.NONE, move(fixture));
            assertDuration(WiredMoveCarryHelper.finishMovementCollection(), 750);
        } finally {
            WiredMoveCarryHelper.clearThreadLocalsForCurrentThread();
        }
    }

    @Test
    void standardTimerKeepsItsDefaultAnimationDuration() {
        Fixture fixture = fixture(WiredEvent.Type.TIMER_REPEAT, mock(HabboItem.class));
        WiredMoveCarryHelper.beginMovementCollection();
        try {
            assertEquals(FurnitureMovementError.NONE, move(fixture));
            assertDuration(WiredMoveCarryHelper.finishMovementCollection(), 500);
        } finally {
            WiredMoveCarryHelper.clearThreadLocalsForCurrentThread();
        }
    }

    private static Fixture fixture(WiredEvent.Type type, HabboItem timer) {
        Room room = mock(Room.class, RETURNS_DEEP_STUBS);
        HabboItem item = mock(HabboItem.class);
        HabboItem effect = mock(HabboItem.class);
        WiredContext context = mock(WiredContext.class);
        when(context.eventType()).thenReturn(type);
        when(context.sourceItem()).thenReturn(Optional.of(timer));
        when(item.getId()).thenReturn(123);
        RoomTile origin = new RoomTile((short) 0, (short) 0, (short) 0, RoomTileState.OPEN, true);
        RoomTile target = new RoomTile((short) 1, (short) 0, (short) 0, RoomTileState.OPEN, true);
        when(room.getLayout().getTile((short) 0, (short) 0)).thenReturn(origin);
        when(room.moveFurniTo(item, target, 0, null, false, false)).thenReturn(FurnitureMovementError.NONE);
        return new Fixture(room, item, effect, target, context);
    }

    private static FurnitureMovementError move(Fixture fixture) {
        return WiredMoveCarryHelper.moveFurni(
                fixture.room, fixture.effect, fixture.item, fixture.target, 0, null, false, fixture.context);
    }

    private static void assertDuration(ServerMessage message, int expected) {
        ByteBuf buffer = message.get();
        try {
            ByteBuf payload = buffer.duplicate();
            payload.skipBytes(6);
            assertEquals(1, payload.readInt());
            assertEquals(WiredMovementsComposer.TYPE_FURNI_MOVE, payload.readInt());
            payload.skipBytes(16);
            payload.skipBytes(payload.readUnsignedShort());
            payload.skipBytes(payload.readUnsignedShort());
            assertEquals(123, payload.readInt());
            assertEquals(expected, payload.readInt());
        } finally {
            buffer.release();
        }
    }

    private record Fixture(Room room, HabboItem item, HabboItem effect, RoomTile target, WiredContext context) {}
}
