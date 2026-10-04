package com.eu.habbo.habbohotel.items.interactions.wired.extra;
import com.eu.habbo.habbohotel.items.Item;
import com.eu.habbo.habbohotel.items.interactions.wired.WiredSettings;
import com.eu.habbo.habbohotel.rooms.Room;
import com.eu.habbo.habbohotel.wired.core.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
class WiredExtraMovementCurveTest {
    private WiredExtraMovementCurve extra() { return new WiredExtraMovementCurve(1,2,mock(Item.class),"",0,0); }
    @Test void nativeSignedStrengthPersistsAndIsBounded() throws Exception {
        var extra = extra();
        extra.saveData(new WiredSettings(new int[]{0,-2000,0,0},"",new int[0],0),null);
        assertEquals(-1000, extra.resolveCurveStrength(null,null));
        var row = mock(java.sql.ResultSet.class); when(row.getString("wired_data")).thenReturn(extra.getWiredData());
        var restored = extra(); restored.loadWiredData(row,null);
        assertEquals(-1000, restored.resolveCurveStrength(null,null));
        restored.saveData(new WiredSettings(new int[]{0,0,0},"",new int[0],0),null);
        assertEquals(0, restored.resolveCurveStrength(null,null));
    }
    @Test void readsRoomVariableAndKeepsMissingVariableLinear() {
        var extra = extra(); var room = mock(Room.class, RETURNS_DEEP_STUBS);
        var ctx = new WiredContext(WiredEvent.builder(WiredEvent.Type.CUSTOM,room).build(),null,mock(WiredServices.class),new WiredState(20));
        extra.saveData(new WiredSettings(new int[]{1,80,-10,0},"custom:77",new int[0],0),null);
        when(room.getRoomVariableManager().hasVariable(77)).thenReturn(true);
        when(room.getRoomVariableManager().getCurrentValue(77)).thenReturn(600);
        assertEquals(600,extra.resolveCurveStrength(ctx,null));
        when(room.getRoomVariableManager().hasVariable(77)).thenReturn(false);
        assertNull(extra.resolveCurveStrength(ctx,null));
    }
    @Test void userMovementPacketCarriesSignedNativeJumpPower() {
        var message = new com.eu.habbo.messages.outgoing.rooms.WiredMovementsComposer(java.util.List.of(
            com.eu.habbo.messages.outgoing.rooms.WiredMovementsComposer.userSlideMovement(12,0,0,4,0,0,0,2,2,500,-100))).compose();
        var buffer = message.get();
        try {
            var payload = buffer.duplicate(); payload.skipBytes(6);
            assertEquals(1,payload.readInt()); assertEquals(0,payload.readInt());
            payload.skipBytes(16); payload.skipBytes(payload.readUnsignedShort()); payload.skipBytes(payload.readUnsignedShort());
            assertEquals(12,payload.readInt()); assertEquals(1,payload.readInt());
            payload.skipBytes(12); assertTrue(payload.readBoolean()); assertEquals(-100,payload.readInt());
        } finally { buffer.release(); }
    }
    @Test void legacyDataRemainsReadable() throws Exception {
        var extra = extra(); var row = mock(java.sql.ResultSet.class);
        when(row.getString("wired_data")).thenReturn("{\"curveType\":4,\"intensity\":70}");
        extra.loadWiredData(row,null);
        assertEquals(4,extra.getCurveType()); assertEquals(70,extra.getIntensity());
        assertEquals(70,extra.resolveCurveStrength(null,null));
    }
}
