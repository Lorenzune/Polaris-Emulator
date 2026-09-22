package com.eu.habbo.habbohotel.rooms.voice;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonObject;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.junit.jupiter.api.Test;

class LiveKitTokenServiceTest {

    @Test
    void createsShortLivedRoomScopedParticipantToken() {
        long now = 1_800_000_000L;
        String token = LiveKitTokenService.createAccessToken("test-api-key", "test-api-secret", 42, 7, "Alice", now);
        String[] parts = token.split("\\.");
        JsonObject claims = LiveKitTokenService.readClaims(token);
        JsonObject grant = claims.getAsJsonObject("video");

        assertEquals(3, parts.length);
        assertEquals("test-api-key", claims.get("iss").getAsString());
        assertEquals("7", claims.get("sub").getAsString());
        assertEquals("Alice", claims.get("name").getAsString());
        assertEquals(now - 5, claims.get("nbf").getAsLong());
        assertEquals(now + 15 * 60, claims.get("exp").getAsLong());
        assertEquals(true, grant.get("roomJoin").getAsBoolean());
        assertEquals("habbo-42", grant.get("room").getAsString());
        assertEquals(true, grant.get("canPublish").getAsBoolean());
        assertEquals(true, grant.get("canSubscribe").getAsBoolean());
        assertEquals(false, grant.get("canPublishData").getAsBoolean());
        assertTrue(hasValidSignature(parts, "test-api-secret"));
    }

    @Test
    void createsReceiveOnlyMonitorTokenWithUniqueIdentity() {
        String sessionId = "3f4f9f89-14a8-4d97-a542-b03e880ace19";
        String token = LiveKitTokenService.createMonitorAccessToken(
                "test-api-key", "test-api-secret", 42, 7, sessionId, "Moderatore Alice");
        JsonObject claims = LiveKitTokenService.readClaims(token);
        JsonObject grant = claims.getAsJsonObject("video");

        assertEquals("mod-7-" + sessionId, claims.get("sub").getAsString());
        assertEquals("Moderatore Alice", claims.get("name").getAsString());
        assertEquals("habbo-42", grant.get("room").getAsString());
        assertEquals(false, grant.get("canPublish").getAsBoolean());
        assertEquals(true, grant.get("canSubscribe").getAsBoolean());
    }

    @Test
    void rejectsInvalidMonitorSessionId() {
        assertThrows(
                IllegalArgumentException.class,
                () -> LiveKitTokenService.createMonitorAccessToken(
                        "test-api-key", "test-api-secret", 42, 7, "arbitrary", "Moderatore Alice"));
    }

    @Test
    void requiresValidRoomAndSecureServerUrl() {
        assertEquals("habbo-42", LiveKitTokenService.roomName(42));
        assertThrows(IllegalArgumentException.class, () -> LiveKitTokenService.roomName(0));
        assertEquals("wss://voice.example.test", LiveKitTokenService.validateServerUrl("wss://voice.example.test"));
        assertThrows(
                IllegalArgumentException.class,
                () -> LiveKitTokenService.validateServerUrl("https://voice.example.test"));
        assertThrows(
                IllegalArgumentException.class,
                () -> LiveKitTokenService.validateServerUrl("wss://user:pass@voice.example.test"));
        assertThrows(
                IllegalArgumentException.class,
                () -> LiveKitTokenService.validateServerUrl("wss://voice.example.test/rtc"));
    }

    private static boolean hasValidSignature(String[] parts, String secret) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            byte[] expected = mac.doFinal((parts[0] + "." + parts[1]).getBytes(StandardCharsets.US_ASCII));
            byte[] actual = Base64.getUrlDecoder().decode(parts[2]);
            return java.security.MessageDigest.isEqual(expected, actual);
        } catch (Exception exception) {
            return false;
        }
    }
}
