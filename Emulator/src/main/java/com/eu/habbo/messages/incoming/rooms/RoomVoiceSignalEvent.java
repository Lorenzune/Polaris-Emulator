package com.eu.habbo.messages.incoming.rooms;

import com.eu.habbo.Emulator;
import com.eu.habbo.habbohotel.permissions.Permission;
import com.eu.habbo.habbohotel.rooms.Room;
import com.eu.habbo.habbohotel.rooms.voice.LiveKitTokenService;
import com.eu.habbo.habbohotel.users.Habbo;
import com.eu.habbo.messages.incoming.MessageHandler;
import com.eu.habbo.messages.incoming.navigator.SearchRoomsEvent;
import com.eu.habbo.messages.outgoing.rooms.RoomVoiceSignalComposer;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class RoomVoiceSignalEvent extends MessageHandler {
    private static final Logger LOGGER = LoggerFactory.getLogger(RoomVoiceSignalEvent.class);
    private static final int MAX_SIGNAL_LENGTH = 16_384;
    private static final int MAX_SDP_LENGTH = 12_288;
    private static final int MAX_ICE_CANDIDATE_LENGTH = 1_024;
    private static final int MAX_ICE_SDP_MID_LENGTH = 128;
    private static final int MAX_MODERATION_DURATION_SECONDS = Integer.MAX_VALUE;
    private static final int MAX_INBOUND_SIGNALS_PER_SECOND = 60;
    private static final int MAX_DIRECT_SIGNALS_PER_SECOND = 20;
    private static final long MIN_BROADCAST_INTERVAL_MILLIS = 500;
    private static final long RATE_STATE_TTL_MILLIS = 120_000;
    private static final long RATE_STATE_SWEEP_INTERVAL_MILLIS = 60_000;
    private static final int RATE_STATE_SWEEP_THRESHOLD = 512;
    private static final Map<Integer, VoiceSignalRateState> SIGNAL_RATE_STATES = new ConcurrentHashMap<>();
    private static final AtomicLong LAST_RATE_STATE_SWEEP_MILLIS = new AtomicLong();

    @Override
    public int getRatelimit() {
        // The schema-aware limiter below permits short ICE bursts without allowing a sustained flood.
        return 1;
    }

    @Override
    public void handle() throws Exception {
        int roomId = this.packet.readInt();
        int targetUserId = this.packet.readInt();
        String signalData = this.packet.readString();

        if (signalData == null || signalData.isBlank() || signalData.length() > MAX_SIGNAL_LENGTH) {
            return;
        }

        Habbo sender = this.client.getHabbo();
        if (sender == null || sender.getHabboInfo() == null || sender.getHabboStats() == null) {
            return;
        }

        int senderUserId = sender.getHabboInfo().getId();
        long now = System.currentTimeMillis();
        if (!allowInboundAttempt(senderUserId, now)) {
            return;
        }

        JsonObject signal = parseSignal(signalData);
        if (signal == null) {
            return;
        }

        String signalType = getString(signal, "type");
        Integer signalTargetUserId = getInteger(signal, "targetUserId");
        if (signalType == null || signalTargetUserId == null || signalTargetUserId != targetUserId) {
            return;
        }

        if ("livekit_monitor_auth_request".equals(signalType)) {
            handleLiveKitMonitorTokenRequest(signal, targetUserId, sender, roomId, senderUserId, now);
            return;
        }

        if ("livekit_monitor_rooms_request".equals(signalType)) {
            handleLiveKitMonitorRoomsRequest(signal, targetUserId, sender, roomId, senderUserId, now);
            return;
        }

        if ("livekit_monitor_stop".equals(signalType)) {
            handleLiveKitMonitorStop(signal, targetUserId, roomId, senderUserId, now);
            return;
        }

        Room currentRoom = sender.getHabboInfo().getCurrentRoom();
        if (currentRoom == null || currentRoom.getId() != roomId || !currentRoom.isVocalChatEnabled()) {
            return;
        }

        int vocalBannedUntil = sender.getHabboStats().getVocalBannedUntil();
        if (vocalBannedUntil > 0 && vocalBannedUntil > Emulator.getIntUnixTimestamp()) {
            int remainingSeconds = vocalBannedUntil == Integer.MAX_VALUE
                    ? (86400 * 3650)
                    : (vocalBannedUntil - Emulator.getIntUnixTimestamp());
            removeVoiceUser(currentRoom, sender.getHabboInfo().getId());
            this.client.sendResponse(new RoomVoiceSignalComposer(
                    0,
                    "{\"type\":\"mod_action\",\"targetUserId\":"
                            + sender.getHabboInfo().getId()
                            + ",\"payload\":{\"action\":\"disconnect\",\"durationSeconds\":" + remainingSeconds
                            + "}}"));
            return;
        }

        if ("livekit_token_request".equals(signalType)) {
            handleLiveKitTokenRequest(signal, targetUserId, sender, currentRoom, senderUserId, now);
            return;
        }

        JsonObject normalizedSignal = normalizeSignal(signal, signalType, targetUserId, sender, currentRoom);
        if (normalizedSignal == null) {
            return;
        }

        if (!allowSignal(senderUserId, targetUserId, now)) {
            return;
        }

        String normalizedSignalData = normalizedSignal.toString();

        if (targetUserId > 0 && targetUserId != senderUserId) {
            Habbo target = currentRoom.getHabbo(targetUserId);
            if (target != null && target.getClient() != null) {
                target.getClient().sendResponse(new RoomVoiceSignalComposer(senderUserId, normalizedSignalData));
            }
        } else if (targetUserId == 0) {
            // Broadcast to everyone in room except sender
            for (Habbo occupant : currentRoom.getHabbos()) {
                if (occupant != null
                        && occupant.getHabboInfo() != null
                        && occupant.getHabboInfo().getId() != senderUserId) {
                    if (occupant.getClient() != null) {
                        occupant.getClient()
                                .sendResponse(new RoomVoiceSignalComposer(senderUserId, normalizedSignalData));
                    }
                }
            }
        }
    }

    private void handleLiveKitTokenRequest(
            JsonObject signal, int targetUserId, Habbo sender, Room room, int senderUserId, long now) {
        JsonObject payload = getObject(signal, "payload");
        String requestId = payload == null ? null : getString(payload, "requestId");
        if (targetUserId != senderUserId
                || requestId == null
                || requestId.length() > 64
                || !allowSignal(senderUserId, senderUserId, now)) {
            return;
        }

        try {
            String serverUrl =
                    LiveKitTokenService.validateServerUrl(Emulator.getConfig().getValue("voice.chat.livekit.url", ""));
            String apiKey = Emulator.getConfig().getValue("voice.chat.livekit.api.key", "");
            String apiSecret = Emulator.getConfig().getValue("voice.chat.livekit.api.secret", "");
            String roomName = LiveKitTokenService.roomName(room.getId());
            String token = LiveKitTokenService.createAccessToken(
                    apiKey,
                    apiSecret,
                    room.getId(),
                    senderUserId,
                    sender.getHabboInfo().getUsername());

            JsonObject response = new JsonObject();
            response.addProperty("type", "livekit_auth");
            response.addProperty("targetUserId", 0);
            response.addProperty("requestId", requestId);
            JsonObject responsePayload = new JsonObject();
            responsePayload.addProperty("url", serverUrl);
            responsePayload.addProperty("roomName", roomName);
            responsePayload.addProperty("token", token);
            response.add("payload", responsePayload);

            this.client.sendResponse(new RoomVoiceSignalComposer(0, response.toString()));
        } catch (RuntimeException exception) {
            LOGGER.warn(
                    "Unable to issue LiveKit token for voice room {} ({}).",
                    room.getId(),
                    exception.getClass().getSimpleName());
            JsonObject response = new JsonObject();
            response.addProperty("type", "livekit_auth_error");
            response.addProperty("targetUserId", 0);
            response.addProperty("requestId", requestId);
            JsonObject responsePayload = new JsonObject();
            responsePayload.addProperty("message", "Chat vocale temporaneamente non disponibile. Riprova tra poco.");
            response.add("payload", responsePayload);
            this.client.sendResponse(new RoomVoiceSignalComposer(0, response.toString()));
        }
    }

    private void handleLiveKitMonitorTokenRequest(
            JsonObject signal, int targetUserId, Habbo moderator, int roomId, int moderatorUserId, long now) {
        JsonObject payload = getObject(signal, "payload");
        String requestId = payload == null ? null : getString(payload, "requestId");
        if (targetUserId != 0
                || !canUseVoiceSurveillance(moderator)
                || !isMonitorSessionId(requestId)
                || !allowSignal(moderatorUserId, moderatorUserId, now)) {
            return;
        }

        Room room = Emulator.getGameEnvironment().getRoomManager().getRoom(roomId);
        if (room == null || !room.isVocalChatEnabled() || room.getVoiceUsers().isEmpty()) {
            sendMonitorAuthError(requestId, "La stanza non ha utenti vocali attivi.");
            return;
        }

        try {
            String serverUrl =
                    LiveKitTokenService.validateServerUrl(Emulator.getConfig().getValue("voice.chat.livekit.url", ""));
            String apiKey = Emulator.getConfig().getValue("voice.chat.livekit.api.key", "");
            String apiSecret = Emulator.getConfig().getValue("voice.chat.livekit.api.secret", "");
            String token = LiveKitTokenService.createMonitorAccessToken(
                    apiKey,
                    apiSecret,
                    roomId,
                    moderatorUserId,
                    requestId,
                    "Moderatore " + moderator.getHabboInfo().getUsername());
            beginVoiceMonitorSession(requestId, moderatorUserId, roomId);

            JsonObject response = new JsonObject();
            response.addProperty("type", "livekit_monitor_auth");
            response.addProperty("targetUserId", 0);
            response.addProperty("requestId", requestId);
            JsonObject responsePayload = new JsonObject();
            responsePayload.addProperty("url", serverUrl);
            responsePayload.addProperty("roomName", LiveKitTokenService.roomName(roomId));
            responsePayload.addProperty("roomId", roomId);
            responsePayload.addProperty("token", token);
            response.add("payload", responsePayload);
            this.client.sendResponse(new RoomVoiceSignalComposer(0, response.toString()));
            LOGGER.info("Voice monitor started: moderator={} room={}", moderatorUserId, roomId);
        } catch (java.sql.SQLException exception) {
            LOGGER.warn(
                    "Unable to audit LiveKit monitor access for moderator={} room={}",
                    moderatorUserId,
                    roomId,
                    exception);
            sendMonitorAuthError(requestId, "Impossibile registrare la sessione di moderazione.");
        } catch (RuntimeException exception) {
            LOGGER.warn(
                    "Unable to issue LiveKit monitor token for room {} ({})",
                    roomId,
                    exception.getClass().getSimpleName());
            sendMonitorAuthError(requestId, "Monitor vocale temporaneamente non disponibile.");
        }
    }

    private void handleLiveKitMonitorRoomsRequest(
            JsonObject signal, int targetUserId, Habbo moderator, int roomId, int moderatorUserId, long now) {
        JsonObject payload = getObject(signal, "payload");
        String requestId = payload == null ? null : getString(payload, "requestId");
        if (roomId != 0
                || targetUserId != 0
                || !canUseVoiceSurveillance(moderator)
                || requestId == null
                || requestId.length() > 64
                || !allowSignal(moderatorUserId, 0, now)) {
            return;
        }

        ArrayList<Room> activeVoiceRooms = new ArrayList<>();
        for (Room room : Emulator.getGameEnvironment().getRoomManager().getActiveRooms()) {
            if (room != null
                    && room.isVocalChatEnabled()
                    && !room.getVoiceUsers().isEmpty()) {
                activeVoiceRooms.add(room);
            }
        }
        activeVoiceRooms.sort(
                Comparator.comparingInt((Room room) -> room.getVoiceUsers().size())
                        .reversed()
                        .thenComparing(Room::getName, String.CASE_INSENSITIVE_ORDER)
                        .thenComparingInt(Room::getId));

        JsonArray rooms = new JsonArray();
        for (Room room : activeVoiceRooms) {
            JsonObject roomData = new JsonObject();
            roomData.addProperty("roomId", room.getId());
            roomData.addProperty("name", room.getName() == null ? "" : room.getName());
            roomData.addProperty("voiceUserCount", room.getVoiceUsers().size());
            rooms.add(roomData);
        }

        JsonObject response = new JsonObject();
        response.addProperty("type", "livekit_monitor_rooms");
        response.addProperty("targetUserId", 0);
        response.addProperty("requestId", requestId);
        JsonObject responsePayload = new JsonObject();
        responsePayload.add("rooms", rooms);
        response.add("payload", responsePayload);
        this.client.sendResponse(new RoomVoiceSignalComposer(0, response.toString()));
    }

    private void handleLiveKitMonitorStop(
            JsonObject signal, int targetUserId, int roomId, int moderatorUserId, long now) {
        JsonObject payload = getObject(signal, "payload");
        String requestId = payload == null ? null : getString(payload, "requestId");
        // Ending an already-issued session must remain possible after a staff permission is revoked.
        // Ownership is enforced by the moderator_user_id condition in the UPDATE below.
        if (targetUserId != 0
                || !isMonitorSessionId(requestId)
                || !allowSignal(moderatorUserId, moderatorUserId, now)) {
            return;
        }

        try (java.sql.Connection connection =
                        Emulator.getDatabase().getDataSource().getConnection();
                java.sql.PreparedStatement statement = connection.prepareStatement(
                        "UPDATE room_voice_monitor_sessions SET stopped_at = CURRENT_TIMESTAMP "
                                + "WHERE session_id = ? AND moderator_user_id = ? AND room_id = ? AND stopped_at IS NULL")) {
            statement.setString(1, requestId);
            statement.setInt(2, moderatorUserId);
            statement.setInt(3, roomId);
            if (statement.executeUpdate() > 0) {
                LOGGER.info("Voice monitor stopped: moderator={} room={}", moderatorUserId, roomId);
            }
        } catch (java.sql.SQLException exception) {
            LOGGER.warn(
                    "Unable to close voice monitor audit session for moderator={} room={}",
                    moderatorUserId,
                    roomId,
                    exception);
        }
    }

    private void sendMonitorAuthError(String requestId, String message) {
        JsonObject response = new JsonObject();
        response.addProperty("type", "livekit_monitor_auth_error");
        response.addProperty("targetUserId", 0);
        response.addProperty("requestId", requestId);
        JsonObject responsePayload = new JsonObject();
        responsePayload.addProperty("message", message);
        response.add("payload", responsePayload);
        this.client.sendResponse(new RoomVoiceSignalComposer(0, response.toString()));
    }

    private static boolean canUseVoiceSurveillance(Habbo habbo) {
        return habbo != null
                && habbo.getHabboInfo() != null
                && habbo.getHabboInfo().getRank().getId() >= 7
                && habbo.hasPermission(Permission.ACC_SUPPORTTOOL)
                && habbo.hasPermission(Permission.ACC_VOICE_SURVEILLANCE);
    }

    private static boolean isMonitorSessionId(String sessionId) {
        if (sessionId == null) return false;
        try {
            return UUID.fromString(sessionId).toString().equalsIgnoreCase(sessionId);
        } catch (IllegalArgumentException ignored) {
            return false;
        }
    }

    private static void beginVoiceMonitorSession(String sessionId, int moderatorUserId, int roomId)
            throws java.sql.SQLException {
        try (java.sql.Connection connection =
                        Emulator.getDatabase().getDataSource().getConnection();
                java.sql.PreparedStatement statement = connection.prepareStatement(
                        "INSERT INTO room_voice_monitor_sessions (session_id, moderator_user_id, room_id) VALUES (?, ?, ?)")) {
            statement.setString(1, sessionId);
            statement.setInt(2, moderatorUserId);
            statement.setInt(3, roomId);
            statement.executeUpdate();
        }
    }

    private static boolean allowInboundAttempt(int senderUserId, long now) {
        sweepRateStates(now);

        VoiceSignalRateState rateState =
                SIGNAL_RATE_STATES.computeIfAbsent(senderUserId, ignored -> new VoiceSignalRateState(now));
        synchronized (rateState) {
            rateState.lastSeenMillis = now;

            if (now - rateState.inboundWindowStartMillis >= 1_000) {
                rateState.inboundWindowStartMillis = now;
                rateState.inboundSignalCount = 0;
            }

            if (rateState.inboundSignalCount >= MAX_INBOUND_SIGNALS_PER_SECOND) {
                return false;
            }

            rateState.inboundSignalCount++;
            return true;
        }
    }

    private static boolean allowSignal(int senderUserId, int targetUserId, long now) {
        sweepRateStates(now);

        VoiceSignalRateState rateState =
                SIGNAL_RATE_STATES.computeIfAbsent(senderUserId, ignored -> new VoiceSignalRateState(now));
        synchronized (rateState) {
            rateState.lastSeenMillis = now;

            if (targetUserId == 0) {
                if (now - rateState.lastBroadcastMillis < MIN_BROADCAST_INTERVAL_MILLIS) {
                    return false;
                }

                rateState.lastBroadcastMillis = now;
                return true;
            }

            if (now - rateState.directWindowStartMillis >= 1_000) {
                rateState.directWindowStartMillis = now;
                rateState.directSignalCount = 0;
            }

            if (rateState.directSignalCount >= MAX_DIRECT_SIGNALS_PER_SECOND) {
                return false;
            }

            rateState.directSignalCount++;
            return true;
        }
    }

    private static void sweepRateStates(long now) {
        if (SIGNAL_RATE_STATES.size() < RATE_STATE_SWEEP_THRESHOLD) {
            return;
        }

        long previousSweep = LAST_RATE_STATE_SWEEP_MILLIS.get();
        if (now - previousSweep < RATE_STATE_SWEEP_INTERVAL_MILLIS
                || !LAST_RATE_STATE_SWEEP_MILLIS.compareAndSet(previousSweep, now)) {
            return;
        }

        SIGNAL_RATE_STATES.entrySet().removeIf(entry -> now - entry.getValue().lastSeenMillis > RATE_STATE_TTL_MILLIS);
    }

    private static JsonObject parseSignal(String signalData) {
        try {
            JsonElement root = JsonParser.parseString(signalData);

            return root.isJsonObject() ? root.getAsJsonObject() : null;
        } catch (RuntimeException ignored) {
            return null;
        }
    }

    private static JsonObject normalizeSignal(
            JsonObject signal, String signalType, int targetUserId, Habbo sender, Room room) {
        JsonObject normalized = new JsonObject();
        normalized.addProperty("type", signalType);
        normalized.addProperty("targetUserId", targetUserId);

        switch (signalType) {
            case "ready":
                if (targetUserId == 0 && !signal.has("payload")) {
                    if (room.getVoiceUsers().add(sender.getHabboInfo().getId())) {
                        SearchRoomsEvent.invalidateCachedResults();
                    }
                    return normalized;
                }
                return null;
            case "listen":
                // A listener receives voice audio but does not publish a microphone.
                return targetUserId == 0 && !signal.has("payload") ? normalized : null;
            case "leave":
                removeVoiceUser(room, sender.getHabboInfo().getId());
                return targetUserId == 0 ? normalized : null;
            case "state":
                return normalizeStateSignal(signal, normalized, targetUserId);
            case "offer":
            case "answer":
                return normalizeSessionDescriptionSignal(signal, normalized, signalType, targetUserId);
            case "ice":
                return normalizeIceSignal(signal, normalized, targetUserId);
            case "mod_action":
                return normalizeModerationSignal(signal, normalized, targetUserId, sender, room);
            default:
                return null;
        }
    }

    private static JsonObject normalizeStateSignal(JsonObject signal, JsonObject normalized, int targetUserId) {
        if (targetUserId < 0) {
            return null;
        }

        JsonObject payload = getObject(signal, "payload");
        if (payload == null) {
            return null;
        }

        Boolean speaking = getBoolean(payload, "speaking");
        Boolean muted = getBoolean(payload, "muted");
        Boolean deafened = getBoolean(payload, "deafened");
        if (speaking == null || muted == null || deafened == null) {
            return null;
        }

        JsonObject normalizedPayload = new JsonObject();
        normalizedPayload.addProperty("speaking", speaking);
        normalizedPayload.addProperty("muted", muted);
        normalizedPayload.addProperty("deafened", deafened);
        normalized.add("payload", normalizedPayload);

        return normalized;
    }

    private static JsonObject normalizeSessionDescriptionSignal(
            JsonObject signal, JsonObject normalized, String signalType, int targetUserId) {
        if (targetUserId <= 0) {
            return null;
        }

        JsonObject payload = getObject(signal, "payload");
        String descriptionType = payload == null ? null : getString(payload, "type");
        String sdp = payload == null ? null : getString(payload, "sdp");
        if (!signalType.equals(descriptionType) || sdp == null || sdp.isBlank() || sdp.length() > MAX_SDP_LENGTH) {
            return null;
        }

        JsonObject normalizedPayload = new JsonObject();
        normalizedPayload.addProperty("type", descriptionType);
        normalizedPayload.addProperty("sdp", sdp);
        normalized.add("payload", normalizedPayload);

        return normalized;
    }

    private static JsonObject normalizeIceSignal(JsonObject signal, JsonObject normalized, int targetUserId) {
        if (targetUserId <= 0) {
            return null;
        }

        JsonObject payload = getObject(signal, "payload");
        String candidate = payload == null ? null : getString(payload, "candidate");
        String sdpMid = payload == null ? null : getNullableString(payload, "sdpMid");
        Integer sdpMLineIndex = payload == null ? null : getInteger(payload, "sdpMLineIndex");
        String usernameFragment = payload == null ? null : getNullableString(payload, "usernameFragment");
        if (candidate == null
                || candidate.isBlank()
                || candidate.length() > MAX_ICE_CANDIDATE_LENGTH
                || (sdpMid != null && sdpMid.length() > MAX_ICE_SDP_MID_LENGTH)
                || (sdpMid == null && sdpMLineIndex == null)
                || (sdpMLineIndex != null && (sdpMLineIndex < 0 || sdpMLineIndex > 255))
                || (usernameFragment != null && usernameFragment.length() > MAX_ICE_SDP_MID_LENGTH)) {
            return null;
        }

        JsonObject normalizedPayload = new JsonObject();
        normalizedPayload.addProperty("candidate", candidate);
        if (sdpMid == null) {
            normalizedPayload.add("sdpMid", null);
        } else {
            normalizedPayload.addProperty("sdpMid", sdpMid);
        }
        if (sdpMLineIndex == null) {
            normalizedPayload.add("sdpMLineIndex", null);
        } else {
            normalizedPayload.addProperty("sdpMLineIndex", sdpMLineIndex);
        }
        if (usernameFragment != null) {
            normalizedPayload.addProperty("usernameFragment", usernameFragment);
        }
        normalized.add("payload", normalizedPayload);

        return normalized;
    }

    private static JsonObject normalizeModerationSignal(
            JsonObject signal, JsonObject normalized, int targetUserId, Habbo sender, Room room) {
        if (targetUserId <= 0 || !hasModerationPermission(sender, room)) {
            return null;
        }

        JsonObject payload = getObject(signal, "payload");
        String action = payload == null ? null : getString(payload, "action");
        Integer durationSeconds = payload == null ? null : getInteger(payload, "durationSeconds");
        if ((!("mute".equals(action) || "disconnect".equals(action) || "unban".equals(action)))
                || durationSeconds == null
                || durationSeconds < 0
                || durationSeconds > MAX_MODERATION_DURATION_SECONDS) {
            return null;
        }

        JsonObject normalizedPayload = new JsonObject();
        normalizedPayload.addProperty("action", action);
        normalizedPayload.addProperty("durationSeconds", durationSeconds);
        normalized.add("payload", normalizedPayload);

        if ("disconnect".equals(action)) {
            removeVoiceUser(room, targetUserId);
        }

        return normalized;
    }

    private static boolean hasModerationPermission(Habbo sender, Room room) {
        return sender.hasPermission(com.eu.habbo.habbohotel.permissions.Permission.ACC_ANYROOMOWNER)
                || sender.hasPermission(com.eu.habbo.habbohotel.permissions.Permission.ACC_SUPPORTTOOL)
                || sender.hasPermission("acc_anyroomowner")
                || room.isOwner(sender);
    }

    private static void removeVoiceUser(Room room, int userId) {
        if (room.getVoiceUsers().remove(userId)) {
            SearchRoomsEvent.invalidateCachedResults();
        }
    }

    private static JsonObject getObject(JsonObject object, String property) {
        JsonElement value = object.get(property);

        return value != null && value.isJsonObject() ? value.getAsJsonObject() : null;
    }

    private static String getString(JsonObject object, String property) {
        JsonElement value = object.get(property);

        return value != null
                        && value.isJsonPrimitive()
                        && value.getAsJsonPrimitive().isString()
                ? value.getAsString()
                : null;
    }

    private static String getNullableString(JsonObject object, String property) {
        JsonElement value = object.get(property);
        if (value == null || value.isJsonNull()) {
            return null;
        }

        return value.isJsonPrimitive() && value.getAsJsonPrimitive().isString() ? value.getAsString() : null;
    }

    private static Boolean getBoolean(JsonObject object, String property) {
        JsonElement value = object.get(property);

        return value != null
                        && value.isJsonPrimitive()
                        && value.getAsJsonPrimitive().isBoolean()
                ? value.getAsBoolean()
                : null;
    }

    private static Integer getInteger(JsonObject object, String property) {
        JsonElement value = object.get(property);
        if (value == null
                || !value.isJsonPrimitive()
                || !value.getAsJsonPrimitive().isNumber()) {
            return null;
        }

        try {
            double number = value.getAsDouble();
            if (!Double.isFinite(number)
                    || number != Math.rint(number)
                    || number < Integer.MIN_VALUE
                    || number > Integer.MAX_VALUE) {
                return null;
            }

            return (int) number;
        } catch (RuntimeException ignored) {
            return null;
        }
    }

    private static class VoiceSignalRateState {
        private long inboundWindowStartMillis;
        private int inboundSignalCount;
        private long directWindowStartMillis;
        private int directSignalCount;
        private long lastBroadcastMillis;
        private volatile long lastSeenMillis;

        private VoiceSignalRateState(long now) {
            this.inboundWindowStartMillis = now;
            this.directWindowStartMillis = now;
            this.lastSeenMillis = now;
        }
    }
}
