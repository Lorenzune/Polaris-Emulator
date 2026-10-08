package com.eu.habbo.messages.incoming.rooms.users;
import static org.mockito.Mockito.*;
import com.eu.habbo.Emulator;
import com.eu.habbo.habbohotel.gameclients.GameClient;
import com.eu.habbo.habbohotel.rooms.*;
import com.eu.habbo.habbohotel.wired.WiredUserActionType;
import com.eu.habbo.habbohotel.wired.core.WiredManager;
import com.eu.habbo.messages.ClientMessage;
import com.eu.habbo.plugin.PluginManager;
import io.netty.buffer.Unpooled;
import org.junit.jupiter.api.Test;

class RoomUserActionIdleDispatchTest {
 @Test void manualRelaxUsesOneCentralIdleEvent() throws Exception {
  var client=mock(GameClient.class,RETURNS_DEEP_STUBS);var room=mock(Room.class);var unit=new RoomUnit();
  when(client.getHabbo().getHabboInfo().getCurrentRoom()).thenReturn(room);
  when(client.getHabbo().getRoomUnit()).thenReturn(unit);
  var plugins=mock(PluginManager.class);when(plugins.fireEvent(any())).thenAnswer(i->i.getArgument(0));
  var habbo=client.getHabbo();
  doAnswer(i->{new RoomUnitManager(room).idle(habbo);return null;}).when(room).idle(habbo);
  try(var emulator=mockStatic(Emulator.class);var wired=mockStatic(WiredManager.class)) {
   emulator.when(Emulator::getPluginManager).thenReturn(plugins);
   var event=new RoomUserActionEvent();event.client=client;event.packet=new ClientMessage(0,Unpooled.buffer().writeInt(5));event.handle();
   wired.verify(()->WiredManager.triggerUserPerformsAction(room,unit,WiredUserActionType.RELAX,-1),times(1));
  }
 }
}
