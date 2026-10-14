package com.eu.habbo.networking.gameserver.auth;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

import com.eu.habbo.Emulator;
import com.eu.habbo.core.ConfigurationManager;
import java.util.Random;
import javax.crypto.spec.SecretKeySpec;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

class NitroSecureAssetHexTest {
    @BeforeAll
    static void initializeHandlerWithoutServerConfiguration() {
        ConfigurationManager config = mock(ConfigurationManager.class);
        when(config.getValue("nitro.secure.master_key", "")).thenReturn("");
        try (MockedStatic<Emulator> emulator = mockStatic(Emulator.class)) {
            emulator.when(Emulator::getConfig).thenReturn(config);
            assertEquals("", NitroSecureAssetHandler.toHex(new byte[0]));
        }
    }

    @Test
    void preservesLegacyLowercaseEncodingForEveryByteValue() {
        byte[] bytes = new byte[256];
        StringBuilder expected = new StringBuilder();
        for (int i = 0; i < bytes.length; i++) {
            bytes[i] = (byte) i;
            expected.append(String.format("%02x", i));
        }

        assertEquals(expected.toString(), NitroSecureAssetHandler.toHex(bytes));
        assertArrayEquals(bytes, NitroSecureAssetHandler.fromHex(expected.toString()));
    }

    @Test
    void preservesLargeEncryptedPayloadRoundTrip() throws Exception {
        byte[] clear = new byte[1024 * 1024];
        new Random(7).nextBytes(clear);
        SecretKeySpec key = new SecretKeySpec(new byte[32], "AES");
        byte[] encrypted = NitroSecureAssetHandler.encrypt(key, clear);
        String wire = NitroSecureAssetHandler.toHex(encrypted);

        assertEquals(encrypted.length * 2, wire.length());
        assertArrayEquals(encrypted, NitroSecureAssetHandler.fromHex(wire));
        assertArrayEquals(clear, NitroSecureAssetHandler.decrypt(key, NitroSecureAssetHandler.fromHex(wire)));
    }
}
