package com.eu.habbo.messages.incoming.rooms;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.eu.habbo.habbohotel.rooms.Room;
import com.eu.habbo.habbohotel.users.Habbo;
import com.eu.habbo.messages.incoming.MessageHandler;
import com.eu.habbo.messages.outgoing.rooms.RoomVoiceSignalComposer;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

public class RoomVoiceSignalEvent extends MessageHandler {
    private static final int MAX_SIGNAL_LENGTH = 16_384;
    private static final int MAX_SDP_LENGTH = 12_288;
    private static final int MAX_ICE_CANDIDATE_LENGTH = 1_024;
    private static final int MAX_ICE_SDP_MID_LENGTH = 128;
    private static final int MAX_MODERATION_DURATION_SECONDS = 86_400;
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
        if (sender == null || sender.getHabboInfo() == null) {
            return;
        }

        Room currentRoom = sender.getHabboInfo().getCurrentRoom();
        if (currentRoom == null || currentRoom.getId() != roomId || !currentRoom.isVocalChatEnabled()) {
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
                if (occupant != null && occupant.getHabboInfo() != null && occupant.getHabboInfo().getId() != senderUserId) {
                    if (occupant.getClient() != null) {
                        occupant.getClient().sendResponse(new RoomVoiceSignalComposer(senderUserId, normalizedSignalData));
                    }
                }
            }
        }
    }

    private static boolean allowInboundAttempt(int senderUserId, long now) {
        sweepRateStates(now);

        VoiceSignalRateState rateState = SIGNAL_RATE_STATES.computeIfAbsent(senderUserId, ignored -> new VoiceSignalRateState(now));
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

        VoiceSignalRateState rateState = SIGNAL_RATE_STATES.computeIfAbsent(senderUserId, ignored -> new VoiceSignalRateState(now));
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
        if (now - previousSweep < RATE_STATE_SWEEP_INTERVAL_MILLIS || !LAST_RATE_STATE_SWEEP_MILLIS.compareAndSet(previousSweep, now)) {
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

    private static JsonObject normalizeSignal(JsonObject signal, String signalType, int targetUserId, Habbo sender, Room room) {
        JsonObject normalized = new JsonObject();
        normalized.addProperty("type", signalType);
        normalized.addProperty("targetUserId", targetUserId);

        switch (signalType) {
            case "ready":
                return targetUserId == 0 && !signal.has("payload") ? normalized : null;
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

    private static JsonObject normalizeSessionDescriptionSignal(JsonObject signal, JsonObject normalized, String signalType, int targetUserId) {
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
        if (candidate == null || candidate.isBlank() || candidate.length() > MAX_ICE_CANDIDATE_LENGTH
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

    private static JsonObject normalizeModerationSignal(JsonObject signal, JsonObject normalized, int targetUserId, Habbo sender, Room room) {
        if (targetUserId <= 0 || !hasModerationPermission(sender, room)) {
            return null;
        }

        JsonObject payload = getObject(signal, "payload");
        String action = payload == null ? null : getString(payload, "action");
        Integer durationSeconds = payload == null ? null : getInteger(payload, "durationSeconds");
        if ((!("mute".equals(action) || "disconnect".equals(action)))
                || durationSeconds == null || durationSeconds < 0 || durationSeconds > MAX_MODERATION_DURATION_SECONDS) {
            return null;
        }

        JsonObject normalizedPayload = new JsonObject();
        normalizedPayload.addProperty("action", action);
        normalizedPayload.addProperty("durationSeconds", durationSeconds);
        normalized.add("payload", normalizedPayload);

        return normalized;
    }

    private static boolean hasModerationPermission(Habbo sender, Room room) {
        return sender.hasPermission(com.eu.habbo.habbohotel.permissions.Permission.ACC_ANYROOMOWNER)
                || sender.hasPermission(com.eu.habbo.habbohotel.permissions.Permission.ACC_SUPPORTTOOL)
                || sender.hasPermission("acc_anyroomowner")
                || room.isOwner(sender)
                || room.hasRights(sender);
    }

    private static JsonObject getObject(JsonObject object, String property) {
        JsonElement value = object.get(property);

        return value != null && value.isJsonObject() ? value.getAsJsonObject() : null;
    }

    private static String getString(JsonObject object, String property) {
        JsonElement value = object.get(property);

        return value != null && value.isJsonPrimitive() && value.getAsJsonPrimitive().isString() ? value.getAsString() : null;
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

        return value != null && value.isJsonPrimitive() && value.getAsJsonPrimitive().isBoolean() ? value.getAsBoolean() : null;
    }

    private static Integer getInteger(JsonObject object, String property) {
        JsonElement value = object.get(property);
        if (value == null || !value.isJsonPrimitive() || !value.getAsJsonPrimitive().isNumber()) {
            return null;
        }

        try {
            double number = value.getAsDouble();
            if (!Double.isFinite(number) || number != Math.rint(number) || number < Integer.MIN_VALUE || number > Integer.MAX_VALUE) {
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
