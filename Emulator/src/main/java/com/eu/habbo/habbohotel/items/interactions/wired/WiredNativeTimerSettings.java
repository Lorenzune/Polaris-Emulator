package com.eu.habbo.habbohotel.items.interactions.wired;

import com.eu.habbo.habbohotel.items.interactions.InteractionWired;
import com.eu.habbo.habbohotel.items.interactions.wired.conditions.WiredConditionCounterTimeMatches;
import com.eu.habbo.habbohotel.items.interactions.wired.conditions.WiredConditionLessTimeElapsed;
import com.eu.habbo.habbohotel.items.interactions.wired.conditions.WiredConditionMoreTimeElapsed;
import com.eu.habbo.habbohotel.items.interactions.wired.effects.WiredEffectAdjustClock;
import com.eu.habbo.habbohotel.items.interactions.wired.effects.WiredEffectControlClock;
import com.eu.habbo.habbohotel.items.interactions.wired.effects.WiredEffectResetTimers;
import com.eu.habbo.habbohotel.items.interactions.wired.triggers.WiredTriggerAtSetTime;
import com.eu.habbo.habbohotel.items.interactions.wired.triggers.WiredTriggerClockCounter;
import com.eu.habbo.habbohotel.items.interactions.wired.triggers.WiredTriggerRepeater;
import com.eu.habbo.habbohotel.items.interactions.wired.triggers.WiredTriggerRepeaterLong;
import com.eu.habbo.habbohotel.items.interactions.wired.triggers.WiredTriggerRepeaterShort;

/** Opt-in SWF timer/clock fields, translated to the existing persisted Polaris format. */
public final class WiredNativeTimerSettings {
    private WiredNativeTimerSettings() {}

    public static WiredSettings normalize(InteractionWired item, WiredSettings settings) {
        if (!settings.isNativeVariableFormat()) return settings;
        int[] p = settings.getIntParams();
        int[] translated;
        boolean clock = item instanceof WiredEffectAdjustClock
                || item instanceof WiredEffectControlClock
                || item instanceof WiredTriggerClockCounter
                || item instanceof WiredConditionCounterTimeMatches;
        if (!settings.getStringParam().isEmpty()
                || settings.getVariableTokens().length != 0
                || settings.getUserSourceTypes().length != 0
                || settings.getFurniIds2().length != 0
                || settings.isFilterExisting()
                || settings.isInvert()) throw new IllegalArgumentException("Unexpected timer fields");
        if (!clock && (settings.getFurniIds().length != 0 || settings.getFurniSourceTypes().length != 0))
            throw new IllegalArgumentException("Timer does not select furni");
        if (!(item instanceof WiredConditionCounterTimeMatches) && settings.getQuantifier() != 0)
            throw new IllegalArgumentException("Timer does not support quantifier");
        if (clock) {
            int[] sources = settings.getFurniSourceTypes();
            if (sources.length != 1
                    || (sources[0] != 0 && sources[0] != 100 && sources[0] != 200 && sources[0] != 201)
                    || (item instanceof WiredTriggerClockCounter && sources[0] != 100 && sources[0] != 200))
                throw new IllegalArgumentException("Invalid clock origin");
            int source = sources[0];
            if (item instanceof WiredEffectControlClock) {
                require(p, 1);
                range(p[0], 0, 4);
                translated = new int[] {p[0], source};
            } else {
                require(p, item instanceof WiredTriggerClockCounter ? 3 : 4);
                range(p[0], 0, 59);
                range(p[1], 0, 99);
                range(p[2], 0, 1);
                int halfSeconds = p[0] * 2 + p[2];
                if (item instanceof WiredTriggerClockCounter) translated = new int[] {p[1], halfSeconds, source};
                else {
                    range(p[3], 0, 2);
                    if (item instanceof WiredEffectAdjustClock)
                        translated = new int[] {p[3], source, p[1], halfSeconds};
                    else {
                        range(settings.getQuantifier(), 0, 1);
                        translated = new int[] {p[3], p[1], halfSeconds, source, settings.getQuantifier()};
                    }
                }
            }
        } else if (item instanceof WiredEffectResetTimers) {
            require(p, 0);
            translated = p;
        } else {
            require(p, 1);
            if (item instanceof WiredConditionMoreTimeElapsed || item instanceof WiredConditionLessTimeElapsed) {
                range(p[0], 2, 1201);
                translated = new int[] {p[0] - 1};
            } else {
                int max = item instanceof WiredTriggerRepeaterShort
                        ? 10
                        : item instanceof WiredTriggerAtSetTime ? 1200 : 120;
                if (!(item instanceof WiredTriggerAtSetTime
                        || item instanceof WiredTriggerRepeater
                        || item instanceof WiredTriggerRepeaterLong))
                    throw new IllegalArgumentException("Unsupported timer");
                range(p[0], 1, max);
                translated = p;
            }
        }
        return new WiredSettings(
                translated, "", settings.getFurniIds(), settings.getStuffTypeSelectionCode(), settings.getDelay());
    }

    private static void require(int[] p, int count) {
        if (p.length != count) throw new IllegalArgumentException("Invalid timer field count");
    }

    private static void range(int value, int min, int max) {
        if (value < min || value > max) throw new IllegalArgumentException("Timer field out of range");
    }
}
