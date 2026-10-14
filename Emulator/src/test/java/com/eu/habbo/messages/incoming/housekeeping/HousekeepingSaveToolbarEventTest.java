package com.eu.habbo.messages.incoming.housekeeping;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.eu.habbo.habbohotel.gameclients.GameClient;
import com.eu.habbo.habbohotel.users.Habbo;
import com.eu.habbo.messages.ClientMessage;
import org.junit.jupiter.api.Test;

class HousekeepingSaveToolbarEventTest {
    @Test
    void requiresTheDedicatedCapabilityBeforeReadingAnyMutation() throws Exception {
        var handler = new HousekeepingSaveToolbarEvent();
        handler.client = mock(GameClient.class);
        handler.packet = mock(ClientMessage.class);
        var actor = mock(Habbo.class);
        when(handler.client.getHabbo()).thenReturn(actor);
        when(actor.hasPermission("acc_housekeeping")).thenReturn(true);
        when(actor.hasPermission("acc_hk_toolbar")).thenReturn(false);
        try (var lockdown = mockStatic(HousekeepingLockdown.class)) {
            lockdown.when(HousekeepingLockdown::isLocked).thenReturn(false);
            handler.handle();
            verifyNoInteractions(handler.packet);
        }
        assertEquals("acc_hk_toolbar", handler.requiredPermission());
    }
}
