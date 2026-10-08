package com.eu.habbo.habbohotel.items.interactions.wired;

import com.eu.habbo.Emulator;
import com.eu.habbo.habbohotel.items.interactions.InteractionWired;
import com.eu.habbo.habbohotel.items.interactions.wired.conditions.WiredConditionHasVariable;
import com.eu.habbo.habbohotel.items.interactions.wired.conditions.WiredConditionVariableAgeMatch;
import com.eu.habbo.habbohotel.items.interactions.wired.conditions.WiredConditionVariableValueMatch;
import com.eu.habbo.habbohotel.items.interactions.wired.effects.WiredEffectChangeVariableValue;
import com.eu.habbo.habbohotel.items.interactions.wired.effects.WiredEffectGiveVariable;
import com.eu.habbo.habbohotel.items.interactions.wired.effects.WiredEffectRemoveVariable;
import com.eu.habbo.habbohotel.items.interactions.wired.extra.WiredExtraContextVariable;
import com.eu.habbo.habbohotel.items.interactions.wired.extra.WiredExtraFilterFurni;
import com.eu.habbo.habbohotel.items.interactions.wired.extra.WiredExtraFilterUser;
import com.eu.habbo.habbohotel.items.interactions.wired.extra.WiredExtraFurniVariable;
import com.eu.habbo.habbohotel.items.interactions.wired.extra.WiredExtraRoomVariable;
import com.eu.habbo.habbohotel.items.interactions.wired.extra.WiredExtraUserVariable;
import com.eu.habbo.habbohotel.items.interactions.wired.extra.WiredExtraVariableEcho;
import com.eu.habbo.habbohotel.items.interactions.wired.extra.WiredExtraVariableFilterBase;
import com.eu.habbo.habbohotel.items.interactions.wired.extra.WiredExtraVariableLevelUpSystem;
import com.eu.habbo.habbohotel.items.interactions.wired.extra.WiredExtraVariableReference;
import com.eu.habbo.habbohotel.items.interactions.wired.extra.WiredVariableReferenceSupport;
import com.eu.habbo.habbohotel.items.interactions.wired.selector.WiredEffectVariableSelectorBase;
import com.eu.habbo.habbohotel.items.interactions.wired.triggers.WiredTriggerVariableChanged;
import com.eu.habbo.habbohotel.wired.core.WiredManager;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.stream.Collectors;

/** SWF field semantics at the save boundary; existing stored configurations and legacy clients stay readable. */
public final class WiredNativeVariableSettings {
    private WiredNativeVariableSettings() {}

    public static WiredSettings normalize(InteractionWired item, WiredSettings settings) {
        if (!settings.isNativeVariableFormat()) return settings;
        int[] p = settings.getIntParams();
        int u = source(settings.getUserSourceTypes(), 0, 0);
        int f = source(settings.getFurniSourceTypes(), 0, 0);
        int u2 = source(settings.getUserSourceTypes(), 1, 0);
        int f2 = source(settings.getFurniSourceTypes(), 1, 0);
        int[] translated;
        String text = settings.getStringParam();
        if (item instanceof WiredExtraVariableFilterBase) {
            boolean countOnly = item instanceof WiredExtraFilterFurni || item instanceof WiredExtraFilterUser;
            require(p, countOnly ? 3 : 4);
            int mode = p[countOnly ? 1 : 2];
            if (mode < 0 || mode > 1 || (mode == 0 && (p[0] < 1 || p[0] > 1000)))
                throw new IllegalArgumentException("Invalid filter amount");
            int sort = countOnly ? 0 : p[1];
            if (sort < 0 || sort > 5) throw new IllegalArgumentException("Invalid filter ordering");
            int referenceTarget = target(p[countOnly ? 2 : 3]);
            if (mode == 1
                    && ((referenceTarget == 0 && u != 0 && u != 11 && u != 200 && u != 201)
                            || (referenceTarget == 1 && f != 0 && f != 100 && f != 200 && f != 201)))
                throw new IllegalArgumentException("Invalid filter reference source");
            translated = new int[] {sort, mode, p[0], referenceTarget, u, f == 100 ? 101 : f};
            text = (countOnly ? "" : token(settings, 0)) + "\t" + (mode == 1 ? token(settings, countOnly ? 0 : 1) : "");
        } else if (item instanceof WiredExtraVariableLevelUpSystem) {
            if (p.length < 2 || p[0] < 0 || p[0] > 255 || p[1] < 0 || p[1] > 2)
                throw new IllegalArgumentException("Invalid level configuration");
            require(p, p[1] == 0 ? 2 : p[1] == 1 ? 4 : 5);
            var config = new LinkedHashMap<String, Object>();
            config.put("mode", p[1] == 0 ? 3 : p[1]);
            var subvariables = new java.util.ArrayList<Integer>();
            for (int index = 0; index < 8; index++) if ((p[0] & (1 << index)) != 0) subvariables.add(index);
            config.put("subvariables", subvariables);
            if (p[1] != 0) {
                if (p[2] < 1
                        || p[2] > 100000
                        || p[3] < (p[1] == 1 ? 2 : 1)
                        || p[3] > 100000
                        || (p[1] == 2 && (p[4] < 2 || p[4] > 100000)))
                    throw new IllegalArgumentException("Level configuration out of range");
                if (p[1] == 1) {
                    config.put("stepSize", p[2]);
                    config.put("maxLevel", p[3]);
                } else {
                    config.put("firstLevelXp", p[2]);
                    config.put("increaseFactor", p[3]);
                    config.put("maxLevel", p[4]);
                }
            } else {
                if (text.length() > 1000) throw new IllegalArgumentException("Manual level text too long");
                config.put("interpolationText", text.replace("\r", "\n"));
                int previousLevel = 1, previousXp = 0;
                for (String line :
                        text.replace("\r\n", "\n").replace("\r", "\n").split("\n")) {
                    if (!line.contains("=")) continue;
                    String[] fields = line.split("=", 2);
                    int level = Integer.parseInt(fields[0].trim()), xp = Integer.parseInt(fields[1].trim());
                    if (level <= previousLevel || xp <= previousXp)
                        throw new IllegalArgumentException("Manual levels and XP must increase");
                    previousLevel = level;
                    previousXp = xp;
                }
            }
            translated = new int[0];
            text = WiredManager.getGson().toJson(config);
        } else if (item instanceof WiredTriggerVariableChanged) {
            require(p, 5);
            String reference = token(settings, 0);
            int separator = reference.indexOf('/');
            if (separator < 1) throw new IllegalArgumentException("Missing trigger variable target");
            int target = target(Integer.parseInt(reference.substring(0, separator)));
            if (target == 2 || (p[3] & ~7) != 0) throw new IllegalArgumentException("Invalid variable trigger fields");
            translated = new int[] {target, p[0], p[1], p[3] & 1, (p[3] >> 1) & 1, (p[3] >> 2) & 1, p[2], p[4]};
            text = reference.substring(separator + 1);
        } else if (item instanceof WiredEffectGiveVariable) {
            require(p, 4);
            if (p[0] == -10) throw new IllegalArgumentException("Global variables cannot be assigned");
            translated = new int[] {target(p[0]), p[3], value(p[1], p[2]), u, f};
            text = customId(token(settings, 0));
        } else if (item instanceof WiredEffectRemoveVariable) {
            require(p, 1);
            if (p[0] == -10) throw new IllegalArgumentException("Global variables cannot be removed");
            translated = new int[] {target(p[0]), u, f};
            text = customId(token(settings, 0));
        } else if (item instanceof WiredEffectChangeVariableValue || item instanceof WiredConditionVariableValueMatch) {
            require(p, 6);
            int[] fields = {target(p[0]), p[1], p[2], value(p[3], p[4]), target(p[5]), u, f, u2, f2};
            if (item instanceof WiredConditionVariableValueMatch) {
                if (p[1] < 0 || p[1] > 5) throw new IllegalArgumentException("Invalid variable comparison");
                fields[1] = new int[] {4, 2, 0, 3, 5, 1}[p[1]];
                translated = Arrays.copyOf(fields, 10);
                translated[9] = settings.getQuantifier();
            } else translated = fields;
            text = token(settings, 0) + "\t" + token(settings, 1) + "\t" + ids(settings.getFurniIds2());
        } else if (item instanceof WiredConditionVariableAgeMatch) {
            require(p, 6);
            translated = new int[] {target(p[0]), p[1], p[2], value(p[3], p[4]), p[5], u, f, settings.getQuantifier()};
            text = token(settings, 0);
        } else if (item instanceof WiredConditionHasVariable) {
            require(p, 1);
            translated = new int[] {target(p[0]), u, f, settings.getQuantifier()};
            text = token(settings, 0);
        } else if (item instanceof WiredEffectVariableSelectorBase) {
            require(p, 5);
            if (p[0] < 0 || p[0] > 5) throw new IllegalArgumentException("Invalid variable comparison");
            if (p[1] < 0 || p[1] > 2) throw new IllegalArgumentException("Invalid variable selector mode");
            translated = new int[] {
                p[1] == 0 ? 0 : 1,
                new int[] {4, 2, 0, 3, 5, 1}[p[0]],
                p[1] == 2 ? 1 : 0,
                value(p[2], p[3]),
                target(p[4]),
                u,
                f,
                settings.isFilterExisting() ? 1 : 0,
                settings.isInvert() ? 1 : 0
            };
            text = token(settings, 0) + "\t" + token(settings, 1);
        } else if (item instanceof WiredExtraVariableReference) {
            require(p, 1);
            int id = Integer.parseInt(customId(token(settings, 0)));
            var room = Emulator.getGameEnvironment().getRoomManager().getRoom(item.getRoomId());
            var definition = WiredVariableReferenceSupport.loadRoomOptions(room).stream()
                    .flatMap(option -> option.getVariables().stream())
                    .filter(option -> option.getItemId() == id)
                    .findFirst()
                    .orElseThrow(() -> new IllegalArgumentException("Unavailable shared variable"));
            var config = new LinkedHashMap<String, Object>();
            config.put("variableName", text);
            config.put("readOnly", p[0] != 0);
            config.put("sourceVariableItemId", id);
            config.put("sourceRoomId", definition.getRoomId());
            config.put("sourceTargetType", definition.getTargetType());
            text = WiredManager.getGson().toJson(config);
            translated = new int[0];
        } else if (item instanceof WiredExtraVariableEcho) {
            require(p, 0);
            String reference = token(settings, 0);
            int separator = reference.indexOf('/');
            if (separator < 1) throw new IllegalArgumentException("Missing echo reference target");
            var config = new LinkedHashMap<String, Object>();
            config.put("variableName", text);
            config.put("sourceTargetType", target(Integer.parseInt(reference.substring(0, separator))));
            config.put("sourceVariableToken", reference.substring(separator + 1));
            text = WiredManager.getGson().toJson(config);
            translated = new int[0];
        } else if (item instanceof WiredExtraUserVariable) {
            require(p, 2);
            translated = new int[] {p[1], p[0]};
        } else if (item instanceof WiredExtraFurniVariable) {
            require(p, 2);
            translated = p;
        } else if (item instanceof WiredExtraRoomVariable || item instanceof WiredExtraContextVariable) {
            require(p, 1);
            translated = p;
        } else {
            throw new IllegalArgumentException("Native variable settings unsupported for this interaction");
        }
        WiredSettings result = new WiredSettings(
                translated,
                text,
                settings.getFurniIds(),
                settings.getFurniIds2(),
                settings.getVariableIds(),
                settings.getFurniSourceTypes(),
                settings.getUserSourceTypes(),
                settings.getStuffTypeSelectionCode(),
                settings.getDelay());
        return result;
    }

    private static void require(int[] p, int count) {
        if (p.length != count) throw new IllegalArgumentException("Invalid native variable field count");
    }

    private static int source(int[] sources, int index, int fallback) {
        return sources.length > index ? sources[index] : fallback;
    }

    private static int target(int nativeTarget) {
        return switch (nativeTarget) {
            case 0 -> 1;
            case 1 -> 0;
            case -10 -> 3;
            case -20 -> 2;
            default -> throw new IllegalArgumentException("Invalid native variable target");
        };
    }

    private static int value(int high, int low) {
        if (high != (low < 0 ? -1 : 0)) throw new IllegalArgumentException("Variable value exceeds signed int range");
        return low;
    }

    private static String token(WiredSettings settings, int index) {
        if (settings.getVariableTokens().length <= index)
            throw new IllegalArgumentException("Missing native variable reference");
        String value = settings.getVariableTokens()[index];
        return "n".equals(value) ? "" : value;
    }

    private static String customId(String token) {
        String value = token.startsWith("custom:") ? token.substring(7) : token;
        try {
            if (Integer.parseInt(value) <= 0) throw new IllegalArgumentException("Invalid variable id");
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("Invalid variable id", e);
        }
        return value;
    }

    private static String ids(int[] ids) {
        return Arrays.stream(ids).mapToObj(Integer::toString).collect(Collectors.joining(";"));
    }
}
