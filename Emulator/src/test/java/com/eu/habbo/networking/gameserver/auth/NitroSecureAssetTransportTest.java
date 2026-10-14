package com.eu.habbo.networking.gameserver.auth;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

import com.eu.habbo.Emulator;
import com.eu.habbo.core.ConfigurationManager;
import com.google.gson.JsonObject;
import io.netty.channel.embedded.EmbeddedChannel;
import io.netty.handler.codec.http.DefaultFullHttpRequest;
import io.netty.handler.codec.http.FullHttpResponse;
import io.netty.handler.codec.http.HttpMethod;
import io.netty.handler.codec.http.HttpVersion;
import java.io.ByteArrayInputStream;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.security.KeyPairGenerator;
import java.util.Base64;
import java.util.zip.GZIPInputStream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.MockedStatic;

class NitroSecureAssetTransportTest {
    @TempDir
    Path root;

    @Test
    void assetsAreCompressedBeforeEncryptionAndRefreshAfterAnEdit() throws Exception {
        String json = "{\n  \"items\": [" + "{\"id\":1,\"name\":\"Caffè 🦆\"},".repeat(10000) + "{}]\n}";
        Path asset = root.resolve("FurnitureData.json");
        Files.writeString(asset, json);
        ConfigurationManager config = mock(ConfigurationManager.class);
        when(config.getValue("nitro.secure.master_key", "")).thenReturn("");
        when(config.getValue("nitro.secure.gamedata.root", "")).thenReturn(root.toString());
        when(config.getBoolean("nitro.secure.assets.enabled", true)).thenReturn(true);
        when(config.getInt(anyString(), anyInt())).thenAnswer(invocation -> invocation.getArgument(1));

        try (MockedStatic<Emulator> emulator = mockStatic(Emulator.class)) {
            emulator.when(Emulator::getConfig).thenReturn(config);
            var generator = KeyPairGenerator.getInstance("EC");
            generator.initialize(256);
            byte[] publicKey = generator.generateKeyPair().getPublic().getEncoded();
            var key = NitroSecureAssetHandler.deriveSessionKey(publicKey);

            byte[] binary = request(publicKey);
            JsonObject fixture = new JsonObject();
            fixture.addProperty("key", Base64.getEncoder().encodeToString(key.getEncoded()));
            fixture.addProperty("body", Base64.getEncoder().encodeToString(binary));
            fixture.addProperty("clear", json);
            Files.writeString(Path.of("target/secure-asset-transport-fixture.json"), fixture.toString());
            assertEquals(json, unzip(NitroSecureAssetHandler.decrypt(key, binary)));
            assertTrue(
                    binary.length < json.getBytes(StandardCharsets.UTF_8).length / 5,
                    "Compression must happen before encryption");
            assertEquals(json, unzip(NitroSecureAssetHandler.decrypt(key, request(publicKey))));

            long previousModified = Files.getLastModifiedTime(asset).toMillis();
            Files.writeString(asset, "{\"edited\":true}");
            Files.setLastModifiedTime(asset, FileTime.fromMillis(previousModified + 2000));
            assertEquals("{\"edited\":true}", unzip(NitroSecureAssetHandler.decrypt(key, request(publicKey))));
            when(config.getInt("nitro.secure.gamedata.max_file_bytes", 64 * 1024 * 1024))
                    .thenReturn(4);
            assertEquals("File too large.", new String(request(publicKey, 400), StandardCharsets.UTF_8));
        }
    }

    private byte[] request(byte[] publicKey) {
        return request(publicKey, 200);
    }

    private byte[] request(byte[] publicKey, int expectedStatus) {
        EmbeddedChannel channel = new EmbeddedChannel(new NitroSecureAssetHandler());
        try {
            var request = new DefaultFullHttpRequest(
                    HttpVersion.HTTP_1_1,
                    HttpMethod.GET,
                    "/nitro-sec/file?kind=gamedata&file=FurnitureData.json&key="
                            + URLEncoder.encode(Base64.getEncoder().encodeToString(publicKey), StandardCharsets.UTF_8));
            channel.writeInbound(request);
            FullHttpResponse response = channel.readOutbound();
            try {
                assertEquals(expectedStatus, response.status().code());
                if (expectedStatus == 200) {
                    assertEquals("1", response.headers().get("X-Nitro-Sec"));
                    assertTrue(response.headers().get("Server-Timing").contains("asset;dur="));
                    assertEquals(
                            "application/vnd.nitro.secure-gzip",
                            response.headers().get("Content-Type"));
                }
                byte[] body = new byte[response.content().readableBytes()];
                response.content().readBytes(body);
                return body;
            } finally {
                response.release();
            }
        } finally {
            channel.finishAndReleaseAll();
        }
    }

    private static String unzip(byte[] bytes) throws Exception {
        try (GZIPInputStream gzip = new GZIPInputStream(new ByteArrayInputStream(bytes))) {
            return new String(gzip.readAllBytes(), StandardCharsets.UTF_8);
        }
    }
}
