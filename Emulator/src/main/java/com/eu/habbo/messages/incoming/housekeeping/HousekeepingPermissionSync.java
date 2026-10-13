package com.eu.habbo.messages.incoming.housekeeping;

import com.eu.habbo.Emulator;
import com.eu.habbo.habbohotel.commands.UpdatePermissionsCommand;
import com.eu.habbo.habbohotel.gameclients.GameClient;
import com.eu.habbo.habbohotel.permissions.PermissionsManager;
import com.eu.habbo.habbohotel.users.Habbo;
import com.eu.habbo.messages.outgoing.users.UserPermissionsComposer;
import java.util.concurrent.atomic.AtomicLong;

/** Refresh an old in-memory permission table after installing the HK migration. */
final class HousekeepingPermissionSync {
    private static final AtomicLong nextReloadAt = new AtomicLong();

    static boolean needsReload(PermissionsManager permissions, Habbo actor) {
        return !permissions.isPermissionDefined(HousekeepingAreas.ROOMS)
                && actor.hasPermission("cmd_update_permissions");
    }

    static void refresh(GameClient client) {
        var permissions = Emulator.getGameEnvironment().getPermissionsManager();
        if (needsReload(permissions, client.getHabbo())) {
            long now = System.currentTimeMillis();
            long next = nextReloadAt.get();
            if (now >= next && nextReloadAt.compareAndSet(next, now + 60_000L)) {
                var command = new UpdatePermissionsCommand();
                command.handle(client, new String[] {"update_permissions"});
            }
        }
        // Resend actual effective grants; a defined denial never falls back to panel access.
        client.sendResponse(new UserPermissionsComposer(client.getHabbo()));
    }
}
