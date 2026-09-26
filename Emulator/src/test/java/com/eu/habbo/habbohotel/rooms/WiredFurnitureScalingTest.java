package com.eu.habbo.habbohotel.rooms;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class WiredFurnitureScalingTest {

    @Test
    void snapsPercentagesToIntegerScaleLevels() {
        assertEquals(25, WiredFurnitureScaling.normalize(25));
        assertEquals(25, WiredFurnitureScaling.normalize(37));
        assertEquals(50, WiredFurnitureScaling.normalize(38));
        assertEquals(50, WiredFurnitureScaling.normalize(74));
        assertEquals(100, WiredFurnitureScaling.normalize(75));
        assertEquals(100, WiredFurnitureScaling.normalize(149));
        assertEquals(200, WiredFurnitureScaling.normalize(150));
        assertEquals(200, WiredFurnitureScaling.normalize(249));
        assertEquals(300, WiredFurnitureScaling.normalize(250));
        assertEquals(300, WiredFurnitureScaling.normalize(350));
    }

    @Test
    void runtimeOpacityStatesNeverKeepFractionalScaleValues() {
        assertEquals(200, new WiredOpacityState(1, false, 100, false, 185).scaling());
        assertEquals(50, new WiredOpacityState(1, false, 100, false, 45).scaling());
    }
}
