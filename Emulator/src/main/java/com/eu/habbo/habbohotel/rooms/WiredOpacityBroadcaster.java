package com.eu.habbo.habbohotel.rooms;

import com.eu.habbo.habbohotel.gameclients.GameClient;
import com.eu.habbo.habbohotel.users.Habbo;
import com.eu.habbo.messages.outgoing.rooms.WiredFurniOpacityComposer;
import java.util.Collection;
import java.util.List;

/**
 * Sends furniture opacity updates only to clients that announced opacity support, so clients that
 * never negotiated the capability keep the historical rendering.
 */
public final class WiredOpacityBroadcaster {

    private WiredOpacityBroadcaster() {}

    public static void broadcast(Room room, Collection<WiredOpacityState> updates, int easing, int durationMs) {
        if (room == null || updates == null || updates.isEmpty()) {
            return;
        }

        List<WiredOpacityState> payload = List.copyOf(updates);
        for (Habbo habbo : room.getHabbos()) {
            int protocolVersion = opacityProtocolVersion(habbo);
            if (protocolVersion == 0) {
                continue;
            }
            habbo.getClient().sendResponse(
                    new WiredFurniOpacityComposer(protocolVersion, room.getId(), payload, easing, durationMs));
        }
    }

    public static boolean supportsOpacity(Habbo habbo) {
        return opacityProtocolVersion(habbo) > 0;
    }

    public static void sendCustomColorSnapshot(Habbo habbo, int roomId, Collection<WiredOpacityState> updates) {
        WiredFurniCustomColorBroadcaster.sendSnapshot(habbo, roomId, updates);
    }

    public static int opacityProtocolVersion(Habbo habbo) {
        if (habbo == null || habbo.getClient() == null) {
            return 0;
        }

        if (habbo.getClient().supportsWiredFeature(
                WiredFurniOpacityComposer.PROTOCOL_VERSION, GameClient.WIRED_FEATURE_OPACITY)) {
            return WiredFurniOpacityComposer.PROTOCOL_VERSION;
        }

        if (habbo.getClient().supportsWiredFeature(
                WiredFurniOpacityComposer.LEGACY_PROTOCOL_VERSION, GameClient.WIRED_FEATURE_OPACITY)) {
            return WiredFurniOpacityComposer.LEGACY_PROTOCOL_VERSION;
        }

        return 0;
    }

    /** Sends each recipient its effective state so per-user opacity overlays stay intact. */
    static void broadcastEffective(Room room, Collection<Integer> itemIds, int easing, int durationMs) {
        if (room == null || itemIds == null || itemIds.isEmpty()) {
            return;
        }

        List<Integer> payload = itemIds.stream().filter(id -> id != null && id > 0).sorted().toList();
        if (payload.isEmpty()) {
            return;
        }

        for (Habbo habbo : room.getHabbos()) {
            int protocolVersion = opacityProtocolVersion(habbo);
            if (protocolVersion == 0) {
                continue;
            }

            List<WiredOpacityState> effective = room.getWiredRuntime()
                    .effectiveOpacity(habbo.getHabboInfo().getId(), payload);
            habbo.getClient().sendResponse(
                    new WiredFurniOpacityComposer(protocolVersion, room.getId(), effective, easing, durationMs));
        }
    }
}
