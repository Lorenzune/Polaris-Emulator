package com.eu.habbo.habbohotel.wired.core;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import com.eu.habbo.habbohotel.items.interactions.InteractionWiredExtra;
import com.eu.habbo.habbohotel.rooms.Room;
import org.junit.jupiter.api.Test;

class WiredVariableBoxUpdateScopeTest {
    @Test
    void nestedChangesActivateEachBoxOnceAfterTheOuterScope() {
        InteractionWiredExtra first = mock(InteractionWiredExtra.class);
        InteractionWiredExtra second = mock(InteractionWiredExtra.class);
        Room room = mock(Room.class);

        WiredVariableBoxUpdateScope outer = WiredVariableBoxUpdateScope.begin();
        WiredVariableBoxUpdateScope.activate(first, room);
        WiredVariableBoxUpdateScope nested = WiredVariableBoxUpdateScope.begin();
        WiredVariableBoxUpdateScope.activate(first, room);
        WiredVariableBoxUpdateScope.activate(second, room);

        nested.close();
        verifyNoInteractions(first, second);
        outer.close();

        verify(first).activateBox(room);
        verify(second).activateBox(room);
    }

    @Test
    void activationOutsideAScopeRemainsImmediate() {
        InteractionWiredExtra extra = mock(InteractionWiredExtra.class);
        Room room = mock(Room.class);

        WiredVariableBoxUpdateScope.activate(extra, room);

        verify(extra).activateBox(room);
    }
}
