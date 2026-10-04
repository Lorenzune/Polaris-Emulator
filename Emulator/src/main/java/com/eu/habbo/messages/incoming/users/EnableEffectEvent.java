package com.eu.habbo.messages.incoming.users;

import com.eu.habbo.habbohotel.dailytasks.DailyTaskActionMatcher;
import com.eu.habbo.messages.incoming.MessageHandler;

public class EnableEffectEvent extends MessageHandler {
    @Override
    public void handle() throws Exception {
        int effectId = this.packet.readInt();

        if (effectId > 0) {
            if (this.client.getHabbo().getInventory().getEffectsComponent().ownsEffect(effectId)) {
                this.client.getHabbo().getInventory().getEffectsComponent().enableEffect(effectId);
                DailyTaskActionMatcher.addEffectProgress(this.client.getHabbo(), effectId);
            }
        } else {
            this.client.getHabbo().setRetainedAvatarEffect(0);
            this.client.getHabbo().getInventory().getEffectsComponent().activatedEffect = 0;

            if (this.client.getHabbo().getHabboInfo().getCurrentRoom() != null) {
                this.client.getHabbo().getHabboInfo().getCurrentRoom().giveEffect(this.client.getHabbo().getRoomUnit(), 0, -1);
            }
        }
    }
}
