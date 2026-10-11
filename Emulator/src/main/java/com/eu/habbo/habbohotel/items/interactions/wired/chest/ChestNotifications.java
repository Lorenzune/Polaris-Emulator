package com.eu.habbo.habbohotel.items.interactions.wired.chest;

import com.eu.habbo.habbohotel.users.Habbo;
import com.eu.habbo.messages.outgoing.generic.alerts.BubbleAlertComposer;
import java.util.Map;

/** Uses the established notification packet, including for clients without chest-specific packets. */
public final class ChestNotifications {
    private ChestNotifications() {}

    public static void send(Habbo user, String event, String message) {
        if (user == null || user.getClient() == null) return;
        user.getClient()
                .sendResponse(new BubbleAlertComposer(
                        "chest." + event, Map.of("message", message, "display", "BUBBLE", "title", "Scrigni Wired")));
    }
}
