package com.eu.habbo.habbohotel.items.interactions.wired.triggers;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import com.eu.habbo.habbohotel.items.Item;
import com.eu.habbo.habbohotel.rooms.Room;
import com.eu.habbo.habbohotel.rooms.RoomUnit;
import com.eu.habbo.habbohotel.wired.core.WiredEvent;
import java.sql.ResultSet;
import org.junit.jupiter.api.Test;

class WiredUserActionMatchTest {
    @Test void everyActionMatchesOnlyItsOwnEventIncludingRelaxAnd67() throws Exception {
        var room=mock(Room.class);var unit=new RoomUnit();
        for(int selected:new int[]{1,2,3,4,5,6,7,8,9,10,11,67}) {
            var trigger=new WiredTriggerHabboPerformsAction(1,2,mock(Item.class),"",0,0);
            var data=mock(ResultSet.class);when(data.getString("wired_data")).thenReturn("{\"selectedAction\":"+selected+"}");
            trigger.loadWiredData(data,room);
            for(int incoming:new int[]{1,2,3,4,5,6,7,8,9,10,11,67}) {
                var event=WiredEvent.builder(WiredEvent.Type.USER_PERFORMS_ACTION,room).actor(unit).actionId(incoming).build();
                assertEquals(selected==incoming,trigger.matches(trigger,event),"selected="+selected+" incoming="+incoming);
            }
            assertFalse(trigger.matches(trigger,WiredEvent.builder(WiredEvent.Type.USER_PERFORMS_ACTION,room).actionId(selected).build()));
        }
    }
    @Test void signAndDanceFiltersDoNotLeakToOtherParameters() throws Exception {
        var room=mock(Room.class);var unit=new RoomUnit();
        for(int action:new int[]{9,10}) {
            var trigger=new WiredTriggerHabboPerformsAction(1,2,mock(Item.class),"",0,0);
            var data=mock(ResultSet.class);when(data.getString("wired_data")).thenReturn("{\"selectedAction\":"+action+",\"signFilterEnabled\":true,\"signId\":2,\"danceFilterEnabled\":true,\"danceId\":2}");
            trigger.loadWiredData(data,room);
            for(int param=1;param<=3;param++)assertEquals(param==2,trigger.matches(trigger,WiredEvent.builder(WiredEvent.Type.USER_PERFORMS_ACTION,room).actor(unit).actionId(action).actionParameter(param).build()));
        }
    }
}
