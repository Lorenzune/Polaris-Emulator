package com.eu.habbo.messages.incoming.floorplaneditor;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class FloorPlanEditorSaveEventTest {

    @Test
    void acceptsMaximum64By64MapIncludingRowSeparators() {
        String row = "0".repeat(64);
        String map = String.join("\r", java.util.Collections.nCopies(64, row));

        assertEquals(64 * 64 + 63, map.length());
        assertTrue(FloorPlanEditorSaveEvent.isSerializedMapLengthWithinLimit(map.length(), 64));
        assertTrue(FloorPlanEditorSaveEvent.isFloorplanAreaWithinLimits(64, 64, 64, 4096));
    }

    @Test
    void rejectsMapsOutsideConfiguredAxisOrAreaLimits() {
        assertFalse(FloorPlanEditorSaveEvent.isSerializedMapLengthWithinLimit(4160, 64));
        assertFalse(FloorPlanEditorSaveEvent.isFloorplanAreaWithinLimits(64, 65, 64, 4096));
        assertFalse(FloorPlanEditorSaveEvent.isFloorplanAreaWithinLimits(64, 64, 64, 4095));
    }
}
