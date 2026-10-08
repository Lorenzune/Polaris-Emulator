package com.eu.habbo.habbohotel.items.interactions.wired.effects;

/** Bit positions are zero-based within a signed 32-bit Wired value. */
final class WiredVariableBitOperations {
    private WiredVariableBitOperations() {}

    static boolean supports(int operation) {
        return operation >= 110 && operation <= 122;
    }

    static int apply(int operation, int value, Integer position) {
        if (operation == 110) return Integer.bitCount(value);
        if (position == null || position < 0 || position > 31) return value;
        return switch (operation) {
            case 115 -> (value >>> position) & 1;
            case 116 -> value | (1 << position);
            case 117 -> value & ~(1 << position);
            case 118 -> value ^ (1 << position);
            case 111, 112, 119, 120 -> find(value, position, 1, operation == 112 || operation == 120, operation >= 119);
            case 113, 114, 121, 122 ->
                find(value, position, -1, operation == 114 || operation == 122, operation >= 119);
            default -> value;
        };
    }

    private static int find(int value, int position, int direction, boolean high, boolean exclusive) {
        for (int bit = position + (exclusive ? direction : 0); bit >= 0 && bit < 32; bit += direction) {
            if (((value >>> bit) & 1) == (high ? 1 : 0)) return bit;
        }
        return -1;
    }
}
