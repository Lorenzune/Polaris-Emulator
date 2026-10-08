package com.eu.habbo.habbohotel.wired.core;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import com.eu.habbo.Emulator;
import com.eu.habbo.habbohotel.GameEnvironment;
import com.eu.habbo.habbohotel.items.Item;
import com.eu.habbo.habbohotel.items.interactions.InteractionWiredCondition;
import com.eu.habbo.habbohotel.items.interactions.wired.conditions.*;
import com.eu.habbo.habbohotel.rooms.Room;
import com.eu.habbo.habbohotel.users.HabboItem;
import java.sql.ResultSet;
import org.junit.jupiter.api.Test;

class WiredFurniInstanceComparisonTest {
    private boolean evaluate(InteractionWiredCondition condition, int comparedId) throws Exception {
        var room=mock(Room.class);
        var base=mock(Item.class);when(base.getId()).thenReturn(42);
        var first=mock(HabboItem.class);when(first.getId()).thenReturn(1);when(first.getBaseItem()).thenReturn(base);
        var second=mock(HabboItem.class);when(second.getId()).thenReturn(2);when(second.getBaseItem()).thenReturn(base);
        when(room.getHabboItem(1)).thenReturn(first);when(room.getHabboItem(2)).thenReturn(second);
        var environment=mock(GameEnvironment.class,RETURNS_DEEP_STUBS);
        when(environment.getRoomManager().getRoom(269)).thenReturn(room);
        condition.setRoomId(269);
        var data=mock(ResultSet.class);
        when(data.getString("wired_data")).thenReturn("{\"primaryItemIds\":[1],\"secondaryItemIds\":["+comparedId+"],\"furniSource\":100,\"compareFurniSource\":101,\"quantifier\":0}");
        try(var emulator=mockStatic(Emulator.class)) {
            emulator.when(Emulator::getGameEnvironment).thenReturn(environment);
            condition.loadWiredData(data,room);
            var event=WiredEvent.builder(WiredEvent.Type.USER_CLICKS_FURNI,room).sourceItem(first).build();
            var ctx=new WiredContext(event,condition,null,DefaultWiredServices.getInstance(),new WiredState(100),null);
            String saved=condition.getWiredData();
            when(data.getString("wired_data")).thenReturn(saved);condition.loadWiredData(data,room);
            assertEquals(saved,condition.getWiredData());
            return condition.evaluate(ctx);
        }
    }
    @Test void defaultTypeComparisonStillAcceptsAnotherInstanceOfSameType() throws Exception {
        assertTrue(evaluate(new WiredConditionFurniTypeMatch(10,16,mock(Item.class),"",0,0),2));
    }
    @Test void instanceComparisonRejectsAnotherInstanceOfSameType() throws Exception {
        assertFalse(evaluate(new WiredConditionFurniInstanceMatch(10,16,mock(Item.class),"",0,0),2));
    }
    @Test void instanceComparisonAcceptsTheSelectedInstance() throws Exception {
        assertTrue(evaluate(new WiredConditionFurniInstanceMatch(10,16,mock(Item.class),"",0,0),1));
    }
    @Test void inverseComparisonUsesInstanceIdentity() throws Exception {
        assertTrue(evaluate(new WiredConditionNotFurniInstanceMatch(10,16,mock(Item.class),"",0,0),2));
        assertFalse(evaluate(new WiredConditionNotFurniInstanceMatch(10,16,mock(Item.class),"",0,0),1));
    }
    @Test void anchoredComparisonPreservesSameStackIdentity() throws Exception {
        assertTrue(evaluate(new WiredConditionFurniAnchoredInstanceMatch(10,16,mock(Item.class),"",0,0),1));
        assertFalse(evaluate(new WiredConditionFurniAnchoredInstanceMatch(10,16,mock(Item.class),"",0,0),2));
    }
    @Test void referencedComparisonDoesNotUseTheCallingStacksSelection() throws Exception {
        var room=mock(Room.class);
        var base=mock(Item.class);
        var pawn=mock(HabboItem.class);when(pawn.getId()).thenReturn(1);when(pawn.getBaseItem()).thenReturn(base);
        var emptyTile=mock(HabboItem.class);when(emptyTile.getId()).thenReturn(2);when(emptyTile.getBaseItem()).thenReturn(base);
        when(room.getHabboItem(1)).thenReturn(pawn);when(room.getHabboItem(2)).thenReturn(emptyTile);
        var environment=mock(GameEnvironment.class,RETURNS_DEEP_STUBS);
        when(environment.getRoomManager().getRoom(269)).thenReturn(room);
        var condition=new WiredConditionFurniAnchoredInstanceMatch(10,16,base,"",0,0);
        condition.setRoomId(269);condition.setX((short)3);
        var data=mock(ResultSet.class);
        when(data.getString("wired_data")).thenReturn("{\"primaryItemIds\":[1],\"secondaryItemIds\":[],\"furniSource\":100,\"compareFurniSource\":200,\"quantifier\":1}");
        var caller=mock(HabboItem.class);when(caller.getX()).thenReturn((short)7);
        try(var emulator=mockStatic(Emulator.class);var sources=mockStatic(WiredSourceUtil.class,CALLS_REAL_METHODS)) {
            emulator.when(Emulator::getGameEnvironment).thenReturn(environment);
            condition.loadWiredData(data,room);
            var event=WiredEvent.builder(WiredEvent.Type.SIGNAL_RECEIVED,room).sourceItem(pawn).build();
            var parent=new WiredContext(event,caller,null,DefaultWiredServices.getInstance(),new WiredState(100),null);
            parent.targets().setItems(java.util.List.of(pawn));
            sources.when(()->WiredSourceUtil.resolveSelectorItems(any(WiredContext.class),eq(false))).thenAnswer(invocation->{
                WiredContext own=invocation.getArgument(0);
                assertSame(condition,own.triggerItem());assertSame(parent.event(),own.event());
                return java.util.List.of(emptyTile);
            });
            assertFalse(condition.evaluate(parent));
            assertEquals(java.util.List.of(pawn),new java.util.ArrayList<>(parent.targets().items()));
        }
    }
}
