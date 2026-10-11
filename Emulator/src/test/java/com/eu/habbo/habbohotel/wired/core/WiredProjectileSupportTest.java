package com.eu.habbo.habbohotel.wired.core;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.RETURNS_DEEP_STUBS;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.anyInt;
import static org.mockito.Mockito.anyShort;
import static org.mockito.Mockito.eq;
import static org.mockito.Mockito.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.eu.habbo.habbohotel.items.Item;
import com.eu.habbo.habbohotel.items.interactions.wired.WiredSettings;
import com.eu.habbo.habbohotel.items.interactions.wired.extra.WiredExtraProjectile;
import com.eu.habbo.habbohotel.rooms.FurnitureMovementError;
import com.eu.habbo.habbohotel.rooms.Room;
import com.eu.habbo.habbohotel.rooms.RoomTile;
import com.eu.habbo.habbohotel.rooms.RoomTileState;
import com.eu.habbo.habbohotel.rooms.RoomUnit;
import com.eu.habbo.habbohotel.users.HabboItem;
import java.util.Set;
import org.junit.jupiter.api.Test;

class WiredProjectileSupportTest {
    private WiredExtraProjectile extra(int[] params, String tokens) {
        var e = new WiredExtraProjectile(42, 2, mock(Item.class), "", 0, 0);
        assertTrue(e.saveData(new WiredSettings(params, tokens, new int[0], 0), null));
        return e;
    }

    private int[] params() {
        int[] p = new int[23];
        p[4] = 500;
        return p;
    }

    private record Fixture(Room room, HabboItem stack, HabboItem item, RoomTile target, WiredContext ctx) {}

    private Fixture fixture(WiredExtraProjectile extra) {
        var room = mock(Room.class, RETURNS_DEEP_STUBS);
        var stack = mock(HabboItem.class);
        var item = mock(HabboItem.class);
        when(item.getId()).thenReturn(17);
        when(room.getHabboItem(17)).thenReturn(item);
        when(item.getX()).thenReturn((short) 0);
        when(item.getY()).thenReturn((short) 0);
        when(room.getRoomSpecialTypes().getExtras((short) 0, (short) 0)).thenReturn(Set.of(extra));
        when(room.getRoomSpecialTypes().getExtras()).thenReturn(Set.of(extra));
        when(room.getRoomSpecialTypes().getExtra(42)).thenReturn(extra);
        var target = new RoomTile((short) 4, (short) 0, (short) 0, RoomTileState.OPEN, true);
        var ctx = new WiredContext(
                WiredEvent.builder(WiredEvent.Type.CUSTOM, room).build(),
                null,
                mock(WiredServices.class),
                new WiredState(20));
        return new Fixture(room, stack, item, target, ctx);
    }

    @Test
    void directionalSystemsAndOffsetMatchTheirNativeSectors() {
        assertEquals(2, WiredProjectileSupport.direction(4, -1, 0, 0));
        assertEquals(1, WiredProjectileSupport.direction(4, -3, 1, 0));
        assertEquals(2, WiredProjectileSupport.direction(4, -1, 2, 0));
        assertEquals(1, WiredProjectileSupport.direction(4, -1, 3, 0));
        assertEquals(7, WiredProjectileSupport.direction(0, -2, 0, 7));
    }

    @Test
    void nativeOvershootRotationCurveAndDistanceScaledDurationReachThePlan() {
        int[] p = params();
        p[0] = 1;
        p[2] = 1;
        p[6] = 1;
        p[14] = 1;
        p[16] = 2;
        p[18] = -200;
        var f = fixture(extra(p, "[]"));
        var plan = WiredProjectileSupport.resolve(f.room, f.stack, f.item, f.target, 0D, 0, 500, f.ctx);
        assertEquals(2, plan.rotation());
        assertEquals(2000, plan.duration());
        assertEquals(1000, plan.overshoot());
        assertEquals(1.5, plan.ratio());
        assertEquals(-200, plan.curve());
    }

    @Test
    void fixedDistanceShortensTheNativeAnimationAndDoesNotMoveTheTarget() {
        int[] p = params();
        p[14] = 2;
        p[16] = 2;
        var f = fixture(extra(p, "[]"));
        var plan = WiredProjectileSupport.resolve(f.room, f.stack, f.item, f.target, 0D, 0, 500, f.ctx);
        assertEquals(-250, plan.overshoot());
        assertEquals(0.5, plan.ratio());
        assertEquals(4, f.target.x);
    }

    @Test
    void roomVariableResolvesAndMissingReferenceKeepsNormalDuration() {
        int[] p = params();
        p[2] = 1;
        p[3] = 1;
        p[5] = -10;
        p[6] = 1;
        var f = fixture(extra(p, "[\"custom:77\",\"\"]"));
        when(f.room.getRoomVariableManager().hasVariable(77)).thenReturn(true);
        when(f.room.getRoomVariableManager().getCurrentValue(77)).thenReturn(100);
        assertEquals(
                400,
                WiredProjectileSupport.resolve(f.room, f.stack, f.item, f.target, 0D, 0, 500, f.ctx)
                        .duration());
        when(f.room.getRoomVariableManager().hasVariable(77)).thenReturn(false);
        assertEquals(
                500,
                WiredProjectileSupport.resolve(f.room, f.stack, f.item, f.target, 0D, 0, 500, f.ctx)
                        .duration());
    }

    @Test
    void readOnlySelectedSubvariablesHaveFinalValuesAndPickupCancelsThem() {
        int[] p = params();
        p[11] = 127;
        var extra = extra(p, "[]");
        var f = fixture(extra);
        when(f.room.isLoaded()).thenReturn(true);
        var origin = new RoomTile((short) 0, (short) 0, (short) 0, RoomTileState.OPEN, true);
        when(f.room.getLayout().getTile(anyShort(), anyShort())).thenReturn(null);
        var plan = WiredProjectileSupport.resolve(f.room, f.stack, f.item, f.target, 0D, 0, 500, f.ctx);
        WiredProjectileSupport.begin(f.room, f.item, origin, 0, f.target, plan, null, 0, f.ctx, false);
        assertEquals(7, WiredProjectileSupport.definitions(f.room).size());
        int id = WiredProjectileSupport.variableId(42, 3);
        assertTrue(WiredProjectileSupport.definition(f.room, id).isReadOnly());
        assertEquals(400, WiredProjectileSupport.read(f.room, 17, id));
        assertEquals(0, WiredProjectileSupport.read(f.room, 17, WiredProjectileSupport.variableId(42, 6)));
        extra.onPickUp();
        assertNull(WiredProjectileSupport.read(f.room, 17, id));
    }

    @Test
    void settingsRoundTripKeepsBothReferenceTokensAndRejectsMalformedDataAtomically() throws Exception {
        int[] p = params();
        p[18] = -2000;
        p[11] = 255;
        var extra = extra(p, "[\"internal:@position_x\",\"custom:77\"]");
        var before = extra.getWiredData();
        assertFalse(extra.saveData(new WiredSettings(params(), "bad json", new int[0], 0), null));
        assertEquals(before, extra.getWiredData());
        var row = mock(java.sql.ResultSet.class);
        when(row.getString("wired_data")).thenReturn(before);
        var restored = extra(params(), "[]");
        restored.loadWiredData(row, null);
        assertArrayEquals(extra.getIntParams(), restored.getIntParams());
        assertArrayEquals(extra.getVariableIds(), restored.getVariableIds());
        assertEquals(-1000, restored.getIntParams()[18]);
        assertEquals(127, restored.getIntParams()[11]);
    }

    @Test
    void projectileOnlyExtraSendsNativeRotationDurationOvershootAndCurve() {
        int[] p = params();
        p[0] = 1;
        p[14] = 1;
        p[16] = 2;
        p[18] = 100;
        var f = fixture(extra(p, "[]"));
        when(f.room.getLayout().getTile((short) 0, (short) 0))
                .thenReturn(new RoomTile((short) 0, (short) 0, (short) 0, RoomTileState.OPEN, true));
        when(f.item.getRotation()).thenReturn(2);
        when(f.room.furnitureFitsAtWithPhysics(eq(f.target), eq(f.item), eq(2), eq(false), any()))
                .thenReturn(FurnitureMovementError.NONE);
        when(f.room.moveFurniToWithPhysics(eq(f.item), eq(f.target), eq(2), isNull(), eq(false), eq(false), any()))
                .thenReturn(FurnitureMovementError.NONE);
        WiredMoveCarryHelper.beginMovementCollection();
        try {
            assertEquals(
                    FurnitureMovementError.NONE,
                    WiredMoveCarryHelper.moveFurni(f.room, f.stack, f.item, f.target, 0, null, false, f.ctx));
            var message = WiredMoveCarryHelper.finishMovementCollection();
            var buffer = message.get();
            try {
                var payload = buffer.duplicate();
                payload.skipBytes(6);
                assertEquals(1, payload.readInt());
                assertEquals(1, payload.readInt());
                payload.skipBytes(16);
                payload.skipBytes(payload.readUnsignedShort());
                payload.skipBytes(payload.readUnsignedShort());
                assertEquals(17, payload.readInt());
                assertEquals(500, payload.readInt());
                assertEquals(2, payload.readInt());
                assertTrue(payload.readBoolean());
                assertEquals(250, payload.readInt());
                assertTrue(payload.readBoolean());
                assertEquals(100, payload.readInt());
            } finally {
                buffer.release();
            }
        } finally {
            WiredMoveCarryHelper.clearThreadLocalsForCurrentThread();
        }
    }

    @Test
    void fastFlightDetectsIntermediateCollisionAndExposesThePositionAtTheEvent() {
        int[] p = params();
        p[11] = 11;
        var e = extra(p, "[]");
        var f = fixture(e);
        when(f.room.isLoaded()).thenReturn(true);
        when(f.room.getLayout().getTile(anyShort(), anyShort()))
                .thenAnswer(a -> new RoomTile(a.getArgument(0), a.getArgument(1), (short) 0, RoomTileState.OPEN, true));
        var unit = mock(RoomUnit.class);
        when(unit.getId()).thenReturn(99);
        when(f.room.getRoomUnits(any(RoomTile.class)))
                .thenAnswer(a -> ((RoomTile) a.getArgument(0)).x == 1 ? Set.of(unit) : Set.of());
        when(f.room.getItemsAt(any(RoomTile.class))).thenReturn(Set.of());
        var plan = WiredProjectileSupport.resolve(f.room, f.stack, f.item, f.target, 0D, 0, 500, f.ctx);
        try (var manager = mockStatic(WiredManager.class)) {
            manager.when(() -> WiredManager.triggerBotCollision(eq(f.room), eq(unit), eq(f.item)))
                    .thenAnswer(a -> {
                        int position =
                                WiredProjectileSupport.read(f.room, 17, WiredProjectileSupport.variableId(42, 3));
                        assertTrue(position >= 50 && position <= 100);
                        return false;
                    });
            WiredProjectileSupport.begin(
                    f.room,
                    f.item,
                    new RoomTile((short) 0, (short) 0, (short) 0, RoomTileState.OPEN, true),
                    0,
                    f.target,
                    plan,
                    null,
                    500,
                    f.ctx,
                    true);
            manager.verify(() -> WiredManager.triggerBotCollision(f.room, unit, f.item), times(1));
            assertEquals(1, WiredProjectileSupport.read(f.room, 17, WiredProjectileSupport.variableId(42, 1)));
            assertEquals(400, WiredProjectileSupport.read(f.room, 17, WiredProjectileSupport.variableId(42, 3)));
        } finally {
            e.onPickUp();
        }
    }

    @Test
    void staleAnimationCallbackCannotResumeAfterPickup() {
        int[] p = params();
        p[11] = 8;
        var e = extra(p, "[]");
        var f = fixture(e);
        when(f.room.isLoaded()).thenReturn(true);
        var plan = WiredProjectileSupport.resolve(f.room, f.stack, f.item, f.target, 0D, 0, 500, f.ctx);
        WiredProjectileSupport.begin(
                f.room,
                f.item,
                new RoomTile((short) 0, (short) 0, (short) 0, RoomTileState.OPEN, true),
                0,
                f.target,
                plan,
                null,
                0,
                f.ctx,
                true);
        var callback = org.mockito.ArgumentCaptor.forClass(Runnable.class);
        verify(f.room.getWiredRuntime()).scheduleProjectileUpdate(callback.capture(), anyInt());
        e.onPickUp();
        callback.getValue().run();
        assertNull(WiredProjectileSupport.read(f.room, 17, WiredProjectileSupport.variableId(42, 3)));
        verify(f.room.getWiredRuntime(), times(1)).scheduleProjectileUpdate(any(), anyInt());
    }

    @Test
    void selectedProjectileSourceDoesNotApplyWithoutTheSelectedTarget() {
        int[] p = params();
        p[19] = 100;
        var f = fixture(extra(p, "[]"));
        assertNull(WiredProjectileSupport.resolve(f.room, f.stack, f.item, f.target, 0D, 0, 500, f.ctx));
    }
}
