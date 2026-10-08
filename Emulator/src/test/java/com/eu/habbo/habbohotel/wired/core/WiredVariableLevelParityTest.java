package com.eu.habbo.habbohotel.wired.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTimeoutPreemptively;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;

import com.eu.habbo.habbohotel.items.Item;
import com.eu.habbo.habbohotel.items.interactions.wired.WiredSettings;
import com.eu.habbo.habbohotel.items.interactions.wired.extra.WiredExtraVariableLevelUpSystem;
import java.time.Duration;
import org.junit.jupiter.api.Test;

class WiredVariableLevelParityTest {
    private static WiredExtraVariableLevelUpSystem config(int[] fields, String text) {
        var box = new WiredExtraVariableLevelUpSystem(1, 2, mock(Item.class), "", 0, 0);
        var settings = new WiredSettings(fields, text, new int[0], 0);
        settings.setNativeVariableFormat(true);
        assertTrue(box.saveData(settings, null));
        return box;
    }

    private static void value(WiredExtraVariableLevelUpSystem config, int xp, int sub, int expected) {
        assertEquals(expected, WiredVariableLevelSystemSupport.getDerivedValue(config, sub, xp));
    }

    @Test
    void linearProgressUsesXpForThisLevelAndZeroProgressAtMaximum() {
        var box = config(new int[] {255, 1, 100, 5}, "");
        value(box, 250, 0, 3);
        value(box, 250, 2, 50);
        value(box, 250, 3, 50);
        value(box, 250, 4, 100);
        value(box, 250, 5, 50);
        value(box, 1000, 0, 5);
        value(box, 1000, 1, 500);
        value(box, 1000, 2, 0);
        value(box, 1000, 3, 0);
        value(box, 1000, 4, 0);
        value(box, 1000, 5, 0);
    }

    @Test
    void exponentialThresholdMatchesTheSwfClosedFormulaWithoutRoundingEveryIncrement() {
        var box = config(new int[] {255, 2, 3, 20, 4}, "");
        value(box, 9, 0, 3);
        value(box, 9, 2, 3);
        value(box, 9, 3, 75);
        value(box, 9, 4, 4);
        value(box, 9, 5, 1);
        value(box, 10, 6, 1);
        value(box, 999, 1, 10);
    }

    @Test
    void manualInterpolationTruncatesAndDoesNotExpandBillionsOfLevels() {
        var box = config(new int[] {255, 0}, "5=101\r10=501");
        value(box, 76, 0, 4);
        value(box, 76, 2, 1);
        value(box, 76, 4, 26);
        value(box, 76, 5, 25);
        var huge = config(new int[] {255, 0}, "1000000000=1000000000");
        assertTimeoutPreemptively(Duration.ofSeconds(2), () -> value(huge, 400, 0, 401));
    }
}
