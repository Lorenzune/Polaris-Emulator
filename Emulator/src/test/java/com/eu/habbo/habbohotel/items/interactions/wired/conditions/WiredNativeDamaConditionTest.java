package com.eu.habbo.habbohotel.items.interactions.wired.conditions;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import com.eu.habbo.Emulator;
import com.eu.habbo.core.ConfigurationManager;
import com.eu.habbo.habbohotel.items.Item;
import com.eu.habbo.habbohotel.items.interactions.wired.WiredSettings;
import com.eu.habbo.habbohotel.rooms.*;
import com.eu.habbo.habbohotel.users.HabboItem;
import com.eu.habbo.habbohotel.wired.core.*;
import java.sql.ResultSet;
import java.util.List;
import org.junit.jupiter.api.Test;

class WiredNativeDamaConditionTest {
    @Test
    void roomEntryMatchesSelectedUsersInsteadOfTheActorItselfAfterReload() throws Exception {
        Room room = mock(Room.class);
        RoomUnit actor = mock(RoomUnit.class), allowed = mock(RoomUnit.class);
        when(actor.getId()).thenReturn(1);when(allowed.getId()).thenReturn(2);
        when(actor.getRoomUnitType()).thenReturn(RoomUnitType.USER);
        when(allowed.getRoomUnitType()).thenReturn(RoomUnitType.USER);
        var condition = new WiredConditionTriggererMatch(1,16,mock(Item.class),"",0,0);
        WiredSettings input = new WiredSettings(new int[]{1},"",new int[0],0);
        input.setNativeVariableFormat(true);input.setUserSourceTypes(new int[]{0,200});input.setQuantifier(1);
        assertTrue(condition.saveData(input));
        ResultSet row=mock(ResultSet.class);when(row.getString("wired_data")).thenReturn(condition.getWiredData());
        condition.loadWiredData(row,room);
        var ctx=new WiredContext(WiredEvent.builder(WiredEvent.Type.USER_ENTERS_ROOM,room).actor(actor).build(),null,mock(WiredServices.class),new WiredState(100));
        ctx.targets().setUsers(List.of(allowed));
        assertFalse(condition.evaluate(ctx));
        ctx.targets().setUsers(List.of(actor));assertTrue(condition.evaluate(ctx));
        ctx.targets().setUsers(List.of());assertFalse(condition.evaluate(ctx));
        assertTrue(condition.saveData(new WiredSettings(new int[]{1,0,0,0,0},"",new int[0],0)));
        assertTrue(condition.evaluate(ctx),"Existing local actor-to-actor configuration remains compatible");
    }

    @Test
    void clickedPieceReadsHundredthsAndComparisonFromNativeOptions() throws Exception {
        ConfigurationManager config=mock(ConfigurationManager.class);
        when(config.getInt("hotel.wired.furni.selection.count")).thenReturn(50);
        Room room=mock(Room.class);HabboItem piece=mock(HabboItem.class);when(piece.getId()).thenReturn(10);
        WiredSettings input=new WiredSettings(new int[]{206,0},"",new int[0],0);
        input.setNativeVariableFormat(true);input.setFurniSourceTypes(new int[]{0});input.setQuantifier(0);
        var condition=new WiredConditionHasAltitude(1,16,mock(Item.class),"",0,0);
        try(var emulator=mockStatic(Emulator.class)){
            emulator.when(Emulator::getConfig).thenReturn(config);
            assertTrue(condition.saveData(input));
            ResultSet row=mock(ResultSet.class);when(row.getString("wired_data")).thenReturn(condition.getWiredData());condition.loadWiredData(row,room);
            var ctx=new WiredContext(WiredEvent.builder(WiredEvent.Type.USER_CLICKS_FURNI,room).sourceItem(piece).build(),null,mock(WiredServices.class),new WiredState(100));
            when(piece.getZ()).thenReturn(2.0);assertTrue(condition.evaluate(ctx));
            when(piece.getZ()).thenReturn(2.06);assertFalse(condition.evaluate(ctx));
            when(piece.getZ()).thenReturn(2.2);assertFalse(condition.evaluate(ctx));
            assertTrue(condition.saveData(new WiredSettings(new int[]{0,0,0},"2.06",new int[0],0)));
            when(piece.getZ()).thenReturn(2.0);assertTrue(condition.evaluate(ctx));
        }
    }
}
