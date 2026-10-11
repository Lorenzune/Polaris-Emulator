package com.eu.habbo.habbohotel.commands;

import com.eu.habbo.Emulator;
import com.eu.habbo.habbohotel.gameclients.GameClient;
import com.eu.habbo.habbohotel.rooms.RoomChatMessageBubbles;
import java.util.concurrent.atomic.AtomicBoolean;

public class UpdatePermissionsCommand extends Command {
    private final AtomicBoolean updateInProgress = new AtomicBoolean();

    public UpdatePermissionsCommand() {
        super(
                "cmd_update_permissions",
                Emulator.getTexts()
                        .getValue("commands.keys.cmd_update_permissions")
                        .split(";"));
    }

    @Override
    public boolean handle(GameClient gameClient, String[] params) {
        if (!this.updateInProgress.compareAndSet(false, true)) {
            gameClient.getHabbo().whisper("Permissions update is already in progress.", RoomChatMessageBubbles.ALERT);
            return true;
        }

        var auditActor = new com.eu.habbo.habbohotel.permissions.PermissionAuditLog.Actor(
                gameClient.getHabbo().getHabboInfo().getId(),
                gameClient.getHabbo().getHabboInfo().getUsername(),
                "update_permissions",
                "Observed reload; SQL editor identity unknown");
        if (Emulator.getThreading().run(() -> {
                    try {
                        int refreshed = Emulator.getGameEnvironment().reloadPermissionsAndBroadcast(auditActor);

                        if (refreshed < 0 && gameClient.getChannel().isOpen() && gameClient.getHabbo() != null) {
                            gameClient
                                    .getHabbo()
                                    .whisper(
                                            "Permissions update is already in progress.", RoomChatMessageBubbles.ALERT);
                        } else if (gameClient.getChannel().isOpen() && gameClient.getHabbo() != null) {
                            gameClient
                                    .getHabbo()
                                    .whisper(
                                            Emulator.getTexts().getValue("commands.succes.cmd_update_permissions")
                                                    + " (" + refreshed + " online refreshed)",
                                            RoomChatMessageBubbles.ALERT);
                        }
                    } finally {
                        this.updateInProgress.set(false);
                    }
                })
                == null) {
            this.updateInProgress.set(false);
        }

        return true;
    }
}
