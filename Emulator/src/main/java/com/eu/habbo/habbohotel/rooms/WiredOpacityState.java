package com.eu.habbo.habbohotel.rooms;

/** Immutable, session-only effective visual state for one room furniture item. */
public record WiredOpacityState(
        int itemId, boolean wallItem, int opacity, boolean clickThrough, int scaling, String customColor) {
    public WiredOpacityState(int itemId, boolean wallItem, int opacity, boolean clickThrough, int scaling) {
        this(itemId, wallItem, opacity, clickThrough, scaling, null);
    }

    public WiredOpacityState(int itemId, boolean wallItem, int opacity, boolean clickThrough) {
        this(itemId, wallItem, opacity, clickThrough, 100, null);
    }

    public WiredOpacityState {
        opacity = Math.max(0, Math.min(100, opacity));
        scaling = WiredFurnitureScaling.normalize(scaling);
    }
}
