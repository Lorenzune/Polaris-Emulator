package com.eu.habbo.habbohotel.items.interactions.wired;

/** Opt-in SWF snapshot flags, with origins and quantifier in the common envelope. */
public final class WiredNativeSnapshotSettings {
    private WiredNativeSnapshotSettings() {}

    public static WiredSettings normalize(WiredSettings settings, boolean condition) {
        if (!settings.isNativeVariableFormat()) return settings;
        int[] flags = settings.getIntParams();
        int[] sources = settings.getFurniSourceTypes();
        if (flags.length != 4 || sources.length != 1)
            throw new IllegalArgumentException("Invalid snapshot field count");
        for (int flag : flags) {
            if (flag != 0 && flag != 1) throw new IllegalArgumentException("Invalid snapshot flag");
        }
        int source = sources[0];
        if (source != 0 && source != 100 && source != 200 && source != 201)
            throw new IllegalArgumentException("Invalid snapshot origin");
        int quantifier = settings.getQuantifier();
        if (quantifier < 0
                || quantifier > (condition ? 1 : 0)
                || !settings.getStringParam().isEmpty()
                || settings.getVariableTokens().length != 0
                || settings.getUserSourceTypes().length != 0
                || settings.getFurniIds2().length != 0
                || settings.isFilterExisting()
                || settings.isInvert()) throw new IllegalArgumentException("Unexpected snapshot fields");
        int[] translated = condition
                ? new int[] {flags[0], flags[1], flags[2], flags[3], source, quantifier}
                : new int[] {flags[0], flags[1], flags[2], flags[3], source};
        // Captured IDs are templates, including when the runtime origin is dynamic.
        return new WiredSettings(
                translated, "", settings.getFurniIds(), settings.getStuffTypeSelectionCode(), settings.getDelay());
    }
}
