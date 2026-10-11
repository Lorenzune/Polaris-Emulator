package com.eu.habbo.habbohotel.items.interactions.wired.effects;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.mockito.Mockito.CALLS_REAL_METHODS;
import static org.mockito.Mockito.anyCollection;
import static org.mockito.Mockito.anyShort;
import static org.mockito.Mockito.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

import com.eu.habbo.habbohotel.items.Item;
import com.eu.habbo.habbohotel.rooms.FurnitureMovementError;
import com.eu.habbo.habbohotel.rooms.Room;
import com.eu.habbo.habbohotel.rooms.RoomLayout;
import com.eu.habbo.habbohotel.rooms.RoomTile;
import com.eu.habbo.habbohotel.rooms.RoomTileState;
import com.eu.habbo.habbohotel.users.HabboItem;
import com.eu.habbo.habbohotel.wired.core.WiredContext;
import com.eu.habbo.habbohotel.wired.core.WiredEngine;
import com.eu.habbo.habbohotel.wired.core.WiredFurniDestinationSnapshot;
import com.eu.habbo.habbohotel.wired.core.WiredMoveCarryHelper;
import com.eu.habbo.habbohotel.wired.core.WiredMovementPhysics;
import com.eu.habbo.habbohotel.wired.core.WiredSourceUtil;
import com.eu.habbo.habbohotel.wired.core.WiredState;
import java.sql.ResultSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

class WiredFurniSwapTest {
    @Test
    void siblingEffectsSwapBothPiecesAndASecondSwapReturnsThemWithoutOverlap() throws Exception {
        for (boolean orderedBatch : new boolean[] {false, true}) {
            for (boolean reverse : new boolean[] {false, true}) {
                var room = mock(Room.class);
                var layout = mock(RoomLayout.class);
                when(room.getLayout()).thenReturn(layout);
                when(layout.getTile(anyShort(), anyShort()))
                        .thenAnswer(call -> new RoomTile(
                                call.getArgument(0), call.getArgument(1), (short) 0, RoomTileState.OPEN, true));
                var firstX = new AtomicInteger(10);
                var secondX = new AtomicInteger(11);
                var first = piece(1, firstX);
                var second = piece(2, secondX);
                when(room.getHabboItem(1)).thenReturn(first);
                when(room.getHabboItem(2)).thenReturn(second);
                when(room.getFloorItems()).thenReturn(Set.of(first, second));
                var ctx = mock(WiredContext.class);
                when(ctx.room()).thenReturn(room);
                when(ctx.actor()).thenReturn(java.util.Optional.empty());
                when(ctx.state()).thenReturn(mock(WiredState.class));
                var forward = effect(1, 0, 201, room);
                var backward = effect(2, 201, 0, room);
                var effects = reverse ? List.of(backward, forward) : List.of(forward, backward);
                try (var sources = mockStatic(WiredSourceUtil.class);
                        var helper = mockStatic(WiredMoveCarryHelper.class, call -> {
                            if (call.getMethod().getName().equals("getFurniMovementPhysics"))
                                return new WiredMovementPhysics(true, Set.of(), Set.of(), Set.of());
                            if (call.getMethod().getName().equals("moveFurni")) {
                                HabboItem mover = call.getArgument(2);
                                RoomTile tile = call.getArgument(3);
                                (mover == first ? firstX : secondX).set(tile.x);
                                return FurnitureMovementError.NONE;
                            }
                            return null;
                        })) {
                    sources.when(() -> WiredSourceUtil.resolveItems(eq(ctx), eq(0), anyCollection()))
                            .thenReturn(List.of(first));
                    sources.when(() -> WiredSourceUtil.resolveItems(eq(ctx), eq(201), anyCollection()))
                            .thenReturn(List.of(second));
                    for (int attempt = 0; attempt < 2; attempt++) {
                        if (orderedBatch) {
                            // The preset's cancellation receiver has wf_xtra_exec_in_order.
                            // Exercise the actual engine path, not just the snapshot helper.
                            var engine = mock(WiredEngine.class, CALLS_REAL_METHODS);
                            var execute = WiredEngine.class.getDeclaredMethod(
                                    "executeOrderedEffectBatch",
                                    List.class,
                                    WiredContext.class,
                                    long.class,
                                    boolean.class);
                            execute.setAccessible(true);
                            execute.invoke(engine, List.copyOf(effects), ctx, 1000L, false);
                        } else {
                            try (var scope = WiredFurniDestinationSnapshot.begin(ctx, List.copyOf(effects), true)) {
                                for (var effect : effects) effect.execute(ctx);
                            }
                        }
                        assertEquals(attempt == 0 ? 11 : 10, firstX.get());
                        assertEquals(attempt == 0 ? 10 : 11, secondX.get());
                        assertNotEquals(firstX.get(), secondX.get());
                    }
                    assertEquals(
                            firstX.get(),
                            WiredFurniDestinationSnapshot.position(ctx, first).x());
                }
            }
        }
    }

    private HabboItem piece(int id, AtomicInteger x) {
        var item = mock(HabboItem.class);
        when(item.getId()).thenReturn(id);
        when(item.getX()).thenAnswer(call -> (short) x.get());
        when(item.getY()).thenReturn((short) 10);
        when(item.getZ()).thenReturn(0.02);
        return item;
    }

    private WiredEffectFurniToFurni effect(int id, int source, int target, Room room) throws Exception {
        var effect = new WiredEffectFurniToFurni(id, 1, mock(Item.class), "", 0, 0);
        var row = mock(ResultSet.class);
        when(row.getString("wired_data"))
                .thenReturn("{\"moveSource\":" + source + ",\"targetSource\":" + target
                        + ",\"itemIds\":[],\"targetItemIds\":[]}");
        effect.loadWiredData(row, room);
        return effect;
    }
}
