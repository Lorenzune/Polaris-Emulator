package com.eu.habbo.habbohotel.items.interactions.wired.effects;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class WiredVariableBitOperationsTest {
    @Test
    void signBitAndBitCountWorkOnTheFullSignedRange() {
        assertEquals(32, WiredVariableBitOperations.apply(110, -1, null));
        assertEquals(1, WiredVariableBitOperations.apply(115, Integer.MIN_VALUE, 31));
        assertEquals(Integer.MIN_VALUE, WiredVariableBitOperations.apply(116, 0, 31));
        assertEquals(Integer.MAX_VALUE, WiredVariableBitOperations.apply(117, -1, 31));
        assertEquals(-1, WiredVariableBitOperations.apply(118, Integer.MAX_VALUE, 31));
    }

    @Test
    void searchesRespectDirectionInclusivityAndNoMatch() {
        int bits = 0b10100;
        assertEquals(2, WiredVariableBitOperations.apply(112, bits, 2));
        assertEquals(4, WiredVariableBitOperations.apply(120, bits, 2));
        assertEquals(2, WiredVariableBitOperations.apply(122, bits, 4));
        assertEquals(4, WiredVariableBitOperations.apply(114, bits, 4));
        assertEquals(3, WiredVariableBitOperations.apply(111, bits, 2));
        assertEquals(1, WiredVariableBitOperations.apply(113, bits, 2));
        assertEquals(3, WiredVariableBitOperations.apply(119, bits, 2));
        assertEquals(3, WiredVariableBitOperations.apply(121, bits, 4));
        assertEquals(-1, WiredVariableBitOperations.apply(120, bits, 4));
        assertEquals(-1, WiredVariableBitOperations.apply(122, bits, 2));
        assertEquals(-1, WiredVariableBitOperations.apply(111, -1, 0));
        assertEquals(-1, WiredVariableBitOperations.apply(114, 0, 31));
    }

    @Test
    void invalidBitPositionsDoNotWrapAroundToAnUnrelatedBit() {
        assertEquals(5, WiredVariableBitOperations.apply(116, 5, 32));
        assertEquals(5, WiredVariableBitOperations.apply(117, 5, -1));
    }
}
