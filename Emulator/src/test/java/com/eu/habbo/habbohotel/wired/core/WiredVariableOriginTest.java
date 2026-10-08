package com.eu.habbo.habbohotel.wired.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;

import com.eu.habbo.habbohotel.rooms.Room;
import org.junit.jupiter.api.Test;

class WiredVariableOriginTest {
    @Test
    void inspectionIsCapturedButNestedWiredEffectsHaveTheirOwnLocalOrigin() {
        var room = mock(Room.class);
        assertEquals(1, WiredVariableChangeOrigin.current());
        try (var inspect = WiredVariableChangeOrigin.begin(4)) {
            var original =
                    WiredEvent.builder(WiredEvent.Type.VARIABLE_CHANGED, room).build();
            assertEquals(4, original.getVariableChangeOrigin());
            var effect = mock(com.eu.habbo.habbohotel.wired.api.IWiredEffect.class);
            doAnswer(invocation -> {
                        assertEquals(1, WiredVariableChangeOrigin.current());
                        return null;
                    })
                    .when(effect)
                    .execute(null);
            WiredExecutionScope.execute(effect, null);
            assertEquals(4, WiredVariableChangeOrigin.current());
            try (var remote = WiredVariableChangeOrigin.begin(2)) {
                assertEquals(2, WiredVariableChangeOrigin.current());
            }
            assertEquals(4, WiredVariableChangeOrigin.current());
        }
        assertEquals(1, WiredVariableChangeOrigin.current());
    }
}
