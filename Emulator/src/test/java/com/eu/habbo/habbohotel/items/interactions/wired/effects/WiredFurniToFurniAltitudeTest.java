package com.eu.habbo.habbohotel.items.interactions.wired.effects;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.eu.habbo.habbohotel.items.Item;
import com.eu.habbo.habbohotel.rooms.*;
import com.eu.habbo.habbohotel.users.HabboItem;
import com.eu.habbo.habbohotel.wired.core.*;
import java.sql.ResultSet;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class WiredFurniToFurniAltitudeTest {
    @Test
    void movesToSignalTargetsBaseAltitudeAndSimulatesTheSameDestination() throws Exception {
        verifyAltitude(false);
    }

    @Test
    void keepAltitudePhysicsRetainsTheMoversHeightInExecutionAndSimulation() throws Exception {
        verifyAltitude(true);
    }

    private void verifyAltitude(boolean keepAltitude) throws Exception {
        Room room = mock(Room.class);
        RoomLayout layout = mock(RoomLayout.class);
        when(room.getLayout()).thenReturn(layout);
        RoomTile destination = new RoomTile((short) 17, (short) 6, (short) 0, RoomTileState.OPEN, true);
        when(layout.getTile((short) 17, (short) 6)).thenReturn(destination);
        HabboItem mover = mock(HabboItem.class), target = mock(HabboItem.class);
        when(mover.getId()).thenReturn(68);
        when(mover.getX()).thenReturn((short) 8);
        when(mover.getY()).thenReturn((short) 4);
        when(mover.getZ()).thenReturn(0.5);
        when(mover.getRotation()).thenReturn(2);
        when(target.getId()).thenReturn(215);
        when(target.getX()).thenReturn((short) 17);
        when(target.getY()).thenReturn((short) 6);
        when(target.getZ()).thenReturn(1.0);
        when(room.getHabboItem(68)).thenReturn(mover);
        when(room.getHabboItem(215)).thenReturn(target);
        var effect = new WiredEffectFurniToFurni(277, 16, mock(Item.class), "", 0, 0);
        ResultSet row = mock(ResultSet.class);
        when(row.getString("wired_data")).thenReturn(
                "{\"moveSource\":200,\"targetSource\":201,\"itemIds\":[],\"targetItemIds\":[],\"delay\":0}");
        effect.loadWiredData(row, room);
        WiredContext ctx = mock(WiredContext.class);
        when(ctx.room()).thenReturn(room);
        List<Object[]> moves = new ArrayList<>();
        try (var sources = mockStatic(WiredSourceUtil.class);
                var helper = mockStatic(WiredMoveCarryHelper.class, invocation -> {
                    if (invocation.getMethod().getName().equals("getFurniMovementPhysics"))
                        return new WiredMovementPhysics(keepAltitude, java.util.Set.of(), java.util.Set.of(), java.util.Set.of());
                    if (invocation.getMethod().getName().equals("moveFurni")) moves.add(invocation.getArguments());
                    return FurnitureMovementError.NONE;
                })) {
            sources.when(() -> WiredSourceUtil.resolveItems(eq(ctx), eq(200), anyCollection())).thenReturn(List.of(mover));
            sources.when(() -> WiredSourceUtil.resolveItems(eq(ctx), eq(201), anyCollection())).thenReturn(List.of(target));
            effect.execute(ctx);
            assertEquals(1, moves.size());
            assertEquals(destination, moves.getFirst()[3]);
            assertEquals(keepAltitude ? 0.5 : 1.0, moves.getFirst()[5]);
            WiredSimulation simulation = new WiredSimulation(room);
            assertTrue(effect.simulate(ctx, simulation));
            assertEquals(17, simulation.getItemPosition(mover).x);
            assertEquals(6, simulation.getItemPosition(mover).y);
            assertEquals(keepAltitude ? 0.5 : 1.0, simulation.getItemPosition(mover).z);
            assertEquals(2, simulation.getItemPosition(mover).rotation);
            verify(mover, never()).setX(anyShort());
        }
    }
}
