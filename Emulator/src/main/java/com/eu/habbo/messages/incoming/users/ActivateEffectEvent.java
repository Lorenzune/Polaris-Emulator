package com.eu.habbo.messages.incoming.users;

import com.eu.habbo.Emulator;
import com.eu.habbo.habbohotel.dailytasks.DailyTaskActionMatcher;
import com.eu.habbo.habbohotel.rooms.Room;
import com.eu.habbo.habbohotel.users.Habbo;
import com.eu.habbo.messages.incoming.MessageHandler;

public class ActivateEffectEvent extends MessageHandler {
    @Override
    public void handle() throws Exception {
        int effectId = this.packet.readInt();
        Habbo habbo = this.client.getHabbo();
        if (habbo == null) return;
        boolean keepOnRoomChange = this.packet.bytesAvailable() >= 1 && this.packet.readBoolean();
        int selectionType = this.packet.bytesAvailable() >= 4 ? this.packet.readInt() : 0;
        if (selectionType == 1) {
            if (effectId < 0 || effectId > 10000 || !habbo.hasPermission("cmd_hand_item")) return;
            Room currentRoom = habbo.getHabboInfo().getCurrentRoom();
            if (currentRoom == null) return;
            if (com.eu.habbo.habbohotel.rooms.RoomHanditemBlockSupport.isHanditemBlocked(currentRoom)) return;
            currentRoom.giveHandItem(habbo, effectId);
            habbo.setRetainedHandItem(keepOnRoomChange ? effectId : 0);
            return;
        }
        if (selectionType != 0) return;

        if (habbo.getInventory().getEffectsComponent().ownsEffect(effectId)) {
            habbo.getInventory().getEffectsComponent().activateEffect(effectId);
            habbo.setRetainedAvatarEffect(keepOnRoomChange ? effectId : 0);
            DailyTaskActionMatcher.addEffectProgress(habbo, effectId);
            return;
        }

        int rankId = habbo.getHabboInfo().getRank().getId();
        if (Emulator.getGameEnvironment().getPermissionsManager().isEffectBlocked(effectId, rankId)) {
            return;
        }

        Room room = habbo.getHabboInfo().getCurrentRoom();
        if (room == null || habbo.getHabboInfo().getRiding() != null) return;

        room.giveEffect(habbo, effectId, -1);
        habbo.setRetainedAvatarEffect(keepOnRoomChange ? effectId : 0);
        DailyTaskActionMatcher.addEffectProgress(habbo, effectId);
    }
}
