package com.eu.habbo.habbohotel.rooms.voice;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Objects;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

/** Creates short-lived LiveKit participant tokens without exposing the API secret to clients. */
public final class LiveKitTokenService {
    private static final long TOKEN_TTL_SECONDS = 15 * 60;

    private LiveKitTokenService() {}

    public static String roomName(int roomId) {
        if (roomId <= 0) {
            throw new IllegalArgumentException("roomId must be positive");
        }
        return "habbo-" + roomId;
    }

    public static String createAccessToken(
            String apiKey, String apiSecret, int roomId, int userId, String displayName) {
        return createAccessToken(apiKey, apiSecret, roomId, userId, displayName, System.currentTimeMillis() / 1_000L);
    }

    public static String createMonitorAccessToken(
            String apiKey, String apiSecret, int roomId, int moderatorUserId, String sessionId, String displayName) {
        if (moderatorUserId <= 0 || sessionId == null || !sessionId.matches("[a-fA-F0-9-]{36}")) {
            throw new IllegalArgumentException("Invalid moderator or monitoring session");
        }
        return createToken(
                apiKey,
                apiSecret,
                roomId,
                "mod-" + moderatorUserId + "-" + sessionId,
                displayName,
                false,
                true,
                System.currentTimeMillis() / 1_000L);
    }

    static String createAccessToken(
            String apiKey, String apiSecret, int roomId, int userId, String displayName, long nowEpochSeconds) {
        if (apiKey == null || apiKey.isBlank() || apiSecret == null || apiSecret.isBlank()) {
            throw new IllegalArgumentException("LiveKit API credentials are required");
        }
        if (userId <= 0 || nowEpochSeconds <= 0) {
            throw new IllegalArgumentException("userId and issue time must be positive");
        }

        return createToken(
                apiKey, apiSecret, roomId, Integer.toString(userId), displayName, true, true, nowEpochSeconds);
    }

    private static String createToken(
            String apiKey,
            String apiSecret,
            int roomId,
            String identity,
            String displayName,
            boolean canPublish,
            boolean canSubscribe,
            long nowEpochSeconds) {
        if (apiKey == null || apiKey.isBlank() || apiSecret == null || apiSecret.isBlank()) {
            throw new IllegalArgumentException("LiveKit API credentials are required");
        }
        if (roomId <= 0 || identity == null || identity.isBlank() || nowEpochSeconds <= 0) {
            throw new IllegalArgumentException("roomId, identity and issue time must be positive");
        }

        JsonObject header = new JsonObject();
        header.addProperty("alg", "HS256");
        header.addProperty("typ", "JWT");

        JsonObject grants = new JsonObject();
        grants.addProperty("roomJoin", true);
        grants.addProperty("room", roomName(roomId));
        grants.addProperty("canPublish", canPublish);
        grants.addProperty("canSubscribe", canSubscribe);
        grants.addProperty("canPublishData", false);

        JsonObject claims = new JsonObject();
        claims.addProperty("iss", apiKey);
        claims.addProperty("sub", identity);
        claims.addProperty("name", Objects.requireNonNullElse(displayName, "Habbo " + identity));
        claims.addProperty("nbf", Math.max(0, nowEpochSeconds - 5));
        claims.addProperty("iat", nowEpochSeconds);
        claims.addProperty("exp", nowEpochSeconds + TOKEN_TTL_SECONDS);
        claims.add("video", grants);

        Base64.Encoder encoder = Base64.getUrlEncoder().withoutPadding();
        String unsignedToken = encoder.encodeToString(header.toString().getBytes(StandardCharsets.UTF_8))
                + "."
                + encoder.encodeToString(claims.toString().getBytes(StandardCharsets.UTF_8));
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(apiSecret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            return unsignedToken + "."
                    + encoder.encodeToString(mac.doFinal(unsignedToken.getBytes(StandardCharsets.US_ASCII)));
        } catch (Exception exception) {
            throw new IllegalStateException("Unable to sign LiveKit access token", exception);
        }
    }

    public static String validateServerUrl(String serverUrl) {
        if (serverUrl == null || serverUrl.isBlank()) {
            throw new IllegalArgumentException("LiveKit server URL is required");
        }

        try {
            java.net.URI uri = java.net.URI.create(serverUrl.trim());
            String scheme = uri.getScheme();
            if ((!"wss".equalsIgnoreCase(scheme) && !"ws".equalsIgnoreCase(scheme))
                    || uri.getHost() == null
                    || uri.getUserInfo() != null
                    || uri.getFragment() != null
                    || uri.getQuery() != null
                    || (uri.getPath() != null && !uri.getPath().isEmpty() && !"/".equals(uri.getPath()))) {
                throw new IllegalArgumentException("LiveKit URL must be a base ws:// or wss:// URL with a host");
            }
            return uri.toString();
        } catch (IllegalArgumentException exception) {
            throw new IllegalArgumentException("Invalid LiveKit server URL", exception);
        }
    }

    static JsonObject readClaims(String token) {
        String[] parts = token == null ? new String[0] : token.split("\\.");
        if (parts.length != 3) {
            throw new IllegalArgumentException("Invalid JWT");
        }
        byte[] payload = Base64.getUrlDecoder().decode(parts[1]);
        return JsonParser.parseString(new String(payload, StandardCharsets.UTF_8))
                .getAsJsonObject();
    }
}
