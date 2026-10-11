package com.eu.habbo.habbohotel.items.interactions.wired.conditions;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.RETURNS_DEEP_STUBS;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.eu.habbo.habbohotel.games.GameTeamColors;
import com.eu.habbo.habbohotel.items.Item;
import com.eu.habbo.habbohotel.items.interactions.games.InteractionGameTimer;
import com.eu.habbo.habbohotel.rooms.Room;
import com.eu.habbo.habbohotel.rooms.RoomUnit;
import com.eu.habbo.habbohotel.users.Habbo;
import com.eu.habbo.habbohotel.wired.core.WiredContext;
import java.sql.ResultSet;
import org.junit.jupiter.api.Test;

class WiredClockAndTeamStateTest {
    @Test
    void nativeActiveClockSnapshotWorksThroughoutCountdownAndFailsWhenStopped() throws Exception {
        var base = mock(Item.class);
        when(base.getCustomParams()).thenReturn("30,60,120");
        var clock = new InteractionGameTimer(674, 16, base, "120\t120", 0, 0);
        var room = mock(Room.class);
        when(room.getHabboItem(674)).thenReturn(clock);
        var context = mock(WiredContext.class);
        when(context.room()).thenReturn(room);
        var condition = new WiredConditionMatchStatePosition(86, 16, null, "", 0, 0);
        var data = mock(ResultSet.class);
        when(data.getString("wired_data"))
                .thenReturn(
                        "{\"state\":true,\"furniSource\":100,\"explicitSource\":true,\"settings\":[{\"item_id\":674,\"state\":\"1\"}]}");
        condition.loadWiredData(data, room);
        assertFalse(condition.evaluateAllTargetsMatch(context));
        clock.setRunning(true);
        assertTrue(condition.evaluateAllTargetsMatch(context));
        clock.setTimeNow(80);
        assertTrue(condition.evaluateAllTargetsMatch(context));
        clock.setRunning(false);
        assertFalse(condition.evaluateAllTargetsMatch(context));
    }

    @Test
    void anyTeamMatchesRealTeamButExcludesUnassignedPlayer() throws Exception {
        var room = mock(Room.class);
        var unit = mock(RoomUnit.class);
        var habbo = mock(Habbo.class, RETURNS_DEEP_STUBS);
        when(room.getHabbo(unit)).thenReturn(habbo);
        var condition = new WiredConditionTeamMember(847, 16, null, "", 0, 0);
        var data = mock(ResultSet.class);
        when(data.getString("wired_data")).thenReturn("{\"teamColor\":\"NONE\",\"userSource\":0,\"quantifier\":0}");
        condition.loadWiredData(data, room);
        when(habbo.getHabboInfo().getGamePlayer().getTeamColor()).thenReturn(GameTeamColors.RED);
        assertTrue(condition.matchesTeam(room, unit));
        when(habbo.getHabboInfo().getGamePlayer().getTeamColor()).thenReturn(GameTeamColors.BLUE);
        assertTrue(condition.matchesTeam(room, unit));
        when(habbo.getHabboInfo().getGamePlayer().getTeamColor()).thenReturn(GameTeamColors.NONE);
        assertFalse(condition.matchesTeam(room, unit));
        when(room.getHabbo(unit)).thenReturn(null);
        assertFalse(condition.matchesTeam(room, unit));
    }
}
