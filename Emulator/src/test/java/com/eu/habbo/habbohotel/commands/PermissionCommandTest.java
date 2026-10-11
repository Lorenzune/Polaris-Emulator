package com.eu.habbo.habbohotel.commands;

import static org.mockito.Mockito.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.eu.habbo.WiredPlatform;
import com.eu.habbo.habbohotel.GameEnvironment;
import com.eu.habbo.habbohotel.gameclients.GameClient;
import com.eu.habbo.habbohotel.users.Habbo;
import org.junit.jupiter.api.Test;

class PermissionCommandTest {
    @Test
    void noReadPermissionCannotAccessAdministration() {
        GameClient client = mock(GameClient.class);
        Habbo actor = mock(Habbo.class);
        when(client.getHabbo()).thenReturn(actor);
        try (var platform = mockStatic(WiredPlatform.class)) {
            new PermissionCommand().handle(client, new String[] {"perm", "set", "target", "acc_test", "1"});
            platform.verifyNoInteractions();
            verify(actor, never()).whisper(anyString());
        }
    }

    @Test
    void readPermissionDoesNotAuthorizeMutation() {
        GameClient client = mock(GameClient.class);
        Habbo actor = mock(Habbo.class);
        when(client.getHabbo()).thenReturn(actor);
        when(actor.hasPermission("cmd_perm")).thenReturn(true);
        try (var platform = mockStatic(WiredPlatform.class)) {
            platform.when(WiredPlatform::gameEnvironment).thenReturn(mock(GameEnvironment.class));
            new PermissionCommand().handle(client, new String[] {"perm", "set", "target", "acc_test", "1"});
            verify(actor).whisper("Serve cmd_perm_edit");
            platform.verify(WiredPlatform::database, never());
        }
    }
}
