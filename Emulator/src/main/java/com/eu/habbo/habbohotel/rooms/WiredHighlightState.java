package com.eu.habbo.habbohotel.rooms;

public record WiredHighlightState(int category, int id, int style, int color) {
    public WiredHighlightState {
        if ((category != 10 && category != 20 && category != 100)
                || id < 0
                || (category != 100 && id == 0)
                || style < 0
                || style > 24
                || color < 0
                || color > 0xffffff) throw new IllegalArgumentException("Invalid highlight");
    }
}
