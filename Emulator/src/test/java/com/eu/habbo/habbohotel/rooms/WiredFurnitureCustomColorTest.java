package com.eu.habbo.habbohotel.rooms;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

class WiredFurnitureCustomColorTest {
    @Test
    void normalizesLayerColorsAndGradients() {
        String config = "{\"v\":1,\"layers\":{\"2\":{\"mode\":\"gradient\",\"start\":\"#FF0000\",\"end\":\"#00FFAA\",\"angle\":45},\"0\":{\"mode\":\"solid\",\"color\":\"#AABBCC\"}}}";

        assertEquals("{\"v\":1,\"layers\":{\"0\":{\"mode\":\"solid\",\"color\":\"#aabbcc\",\"intensity\":100,\"fill\":false},\"2\":{\"mode\":\"gradient\",\"start\":\"#ff0000\",\"end\":\"#00ffaa\",\"angle\":45,\"intensity\":100,\"fill\":false}}}",
                WiredFurnitureCustomColor.normalize(config));
    }

    @Test
    void normalizesCustomIntensityAndFill() {
        String config = "{\"v\":1,\"layers\":{\"0\":{\"mode\":\"solid\",\"color\":\"#AABBCC\",\"intensity\":35,\"fill\":true}}}";

        assertEquals("{\"v\":1,\"layers\":{\"0\":{\"mode\":\"solid\",\"color\":\"#aabbcc\",\"intensity\":35,\"fill\":true}}}",
                WiredFurnitureCustomColor.normalize(config));
        assertThrows(IllegalArgumentException.class,
                () -> WiredFurnitureCustomColor.normalize("{\"v\":1,\"layers\":{\"0\":{\"mode\":\"solid\",\"color\":\"#ffffff\",\"intensity\":101,\"fill\":false}}}"));
    }

    @Test
    void rejectsInvalidColorsAnglesLayerIdsAndOversizedPayloads() {
        assertThrows(IllegalArgumentException.class,
                () -> WiredFurnitureCustomColor.normalize("{\"v\":1,\"layers\":{\"26\":{\"mode\":\"solid\",\"color\":\"#ffffff\"}}}"));
        assertThrows(IllegalArgumentException.class,
                () -> WiredFurnitureCustomColor.normalize("{\"v\":1,\"layers\":{\"0\":{\"mode\":\"solid\",\"color\":\"red\"}}}"));
        assertThrows(IllegalArgumentException.class,
                () -> WiredFurnitureCustomColor.normalize("{\"v\":1,\"layers\":{\"0\":{\"mode\":\"gradient\",\"start\":\"#ffffff\",\"end\":\"#000000\",\"angle\":360}}}"));
        assertThrows(IllegalArgumentException.class, () -> WiredFurnitureCustomColor.normalize("x".repeat(4097)));
    }

    @Test
    void emptyConfigurationClearsOverride() {
        assertEquals(null, WiredFurnitureCustomColor.normalize(null));
        assertEquals(null, WiredFurnitureCustomColor.normalize("{\"v\":1,\"layers\":{}}"));
    }
}
