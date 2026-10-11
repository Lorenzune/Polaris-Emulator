package com.eu.habbo.habbohotel.items.interactions.wired.effects;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyShort;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.eu.habbo.habbohotel.items.Item;
import com.eu.habbo.habbohotel.items.interactions.InteractionMultiHeight;
import com.eu.habbo.habbohotel.rooms.FurnitureMovementError;
import com.eu.habbo.habbohotel.rooms.Room;
import com.eu.habbo.habbohotel.rooms.RoomLayout;
import com.eu.habbo.habbohotel.rooms.RoomTile;
import com.eu.habbo.habbohotel.rooms.RoomTileState;
import com.eu.habbo.habbohotel.users.HabboItem;
import com.eu.habbo.habbohotel.wired.core.WiredContext;
import com.eu.habbo.habbohotel.wired.core.WiredMoveCarryHelper;
import com.eu.habbo.habbohotel.wired.core.WiredMovementPhysics;
import com.eu.habbo.habbohotel.wired.core.WiredSimulation;
import com.eu.habbo.habbohotel.wired.core.WiredSourceUtil;
import java.sql.ResultSet;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;

class WiredFurniToFurniAltitudeTest {
    @Test
    void disabledKeepAltitudeStacksOnTopOfTheTargetInExecutionAndSimulation() throws Exception {
        var fixture = new Fixture();
        fixture.verifyAltitude(fixture.physics(false), 2.25);
    }

    @Test
    void keepAltitudeRetainsTheMoversHeightEvenAboveAWholeStack() throws Exception {
        var fixture = new Fixture();
        fixture.add(216, 17, 6, 3, 2);
        fixture.verifyAltitude(fixture.physics(true), 0.5);
    }

    @Test
    void withoutPhysicsAccessoryRetainsLegacyTargetBaseAltitude() throws Exception {
        new Fixture().verifyAltitude(WiredMovementPhysics.NONE, 1);
    }

    @Test
    void disabledKeepAltitudeUsesTheWholeStackRatherThanTheSelectedTarget() throws Exception {
        var fixture = new Fixture();
        fixture.add(216, 17, 6, 3, 2);
        fixture.verifyAltitude(fixture.physics(false), 5);
    }

    @Test
    void considersTheWholeRotatedMovingFurnitureFootprint() throws Exception {
        var fixture = new Fixture();
        // A 1x2 piece rotated east occupies the neighboring square too.
        when(fixture.mover.getBaseItem().getLength()).thenReturn(2);
        fixture.add(216, 18, 6, 7, 2);
        fixture.verifyAltitude(fixture.physics(false), 9);
    }

    @Test
    void ignoresTheMoverItselfWhenAlreadyOnTheDestinationStack() throws Exception {
        var fixture = new Fixture();
        when(fixture.mover.getX()).thenReturn((short) 17);
        when(fixture.mover.getY()).thenReturn((short) 6);
        when(fixture.mover.getZ()).thenReturn(9.0);
        fixture.add(216, 17, 6, 3, 2);
        fixture.verifyAltitude(fixture.physics(false), 5);
    }

    @Test
    void usesTheCurrentHeightOfMultiHeightFurnitureInTheStack() throws Exception {
        var fixture = new Fixture();
        var support = mock(InteractionMultiHeight.class);
        fixture.configure(support, 216, 17, 6, 3, 1);
        when(support.getBaseItem().getMultiHeights()).thenReturn(new double[] {1, 3});
        when(support.getExtradata()).thenReturn("1");
        fixture.verifyAltitude(fixture.physics(false), 6);
    }

    @Test
    void passThroughFurnitureDoesNotActAsStackSupport() throws Exception {
        var fixture = new Fixture();
        fixture.add(216, 17, 6, 3, 2);
        fixture.verifyAltitude(new WiredMovementPhysics(false, Set.of(216), Set.of(), Set.of()), 2.25);
    }

    @Test
    void simulationUsesSupportsMovedIntoAndOutOfTheStack() throws Exception {
        for (boolean moveIntoStack : new boolean[] {false, true}) {
            var fixture = new Fixture();
            var support = fixture.add(216, moveIntoStack ? 20 : 17, 6, 3, 2);
            try (var sources = mockStatic(WiredSourceUtil.class);
                    var helper = mockStatic(WiredMoveCarryHelper.class)) {
                fixture.configureSources(sources);
                helper.when(() -> WiredMoveCarryHelper.getFurniMovementPhysics(
                                fixture.room, fixture.effect, fixture.mover, fixture.ctx))
                        .thenReturn(fixture.physics(false));
                helper.when(() -> WiredMoveCarryHelper.getSimulationMovementError(
                                eq(fixture.room),
                                eq(fixture.effect),
                                eq(fixture.mover),
                                eq(fixture.tile(17, 6)),
                                eq(2),
                                eq(fixture.ctx),
                                eq(false)))
                        .thenReturn(FurnitureMovementError.NONE);
                var simulation = new WiredSimulation(fixture.room);
                short supportX = (short) (moveIntoStack ? 17 : 20);
                when(fixture.room.furnitureFitsAt(fixture.tile(supportX, 6), support, 0, false))
                        .thenReturn(FurnitureMovementError.NONE);
                assertTrue(simulation.moveItem(support, supportX, (short) 6, 3, 0));
                assertTrue(fixture.effect.simulate(fixture.ctx, simulation));
                assertEquals(moveIntoStack ? 5 : 2.25, simulation.getItemPosition(fixture.mover).z);
            }
        }
    }

    private static class Fixture {
        final Room room = mock(Room.class);
        final RoomLayout layout = mock(RoomLayout.class);
        final Map<String, RoomTile> tiles = new HashMap<>();
        final Set<HabboItem> items = new HashSet<>();
        final HabboItem mover;
        final HabboItem target;
        final WiredEffectFurniToFurni effect;
        final WiredContext ctx = mock(WiredContext.class);

        Fixture() throws Exception {
            when(room.getLayout()).thenReturn(layout);
            when(layout.getTile(anyShort(), anyShort()))
                    .thenAnswer(call -> tile(call.<Short>getArgument(0), call.<Short>getArgument(1)));
            when(layout.getTilesAt(org.mockito.ArgumentMatchers.any(RoomTile.class), anyInt(), anyInt(), anyInt()))
                    .thenAnswer(call -> footprint(
                            call.getArgument(0), call.getArgument(1), call.getArgument(2), call.getArgument(3)));
            when(room.getFloorItems()).thenReturn(items);
            when(room.getItemsAt(org.mockito.ArgumentMatchers.any(RoomTile.class)))
                    .thenAnswer(call -> {
                        RoomTile requested = call.getArgument(0);
                        Set<HabboItem> found = new HashSet<>();
                        for (HabboItem item : items) {
                            var base = item.getBaseItem();
                            if (footprint(
                                            tile(item.getX(), item.getY()),
                                            base.getWidth(),
                                            base.getLength(),
                                            item.getRotation())
                                    .contains(requested)) found.add(item);
                        }
                        return found;
                    });
            mover = add(68, 8, 4, 0.5, 1);
            when(mover.getRotation()).thenReturn(2);
            target = add(215, 17, 6, 1, 1.25);
            effect = new WiredEffectFurniToFurni(277, 16, mock(Item.class), "", 0, 0);
            ResultSet row = mock(ResultSet.class);
            when(row.getString("wired_data"))
                    .thenReturn(
                            "{\"moveSource\":200,\"targetSource\":201,\"itemIds\":[],\"targetItemIds\":[],\"delay\":0}");
            effect.loadWiredData(row, room);
            when(ctx.room()).thenReturn(room);
        }

        RoomTile tile(int x, int y) {
            return tiles.computeIfAbsent(
                    x + ":" + y, ignored -> new RoomTile((short) x, (short) y, (short) 0, RoomTileState.OPEN, true));
        }

        Set<RoomTile> footprint(RoomTile tile, int width, int length, int rotation) {
            int w = rotation == 2 || rotation == 6 ? length : width;
            int l = rotation == 2 || rotation == 6 ? width : length;
            Set<RoomTile> result = new HashSet<>();
            for (int x = tile.x; x < tile.x + w; x++) for (int y = tile.y; y < tile.y + l; y++) result.add(tile(x, y));
            return result;
        }

        HabboItem add(int id, int x, int y, double z, double height) {
            return configure(mock(HabboItem.class), id, x, y, z, height);
        }

        HabboItem configure(HabboItem item, int id, int x, int y, double z, double height) {
            var base = mock(Item.class);
            when(item.getId()).thenReturn(id);
            when(item.getX()).thenReturn((short) x);
            when(item.getY()).thenReturn((short) y);
            when(item.getZ()).thenReturn(z);
            when(item.getBaseItem()).thenReturn(base);
            when(base.getWidth()).thenReturn(1);
            when(base.getLength()).thenReturn(1);
            when(base.getHeight()).thenReturn(height);
            when(room.getHabboItem(id)).thenReturn(item);
            items.add(item);
            return item;
        }

        WiredMovementPhysics physics(boolean keepAltitude) {
            return new WiredMovementPhysics(keepAltitude, Set.of(), Set.of(), Set.of());
        }

        void configureSources(org.mockito.MockedStatic<WiredSourceUtil> sources) {
            sources.when(() -> WiredSourceUtil.resolveItems(eq(ctx), eq(200), anyCollection()))
                    .thenReturn(List.of(mover));
            sources.when(() -> WiredSourceUtil.resolveItems(eq(ctx), eq(201), anyCollection()))
                    .thenReturn(List.of(target));
        }

        void verifyAltitude(WiredMovementPhysics physics, double expected) {
            List<Object[]> moves = new ArrayList<>();
            try (var sources = mockStatic(WiredSourceUtil.class);
                    var helper = mockStatic(WiredMoveCarryHelper.class, invocation -> {
                        if (invocation.getMethod().getName().equals("getFurniMovementPhysics")) return physics;
                        if (invocation.getMethod().getName().equals("moveFurni")) moves.add(invocation.getArguments());
                        return FurnitureMovementError.NONE;
                    })) {
                configureSources(sources);
                effect.execute(ctx);
                assertEquals(1, moves.size());
                assertEquals(tile(17, 6), moves.getFirst()[3]);
                assertEquals(expected, moves.getFirst()[5]);
                var simulation = new WiredSimulation(room);
                assertTrue(effect.simulate(ctx, simulation));
                assertEquals(17, simulation.getItemPosition(mover).x);
                assertEquals(6, simulation.getItemPosition(mover).y);
                assertEquals(expected, simulation.getItemPosition(mover).z);
                assertEquals(2, simulation.getItemPosition(mover).rotation);
                verify(mover, never()).setX(anyShort());
            }
        }
    }
}
