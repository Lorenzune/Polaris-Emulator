package com.eu.habbo.habbohotel.permissions;

import static org.mockito.Mockito.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.eu.habbo.habbohotel.commands.CommandHandler;
import com.eu.habbo.habbohotel.gameclients.GameClient;
import com.eu.habbo.habbohotel.users.Habbo;
import com.eu.habbo.habbohotel.users.HabboInfo;
import com.eu.habbo.habbohotel.users.HabboManager;
import com.eu.habbo.habbohotel.users.HabboStats;
import com.eu.habbo.threading.ThreadPooling;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;

class PermissionLiveRefreshTest {
    @Test
    void changedOverridesRefreshClientButUnchangedStateDoesNot() {
        var rows = new AtomicReference<List<UserPermissionOverrides.UserOverride>>(List.of());
        var store = new UserPermissionOverrides(id -> rows.get(), System::currentTimeMillis);
        PermissionsManager manager = mock(PermissionsManager.class);
        when(manager.getOverrides()).thenReturn(store);
        Habbo habbo = mock(Habbo.class);
        HabboInfo info = mock(HabboInfo.class);
        GameClient client = mock(GameClient.class);
        when(habbo.getHabboInfo()).thenReturn(info);
        when(info.getId()).thenReturn(42);
        when(info.getRank()).thenReturn(mock(Rank.class));
        when(habbo.getClient()).thenReturn(client);
        when(habbo.getHabboStats()).thenReturn(mock(HabboStats.class));
        var refresh = new PermissionLiveRefresh(
                manager, mock(HabboManager.class), mock(CommandHandler.class), mock(ThreadPooling.class));
        refresh.refresh(habbo);
        refresh.refresh(habbo);
        verifyNoInteractions(client);
        rows.set(List.of(new UserPermissionOverrides.UserOverride(
                "acc_test", PermissionSetting.ALLOWED, System.currentTimeMillis() + 10000, "", 99, "")));
        store.invalidate(42);
        refresh.refresh(habbo);
        verify(client, times(3)).sendResponse(any(com.eu.habbo.messages.outgoing.MessageComposer.class));
        refresh.refresh(habbo);
        verify(client, times(3)).sendResponse(any(com.eu.habbo.messages.outgoing.MessageComposer.class));
        rows.set(List.of(new UserPermissionOverrides.UserOverride(
                "acc_test", PermissionSetting.ALLOWED, System.currentTimeMillis() - 1, "", 99, "")));
        store.invalidate(42);
        refresh.refresh(habbo);
        verify(client, times(6)).sendResponse(any(com.eu.habbo.messages.outgoing.MessageComposer.class));
        refresh.dispose();
    }
}
