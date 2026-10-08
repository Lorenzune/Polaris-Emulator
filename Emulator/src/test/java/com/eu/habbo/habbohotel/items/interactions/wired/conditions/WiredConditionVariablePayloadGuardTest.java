package com.eu.habbo.habbohotel.items.interactions.wired.conditions;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.eu.habbo.habbohotel.wired.core.WiredSourceUtil;
import org.junit.jupiter.api.Test;

class WiredConditionVariablePayloadGuardTest {
    @Test
    void variableAgeDurationsAreBounded() {
        assertEquals(-1, WiredConditionVariableAgeMatch.normalizeDurationAmount(-1));
        assertEquals(42, WiredConditionVariableAgeMatch.normalizeDurationAmount(42));
        assertEquals(Integer.MAX_VALUE, WiredConditionVariableAgeMatch.normalizeDurationAmount(Integer.MAX_VALUE));
        assertEquals(-1000L, WiredConditionVariableAgeMatch.durationToMillis(-1, 1));
        assertEquals(Long.MAX_VALUE, WiredConditionVariableAgeMatch.durationToMillis(Integer.MAX_VALUE, 7));
        assertEquals(Long.MIN_VALUE, WiredConditionVariableAgeMatch.durationToMillis(Integer.MIN_VALUE, 7));
        assertEquals(1_000L, WiredConditionVariableAgeMatch.durationToMillis(1, 1));
    }

    @Test
    void variableAgeModesFallBackToSafeDefaults() {
        assertEquals(0, WiredConditionVariableAgeMatch.normalizeTargetTypeExtended(999));
        assertEquals(1, WiredConditionVariableAgeMatch.normalizeTargetTypeExtended(1));
        assertEquals(0, WiredConditionVariableAgeMatch.normalizeCompareValue(99));
        assertEquals(1, WiredConditionVariableAgeMatch.normalizeCompareValue(1));
        assertEquals(0, WiredConditionVariableAgeMatch.normalizeComparison(99));
        assertEquals(2, WiredConditionVariableAgeMatch.normalizeComparison(2));
        assertEquals(1, WiredConditionVariableAgeMatch.normalizeDurationUnit(99));
        assertEquals(7, WiredConditionVariableAgeMatch.normalizeDurationUnit(7));
    }

    @Test
    void variableValueModesAndConstantsAreBounded() {
        assertEquals(0, WiredConditionVariableValueMatch.normalizeTargetTypeExtended(900));
        assertEquals(3, WiredConditionVariableValueMatch.normalizeTargetTypeExtended(3));
        assertEquals(0, WiredConditionVariableValueMatch.normalizeReferenceMode(5));
        assertEquals(1, WiredConditionVariableValueMatch.normalizeReferenceMode(1));
        assertEquals(
                WiredSourceUtil.SOURCE_TRIGGER, WiredConditionVariableValueMatch.normalizeReferenceFurniSource(-1));
        assertEquals(101, WiredConditionVariableValueMatch.normalizeReferenceFurniSource(101));
        assertEquals(2, WiredConditionVariableValueMatch.normalizeComparison(99));
        assertEquals(5, WiredConditionVariableValueMatch.normalizeComparison(5));
        assertEquals(
                Integer.MAX_VALUE, WiredConditionVariableValueMatch.normalizeReferenceConstantValue(Integer.MAX_VALUE));
        assertEquals(
                Integer.MIN_VALUE, WiredConditionVariableValueMatch.normalizeReferenceConstantValue(Integer.MIN_VALUE));
        assertEquals(0, WiredConditionVariableValueMatch.parseInteger("nope"));
        assertEquals(123, WiredConditionVariableValueMatch.parseInteger(" 123 "));
    }
}
