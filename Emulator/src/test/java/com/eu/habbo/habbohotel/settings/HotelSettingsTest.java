package com.eu.habbo.habbohotel.settings;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class HotelSettingsTest {
    @Test
    void parsesAndClampsNonNegativeIntegerSettings() {
        assertEquals(25, HotelSettings.parseNonNegativeInt(" 25 ", 7, 100));
        assertEquals(0, HotelSettings.parseNonNegativeInt("-5", 7, 100));
        assertEquals(100, HotelSettings.parseNonNegativeInt("150", 7, 100));
        assertEquals(7, HotelSettings.parseNonNegativeInt("invalid", 7, 100));
        assertEquals(7, HotelSettings.parseNonNegativeInt(null, 7, 100));
    }
}
