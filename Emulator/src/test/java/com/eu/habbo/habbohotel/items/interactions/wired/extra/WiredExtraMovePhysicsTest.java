package com.eu.habbo.habbohotel.items.interactions.wired.extra;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.when;

import com.eu.habbo.habbohotel.items.Item;
import com.eu.habbo.habbohotel.items.interactions.wired.WiredSettings;
import com.eu.habbo.messages.ServerMessage;
import java.sql.ResultSet;
import org.junit.jupiter.api.Test;

class WiredExtraMovePhysicsTest {
    private WiredExtraMovePhysics extra() {
        return new WiredExtraMovePhysics(1, 2, mock(Item.class), "", 0, 0);
    }

    @Test
    void newEmptyAndPickedUpAccessoriesKeepAltitudeAndUseAllRoomSources() throws Exception {
        var extra = extra();
        assertDefaults(extra);
        for (String payload : new String[] {"", "{}", "{broken"}) {
            extra.saveData(new WiredSettings(new int[] {0, 1, 1, 1, 201, 201, 201}, "", new int[0], 0), null);
            var row = mock(ResultSet.class);
            when(row.getString("wired_data")).thenReturn(payload);
            extra.loadWiredData(row, null);
            assertDefaults(extra);
        }
        extra.saveData(new WiredSettings(new int[] {0, 1, 1, 1, 201, 201, 201}, "", new int[0], 0), null);
        extra.onPickUp();
        assertDefaults(extra);
    }

    @Test
    void serverSendsRequestedDefaultsToExistingClients() {
        var message = mock(ServerMessage.class);
        extra().serializeWiredData(message, null);
        var order = inOrder(message);
        order.verify(message).appendBoolean(false);
        order.verify(message, times(3)).appendInt(0);
        order.verify(message).appendInt(1);
        order.verify(message).appendString("");
        order.verify(message).appendInt(7);
        order.verify(message).appendInt(1);
        order.verify(message, times(3)).appendInt(0);
        order.verify(message, times(3)).appendInt(900);
    }

    @Test
    void explicitDisabledAltitudeAndSelectedOriginsSurviveReload() throws Exception {
        var extra = extra();
        extra.saveData(new WiredSettings(new int[] {0, 0, 0, 0, 0, 200, 201}, "", new int[0], 0), null);
        var row = mock(ResultSet.class);
        when(row.getString("wired_data")).thenReturn(extra.getWiredData());
        var restored = extra();
        restored.loadWiredData(row, null);
        assertFalse(restored.isKeepAltitude());
        assertEquals(0, restored.getMoveThroughFurniSource());
        assertEquals(200, restored.getBlockByFurniSource());
        assertEquals(201, restored.getMoveThroughUsersSource());
    }

    @Test
    void legacyFlagsAndShortSavesUseAllRoomForMissingOrigins() throws Exception {
        var extra = extra();
        var row = mock(ResultSet.class);
        when(row.getString("wired_data")).thenReturn("0\t0\t0\t0");
        extra.loadWiredData(row, null);
        assertFalse(extra.isKeepAltitude());
        assertAllRoomSources(extra);
        extra.saveData(new WiredSettings(new int[0], "", new int[0], 0), null);
        assertDefaults(extra);
    }

    private void assertDefaults(WiredExtraMovePhysics extra) {
        assertTrue(extra.isKeepAltitude());
        assertFalse(extra.isMoveThroughFurni());
        assertFalse(extra.isMoveThroughUsers());
        assertFalse(extra.isBlockByFurni());
        assertAllRoomSources(extra);
    }

    private void assertAllRoomSources(WiredExtraMovePhysics extra) {
        assertEquals(900, extra.getMoveThroughFurniSource());
        assertEquals(900, extra.getBlockByFurniSource());
        assertEquals(900, extra.getMoveThroughUsersSource());
    }
}
