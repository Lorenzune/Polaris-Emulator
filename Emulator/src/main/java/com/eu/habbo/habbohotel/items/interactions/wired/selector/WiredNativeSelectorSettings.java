package com.eu.habbo.habbohotel.items.interactions.wired.selector;

import com.eu.habbo.habbohotel.items.interactions.InteractionWiredEffect;
import com.eu.habbo.habbohotel.items.interactions.wired.WiredSettings;
import java.util.ArrayList;
import java.util.Arrays;

/** Converts the separate SWF fields to the persisted, backwards-compatible selector representation. */
public final class WiredNativeSelectorSettings {
    private WiredNativeSelectorSettings() {}

    public static WiredSettings normalize(InteractionWiredEffect effect, WiredSettings settings) {
        if (!settings.isNativeVariableFormat()) return settings;
        int[] p = settings.getIntParams();
        int filter = settings.isFilterExisting() ? 1 : 0, invert = settings.isInvert() ? 1 : 0;
        int source = settings.getFurniSourceTypes().length == 0 ? 100 : settings.getFurniSourceTypes()[0];
        int[] translated;
        String text = settings.getStringParam();
        switch (effect.getType()) {
            case FURNI_PICKS_SELECTOR, FURNI_SIGNAL_SELECTOR, USERS_SIGNAL_SELECTOR, USERS_BY_NAME_SELECTOR -> {
                require(p, 0);
                translated = new int[] {filter, invert};
                if (effect.getType().code == 52 && text.length() > 1000)
                    throw new IllegalArgumentException("Names exceed 1000 characters");
                if (effect.getType().code == 52) text = text.replace('\t', '\n');
            }
            case USERS_BY_TYPE_SELECTOR, USERS_TEAM_SELECTOR, USERS_HANDITEM_SELECTOR -> {
                require(p, 1);
                translated = new int[] {p[0], filter, invert};
            }
            case FURNI_BYTYPE_SELECTOR -> {
                require(p, 1);
                int localSource =
                        switch (source) {
                            case 100 -> 0;
                            case 201 -> 1;
                            case 0 -> 2;
                            case 200 -> 3;
                            default -> throw new IllegalArgumentException("Invalid type source");
                        };
                translated = new int[] {localSource, p[0], filter, invert};
            }
            case FURNI_ON_FURNI_SELECTOR -> {
                require(p, 1);
                validateSource(source);
                translated = new int[] {p[0], source, filter, invert};
            }
            case USERS_ON_FURNI_SELECTOR -> {
                require(p, 0);
                validateSource(source);
                translated = new int[] {source, filter, invert};
            }
            case FURNI_AREA_SELECTOR, USERS_AREA_SELECTOR -> {
                require(p, 4);
                translated = new int[] {p[0], p[1], p[2], p[3], filter, invert};
            }
            case FURNI_ALTITUDE_SELECTOR -> {
                require(p, 2);
                if (p[0] < 0 || p[0] > 8000 || p[1] < 0 || p[1] > 2)
                    throw new IllegalArgumentException("Invalid altitude");
                translated = new int[] {p[1], filter, invert};
                text = Double.toString(p[0] / 100D);
            }
            case USERS_GROUP_SELECTOR -> {
                require(p, 0);
                int group = text.isEmpty() ? 0 : Integer.parseInt(text);
                if (group < 0) throw new IllegalArgumentException("Invalid group");
                translated = new int[] {group == 0 ? 0 : 1, group, filter, invert};
                text = "";
            }
            case USERS_BY_ACTION_SELECTOR -> {
                require(p, 1);
                boolean specific = !text.isEmpty();
                int extra = specific ? Integer.parseInt(text.replace("dance ", "")) : 0;
                translated = new int[] {
                    p[0],
                    p[0] == 10 && specific ? 1 : 0,
                    p[0] == 10 ? extra : 0,
                    p[0] == 11 && specific ? 1 : 0,
                    p[0] == 11 ? extra : 1,
                    filter,
                    invert
                };
                text = "";
            }
            case REMOTE_SELECTOR -> {
                require(p, 2);
                if (p[0] < 0 || p[0] > 1 || p[1] < 0) throw new IllegalArgumentException("Invalid remote selection");
                translated = new int[] {filter, invert, p[0], p[1]};
            }
            case FURNI_NEIGHBORHOOD_SELECTOR, USERS_NEIGHBORHOOD_SELECTOR -> {
                if (p.length < 3
                        || p.length > 17
                        || p[0] < 0
                        || p[0] > 1
                        || Math.abs((long) p[1]) > 64
                        || Math.abs((long) p[2]) > 64) throw new IllegalArgumentException("Invalid neighborhood");
                int localSource;
                if (p[0] == 1) {
                    int user = settings.getUserSourceTypes().length == 0 ? 0 : settings.getUserSourceTypes()[0];
                    localSource = switch (user) {
                        case 0 -> 0;
                        case 201 -> 1;
                        case 11 -> 2;
                        case 200 -> 6;
                        default -> throw new IllegalArgumentException("Invalid neighborhood user source");
                    };
                } else {
                    localSource = switch (source) {
                        case 0 -> 3;
                        case 100 -> 4;
                        case 201 -> 5;
                        case 200 -> 7;
                        default -> throw new IllegalArgumentException("Invalid neighborhood furni source");
                    };
                }
                var offsets = spiralOffsets(Arrays.copyOfRange(p, 3, p.length));
                translated = new int[6 + offsets.size() * 2];
                translated[0] = localSource;
                translated[1] = filter;
                translated[2] = invert;
                translated[3] = p[1];
                translated[4] = p[2];
                translated[5] = offsets.size();
                for (int i = 0; i < offsets.size(); i++) {
                    translated[6 + i * 2] = offsets.get(i)[0];
                    translated[7 + i * 2] = offsets.get(i)[1];
                }
            }
            default -> throw new IllegalArgumentException("Unsupported native selector");
        }
        var result = new WiredSettings(
                translated,
                text,
                settings.getFurniIds(),
                settings.getFurniIds2(),
                settings.getVariableIds(),
                settings.getFurniSourceTypes(),
                settings.getUserSourceTypes(),
                settings.getStuffTypeSelectionCode(),
                0);
        result.setNativeVariableFormat(true);
        return result;
    }

    static ArrayList<int[]> spiralOffsets(int[] mask) {
        var result = new ArrayList<int[]>();
        int x = 0, y = 0, dx = 1, dy = 0, rank = 0;
        for (int length = 1; rank < 441; length++) {
            for (int turn = 0; turn < 2 && rank < 441; turn++) {
                for (int step = 0; step < length && rank < 441; step++, rank++) {
                    if (rank / 32 < mask.length && (mask[rank / 32] & (1 << (rank % 32))) != 0)
                        result.add(new int[] {x, y});
                    x += dx;
                    y += dy;
                }
                int previous = dx;
                dx = dy;
                dy = -previous;
            }
        }
        return result;
    }

    private static void require(int[] p, int count) {
        if (p.length != count) throw new IllegalArgumentException("Invalid native selector field count");
    }

    private static void validateSource(int source) {
        if (source != 0 && source != 100 && source != 200 && source != 201)
            throw new IllegalArgumentException("Invalid furni source");
    }
}
