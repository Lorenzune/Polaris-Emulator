package com.eu.habbo.messages.incoming.users;

import com.eu.habbo.habbohotel.dailytasks.DailyTaskActionMatcher;
import com.eu.habbo.habbohotel.users.HabboBadge;
import com.eu.habbo.habbohotel.users.inventory.BadgesComponent;
import com.eu.habbo.messages.incoming.MessageHandler;
import java.util.LinkedHashMap;
import java.util.Map;

public class UserWearBadgeEvent extends MessageHandler {
    @Override
    public int getRatelimit() {
        return 250;
    }

    @Override
    public void handle() throws Exception {
        Map<Integer, HabboBadge> requestedBadges = new LinkedHashMap<>();

        for (int i = 0; i < 5; i++) {
            int slot = this.packet.readInt();
            if (slot < 1 || slot > 5) return;

            String badgeId = this.packet.readString();
            if (badgeId.isEmpty()) continue;

            HabboBadge badge = this.client.getHabbo().getInventory().getBadgesComponent().getBadge(badgeId);
            if (badge == null || requestedBadges.containsValue(badge) || requestedBadges.containsKey(slot)) continue;

            requestedBadges.put(slot, badge);
        }

        BadgesComponent badgesComponent = this.client.getHabbo().getInventory().getBadgesComponent();
        if (!badgesComponent.replaceWearingBadges(this.client.getHabbo(), requestedBadges)) return;

        BadgesComponent.synchronizeBadgeState(this.client.getHabbo());

        if (!requestedBadges.isEmpty()) {
            DailyTaskActionMatcher.addProgress(this.client.getHabbo(), DailyTaskActionMatcher.WEAR_BADGE, 1);
        }
    }
}
