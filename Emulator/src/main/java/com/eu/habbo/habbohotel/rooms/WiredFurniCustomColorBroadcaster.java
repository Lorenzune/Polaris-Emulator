package com.eu.habbo.habbohotel.rooms;

import com.eu.habbo.habbohotel.gameclients.GameClient;
import com.eu.habbo.habbohotel.users.Habbo;
import com.eu.habbo.messages.outgoing.rooms.WiredFurniCustomColorComposer;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** Sends compact, size-bounded layer-color state only to clients that negotiated its support. */
public final class WiredFurniCustomColorBroadcaster {
    private static final int MAXIMUM_PACKET_BYTES = 64 * 1024;
    private static final int MAXIMUM_UPDATES = 1_000;

    private WiredFurniCustomColorBroadcaster() {}

    public static boolean supportsCustomColor(Habbo habbo) {
        return habbo != null
                && habbo.getClient() != null
                && habbo.getClient().supportsWiredFeature(3, GameClient.WIRED_FEATURE_CUSTOM_COLOR);
    }

    public static void broadcast(Room room, Collection<WiredOpacityState> updates) {
        if (room == null || updates == null || updates.isEmpty()) {
            return;
        }
        List<WiredOpacityState> payload = List.copyOf(updates);
        for (Habbo habbo : room.getHabbos()) {
            if (supportsCustomColor(habbo)) {
                send(habbo, room.getId(), payload);
            }
        }
    }

    public static void sendSnapshot(Habbo habbo, int roomId, Collection<WiredOpacityState> updates) {
        if (supportsCustomColor(habbo) && updates != null && !updates.isEmpty()) {
            send(habbo, roomId, updates);
        }
    }

    private static void send(Habbo habbo, int roomId, Collection<WiredOpacityState> updates) {
        for (List<WiredOpacityState> batch : batches(updates)) {
            habbo.getClient().sendResponse(new WiredFurniCustomColorComposer(roomId, batch));
        }
    }

    private static List<List<WiredOpacityState>> batches(Collection<WiredOpacityState> updates) {
        ArrayList<List<WiredOpacityState>> batches = new ArrayList<>();
        ArrayList<WiredOpacityState> current = new ArrayList<>();
        Set<String> colors = new HashSet<>();
        int bytes = 12; // room id, dictionary count and update count

        for (WiredOpacityState update : updates) {
            if (update == null || update.itemId() <= 0) {
                continue;
            }
            String color = update.customColor() == null ? "" : update.customColor();
            int addedBytes = 8 + (colors.contains(color) ? 0 : 2 + color.getBytes(StandardCharsets.UTF_8).length);
            if (!current.isEmpty()
                    && (current.size() >= MAXIMUM_UPDATES || bytes + addedBytes > MAXIMUM_PACKET_BYTES)) {
                batches.add(List.copyOf(current));
                current.clear();
                colors.clear();
                bytes = 12;
                addedBytes = 10 + color.getBytes(StandardCharsets.UTF_8).length;
            }
            current.add(update);
            colors.add(color);
            bytes += addedBytes;
        }
        if (!current.isEmpty()) {
            batches.add(List.copyOf(current));
        }
        return batches;
    }
}
