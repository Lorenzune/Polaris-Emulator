package com.eu.habbo.habbohotel.rooms;

/** Supported integer scale levels for pixel-art furniture sprites. */
public final class WiredFurnitureScaling {

    public static final int DEFAULT = 100;

    private WiredFurnitureScaling() {}

    /** Snaps a percentage to the nearest supported pixel-art scale level; midpoint ties round up. */
    public static int normalize(int percentage) {
        if (percentage < 38) return 25;
        if (percentage < 75) return 50;
        if (percentage < 150) return 100;
        if (percentage < 250) return 200;
        return 300;
    }
}
