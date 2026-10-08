package com.eu.habbo.habbohotel.items.interactions.wired;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import com.eu.habbo.habbohotel.items.Item;
import com.eu.habbo.habbohotel.items.interactions.wired.extra.WiredExtraExecutionLimit;
import com.eu.habbo.habbohotel.rooms.Room;
import java.sql.ResultSet;
import org.junit.jupiter.api.Test;

class WiredExecutionLimitPulseTest {
    @Test
    void oneHalfSecondWindowBlocksDuplicatesButAllowsTheNextPulse() throws Exception {
        var extra = configured(500);
        assertEquals(500, extra.getTimeWindowMs());
        assertTrue(extra.tryAcquireExecutionSlot(10000));
        assertFalse(extra.tryAcquireExecutionSlot(10499));
        assertTrue(extra.tryAcquireExecutionSlot(10500));
    }

    @Test
    void twoSwfPulsesAllowTheDelayedGridContinuationAfterOneSecond() throws Exception {
        var extra = configured(1000);
        assertTrue(extra.tryAcquireExecutionSlot(10000));
        assertFalse(extra.tryAcquireExecutionSlot(10999));
        assertTrue(extra.tryAcquireExecutionSlot(11080));
    }

    private WiredExtraExecutionLimit configured(int milliseconds) throws Exception {
        var extra = new WiredExtraExecutionLimit(1, 1, mock(Item.class), "", 0, 0);
        var row = mock(ResultSet.class);
        when(row.getString("wired_data")).thenReturn(
                "{\"maxExecutions\":1,\"timeWindowMs\":" + milliseconds + "}");
        extra.loadWiredData(row, mock(Room.class));
        return extra;
    }
}
