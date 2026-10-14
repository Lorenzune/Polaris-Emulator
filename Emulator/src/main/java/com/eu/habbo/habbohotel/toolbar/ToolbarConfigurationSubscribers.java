package com.eu.habbo.habbohotel.toolbar;

import com.eu.habbo.habbohotel.gameclients.GameClient;
import com.eu.habbo.messages.outgoing.housekeeping.ToolbarConfigurationComposer;
import java.util.Collections;
import java.util.Set;
import java.util.WeakHashMap;

/** Only clients that opt into this additive protocol receive live updates. */
public final class ToolbarConfigurationSubscribers {
    private static final Set<GameClient> CLIENTS = Collections.newSetFromMap(new WeakHashMap<>());

    private ToolbarConfigurationSubscribers() {}

    public static synchronized void subscribe(GameClient client) {
        CLIENTS.add(client);
    }

    public static synchronized void broadcast(ToolbarConfigurationStore.State state) {
        CLIENTS.removeIf(client -> client.getHabbo() == null
                || client.getChannel() == null
                || !client.getChannel().isActive());
        for (GameClient client : CLIENTS) client.sendResponse(new ToolbarConfigurationComposer(state));
    }
}
