package com.eu.habbo.habbohotel.items.interactions.wired.effects;

import static org.mockito.Mockito.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.eu.habbo.habbohotel.games.Game;
import com.eu.habbo.habbohotel.games.GameState;
import com.eu.habbo.habbohotel.games.GameTeam;
import com.eu.habbo.habbohotel.games.GameTeamColors;
import com.eu.habbo.habbohotel.items.Item;
import com.eu.habbo.habbohotel.rooms.Room;
import com.eu.habbo.habbohotel.wired.core.WiredContext;
import java.sql.ResultSet;
import java.util.Set;
import org.junit.jupiter.api.Test;

class WiredEffectGiveScoreToTeamTest {
    @Test
    void scoresTheRunningGameThroughItsPublicStateRatherThanItsInheritedField() throws Exception {
        var room = mock(Room.class);
        var game = mock(Game.class);
        var paused = mock(Game.class);
        var team = mock(GameTeam.class);
        when(game.getState()).thenReturn(GameState.RUNNING);
        when(paused.getState()).thenReturn(GameState.PAUSED);
        when(game.getTeam(GameTeamColors.RED)).thenReturn(team);
        when(room.getGames()).thenReturn(Set.of(game, paused));
        var rows = mock(ResultSet.class);
        when(rows.getString("wired_data")).thenReturn("{\"score\":10,\"team\":\"RED\",\"operation\":0}");
        var effect = new WiredEffectGiveScoreToTeam(1, 1, mock(Item.class), "", 0, 0);
        effect.loadWiredData(rows, room);
        var context = mock(WiredContext.class);
        when(context.room()).thenReturn(room);
        effect.execute(context);
        verify(team).addTeamScore(10);
        verify(paused, never()).getTeam(any());
    }
}
