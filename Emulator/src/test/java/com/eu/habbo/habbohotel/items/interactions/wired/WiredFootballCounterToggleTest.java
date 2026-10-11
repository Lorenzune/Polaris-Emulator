package com.eu.habbo.habbohotel.items.interactions.wired;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.eu.habbo.habbohotel.items.Item;
import com.eu.habbo.habbohotel.items.interactions.games.football.scoreboards.InteractionFootballScoreboard;
import com.eu.habbo.habbohotel.items.interactions.wired.effects.WiredEffectToggleFurni;
import com.eu.habbo.habbohotel.rooms.Room;
import com.eu.habbo.habbohotel.wired.core.WiredContext;
import java.sql.ResultSet;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class WiredFootballCounterToggleTest {
    @Test
    void blankSwfSnapshotResetsFootballCounterForTheNextGame() throws Exception {
        Room room = mock(Room.class);
        var layout = mock(com.eu.habbo.habbohotel.rooms.RoomLayout.class);
        when(room.getLayout()).thenReturn(layout);
        when(layout.getTile((short) 0, (short) 0))
                .thenReturn(new com.eu.habbo.habbohotel.rooms.RoomTile(
                        (short) 0, (short) 0, (short) 0, com.eu.habbo.habbohotel.rooms.RoomTileState.OPEN, true));
        var counter = mock(InteractionFootballScoreboard.class);
        when(counter.getId()).thenReturn(123);
        // Football counters inherit false: their numeric state still supports reset.
        when(counter.allowWiredResetState()).thenReturn(false);
        when(counter.getScore()).thenReturn(93);
        when(counter.getExtradata()).thenReturn("93");
        when(room.getHabboItem(123)).thenReturn(counter);
        var effect = new com.eu.habbo.habbohotel.items.interactions.wired.effects.WiredEffectMatchFurni(
                1, 1, mock(Item.class), "", 0, 0);
        ResultSet row = mock(ResultSet.class);
        when(row.getString("wired_data"))
                .thenReturn("{\"state\":true,\"furniSource\":100,\"items\":[{\"item_id\":123,\"state\":\" \"}]}");
        effect.loadWiredData(row, room);
        WiredContext ctx = mock(WiredContext.class);
        when(ctx.room()).thenReturn(room);
        effect.execute(ctx);
        verify(counter).setScore(0);
        verify(room).updateItemState(counter);
    }

    @Test
    void wiredTogglesFootballCounterAndKeepsTheSelectedCounter() throws Exception {
        Room room = mock(Room.class);
        Item counterBase = mock(Item.class);
        when(counterBase.getStateCount()).thenReturn(100);
        var counter = mock(InteractionFootballScoreboard.class);
        when(counter.getBaseItem()).thenReturn(counterBase);
        when(counter.getId()).thenReturn(123);
        when(counter.getRoomId()).thenReturn(268);
        when(counter.getExtradata()).thenReturn("6");
        when(room.getHabboItem(123)).thenReturn(counter);
        var effect = new WiredEffectToggleFurni(1, 1, mock(Item.class), "", 0, 0);
        ResultSet row = mock(ResultSet.class);
        when(row.getString("wired_data")).thenReturn("{\"itemIds\":[123],\"furniSource\":100,\"toggleType\":0}");
        effect.loadWiredData(row, room);
        WiredContext ctx = mock(WiredContext.class);
        when(ctx.room()).thenReturn(room);
        when(ctx.actor()).thenReturn(Optional.empty());
        effect.execute(ctx);
        verify(counter).setScore(7);
        assertTrue(effect.getWiredData().contains("[123]"));
    }
}
