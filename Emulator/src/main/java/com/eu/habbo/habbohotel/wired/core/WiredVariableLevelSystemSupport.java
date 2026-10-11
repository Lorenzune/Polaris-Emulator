package com.eu.habbo.habbohotel.wired.core;

import com.eu.habbo.habbohotel.items.interactions.InteractionWiredExtra;
import com.eu.habbo.habbohotel.items.interactions.wired.extra.WiredExtraFurniVariable;
import com.eu.habbo.habbohotel.items.interactions.wired.extra.WiredExtraRoomVariable;
import com.eu.habbo.habbohotel.items.interactions.wired.extra.WiredExtraTimeUtilities;
import com.eu.habbo.habbohotel.items.interactions.wired.extra.WiredExtraUserVariable;
import com.eu.habbo.habbohotel.items.interactions.wired.extra.WiredExtraVariableLevelUpSystem;
import com.eu.habbo.habbohotel.items.interactions.wired.extra.WiredExtraVariableReference;
import com.eu.habbo.habbohotel.rooms.Room;
import com.eu.habbo.habbohotel.rooms.WiredVariableDefinitionInfo;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class WiredVariableLevelSystemSupport {
    public static final int TARGET_USER = 0;
    public static final int TARGET_FURNI = 1;
    public static final int TARGET_ROOM = 3;

    private static final int SYNTHETIC_USER_OFFSET = 700_000_000;
    private static final int SYNTHETIC_FURNI_OFFSET = 800_000_000;
    private static final int SYNTHETIC_ROOM_OFFSET = 900_000_000;
    private static final int SYNTHETIC_STRIDE = 16;

    private WiredVariableLevelSystemSupport() {}

    public static WiredExtraVariableLevelUpSystem getLevelSystem(Room room, InteractionWiredExtra definition) {
        if (room == null || definition == null || room.getRoomSpecialTypes() == null) {
            return null;
        }

        Collection<InteractionWiredExtra> extras =
                room.getRoomSpecialTypes().getExtras(definition.getX(), definition.getY());
        if (extras == null || extras.isEmpty()) {
            return null;
        }

        for (InteractionWiredExtra extra : WiredExecutionOrderUtil.sort(extras)) {
            if (extra instanceof WiredExtraVariableLevelUpSystem) {
                return (WiredExtraVariableLevelUpSystem) extra;
            }
        }

        return null;
    }

    /**
     * Find the co-located derived-variable box (the level-up box OR any {@link WiredDerivedVariableBox},
     * e.g. a quest box) for a base variable definition. At most one box per definition; first wins.
     */
    public static InteractionWiredExtra getDerivedBox(Room room, InteractionWiredExtra definition) {
        if (room == null || definition == null || room.getRoomSpecialTypes() == null) {
            return null;
        }

        Collection<InteractionWiredExtra> extras =
                room.getRoomSpecialTypes().getExtras(definition.getX(), definition.getY());
        if (extras == null || extras.isEmpty()) {
            return null;
        }

        for (InteractionWiredExtra extra : WiredExecutionOrderUtil.sort(extras)) {
            if (extra instanceof com.eu.habbo.habbohotel.items.interactions.wired.extra.WiredExtraQuestBase quest
                    && quest.isNativeConfiguration()) continue;
            if (extra instanceof WiredExtraVariableLevelUpSystem || extra instanceof WiredDerivedVariableBox) {
                return extra;
            }
        }

        return null;
    }

    private static List<Integer> boxSubvariables(InteractionWiredExtra box) {
        if (box instanceof WiredExtraVariableLevelUpSystem lvl) return lvl.getSelectedSubvariables();
        if (box instanceof WiredDerivedVariableBox derived) return derived.getSelectedSubvariables();
        return Collections.emptyList();
    }

    private static boolean boxHasSubvariable(InteractionWiredExtra box, int subType) {
        if (box instanceof WiredExtraVariableLevelUpSystem lvl) return lvl.hasSubvariable(subType);
        if (box instanceof WiredDerivedVariableBox derived) return derived.hasSubvariable(subType);
        return false;
    }

    private static String boxSubvariableKey(InteractionWiredExtra box, int subType) {
        if (box instanceof WiredDerivedVariableBox derived) return derived.subvariableKey(subType);
        return getSubvariableKey(subType);
    }

    public static List<WiredVariableDefinitionInfo> getDerivedDefinitions(
            Room room,
            int targetType,
            InteractionWiredExtra definitionExtra,
            WiredVariableDefinitionInfo baseDefinition) {
        if (room == null || definitionExtra == null || baseDefinition == null) {
            return Collections.emptyList();
        }

        InteractionWiredExtra box = getDerivedBox(room, definitionExtra);
        if (box == null
                || (!baseDefinition.hasValue()
                        && (!(box instanceof WiredExtraTimeUtilities time) || time.getSourceMode() == 0))) {
            return Collections.emptyList();
        }

        List<WiredVariableDefinitionInfo> result = new ArrayList<>();

        for (int subvariableType : boxSubvariables(box)) {
            if (box instanceof WiredExtraTimeUtilities && baseDefinition.getItemId() >= 9_375_000) continue;
            result.add(new WiredVariableDefinitionInfo(
                    box instanceof WiredExtraTimeUtilities
                            ? createTimeSyntheticItemId(targetType, baseDefinition.getItemId(), subvariableType)
                            : createSyntheticItemId(targetType, baseDefinition.getItemId(), subvariableType),
                    baseDefinition.getName() + "." + boxSubvariableKey(box, subvariableType),
                    true,
                    baseDefinition.getAvailability(),
                    false,
                    true));
        }

        result.sort(Comparator.comparing(WiredVariableDefinitionInfo::getName, String.CASE_INSENSITIVE_ORDER)
                .thenComparingInt(WiredVariableDefinitionInfo::getItemId));
        return result;
    }

    public static WiredVariableDefinitionInfo getDerivedDefinitionInfo(Room room, int targetType, int syntheticItemId) {
        DerivedDefinition derived = resolveDerivedDefinition(room, targetType, syntheticItemId);

        if (derived == null) {
            return null;
        }

        return new WiredVariableDefinitionInfo(
                derived.syntheticItemId,
                derived.variableName,
                true,
                derived.baseDefinition.getAvailability(),
                false,
                true);
    }

    public static DerivedDefinition resolveDerivedDefinition(Room room, int targetType, int syntheticItemId) {
        // Preserve legacy room IDs even if a large item ID reaches the newer time namespace.
        if (targetType == TARGET_ROOM && syntheticItemId >= 1_000_000_000) {
            DerivedDefinition legacy = resolveDecodedDefinition(
                    room,
                    targetType,
                    syntheticItemId,
                    decodeSyntheticId(syntheticItemId, TARGET_ROOM, SYNTHETIC_ROOM_OFFSET));
            if (legacy != null) return legacy;
        }
        return resolveDecodedDefinition(room, targetType, syntheticItemId, decodeSyntheticId(syntheticItemId));
    }

    private static DerivedDefinition resolveDecodedDefinition(
            Room room, int targetType, int syntheticItemId, DecodedSyntheticId decoded) {
        if (decoded == null || decoded.targetType != targetType || room == null || room.getRoomSpecialTypes() == null) {
            return null;
        }

        InteractionWiredExtra baseExtra = room.getRoomSpecialTypes().getExtra(decoded.baseDefinitionItemId);
        if (!matchesTarget(baseExtra, targetType)) {
            return null;
        }

        WiredVariableDefinitionInfo baseDefinition = createBaseDefinitionInfo(room, baseExtra, targetType);
        if (baseDefinition == null) {
            return null;
        }

        InteractionWiredExtra box = getDerivedBox(room, baseExtra);
        if (box == null
                || !boxHasSubvariable(box, decoded.subvariableType)
                || (!baseDefinition.hasValue()
                        && (!(box instanceof WiredExtraTimeUtilities time) || time.getSourceMode() == 0))) {
            return null;
        }

        int expectedId = box instanceof WiredExtraTimeUtilities
                ? createTimeSyntheticItemId(targetType, decoded.baseDefinitionItemId, decoded.subvariableType)
                : createSyntheticItemId(targetType, decoded.baseDefinitionItemId, decoded.subvariableType);
        if (expectedId != syntheticItemId) return null;
        return new DerivedDefinition(
                syntheticItemId,
                decoded.baseDefinitionItemId,
                decoded.subvariableType,
                baseDefinition.getName() + "." + boxSubvariableKey(box, decoded.subvariableType),
                baseDefinition,
                box);
    }

    public static Integer getDerivedValue(
            InteractionWiredExtra box, int sub, Integer value, int createdAt, int updatedAt) {
        if (box instanceof WiredExtraTimeUtilities time) {
            return switch (time.getSourceMode()) {
                case 1 -> createdAt == 0 ? null : time.deriveTime(sub, createdAt * 1000L);
                case 2 -> updatedAt == 0 ? null : time.deriveTime(sub, updatedAt * 1000L);
                default -> time.derive(sub, value);
            };
        }
        return getDerivedValue(box, sub, value);
    }

    public static Integer getDerivedValue(InteractionWiredExtra box, int subvariableType, Integer baseValue) {
        if (box == null || baseValue == null) {
            return null;
        }

        if (box instanceof WiredDerivedVariableBox derived) {
            return derived.derive(subvariableType, baseValue);
        }

        if (!(box instanceof WiredExtraVariableLevelUpSystem levelSystem)) {
            return null;
        }

        LevelProgress progress = calculateProgress(levelSystem, baseValue);
        return switch (subvariableType) {
            case WiredExtraVariableLevelUpSystem.SUB_CURRENT_LEVEL -> progress.currentLevel;
            case WiredExtraVariableLevelUpSystem.SUB_CURRENT_XP -> progress.currentXp;
            case WiredExtraVariableLevelUpSystem.SUB_LEVEL_PROGRESS -> progress.progressXp;
            case WiredExtraVariableLevelUpSystem.SUB_LEVEL_PROGRESS_PERCENT -> progress.progressPercent;
            case WiredExtraVariableLevelUpSystem.SUB_TOTAL_XP_REQUIRED -> progress.totalXpRequired;
            case WiredExtraVariableLevelUpSystem.SUB_XP_REMAINING -> progress.xpRemaining;
            case WiredExtraVariableLevelUpSystem.SUB_IS_AT_MAX -> progress.isAtMax ? 1 : 0;
            case WiredExtraVariableLevelUpSystem.SUB_MAX_LEVEL -> progress.maxLevel;
            default -> null;
        };
    }

    public static List<LevelEntry> buildPreviewEntries(WiredExtraVariableLevelUpSystem levelSystem) {
        if (levelSystem == null) {
            return Collections.emptyList();
        }

        var anchors = parseAnchors(levelSystem.getInterpolationText());
        int maxLevel = levelSystem.getMode() == WiredExtraVariableLevelUpSystem.MODE_MANUAL
                ? anchors.keySet().stream().mapToInt(Integer::intValue).max().orElse(1)
                : levelSystem.getMaxLevel();
        var result = new ArrayList<LevelEntry>();
        for (int level : new int[] {1, 2, 3, 5, 10, 20})
            if (level <= maxLevel) result.add(new LevelEntry(level, xpForLevel(levelSystem, anchors, level)));
        return result;
    }

    private static boolean matchesTarget(InteractionWiredExtra extra, int targetType) {
        if (extra == null) {
            return false;
        }

        return switch (targetType) {
            case TARGET_FURNI -> extra instanceof WiredExtraFurniVariable;
            case TARGET_ROOM ->
                (extra instanceof WiredExtraRoomVariable)
                        || (extra instanceof WiredExtraVariableReference
                                && ((WiredExtraVariableReference) extra).isRoomReference());
            default ->
                (extra instanceof WiredExtraUserVariable)
                        || (extra instanceof WiredExtraVariableReference
                                && ((WiredExtraVariableReference) extra).isUserReference());
        };
    }

    private static WiredVariableDefinitionInfo createBaseDefinitionInfo(
            Room room, InteractionWiredExtra extra, int targetType) {
        if (room == null || extra == null) {
            return null;
        }

        if (targetType == TARGET_FURNI && extra instanceof WiredExtraFurniVariable) {
            WiredExtraFurniVariable definition = (WiredExtraFurniVariable) extra;
            return new WiredVariableDefinitionInfo(
                    definition.getId(),
                    definition.getVariableName(),
                    definition.hasValue(),
                    definition.getAvailability(),
                    WiredVariableTextConnectorSupport.isTextConnected(room, definition),
                    false);
        }

        if (targetType == TARGET_USER) {
            if (extra instanceof WiredExtraUserVariable) {
                WiredExtraUserVariable definition = (WiredExtraUserVariable) extra;
                return new WiredVariableDefinitionInfo(
                        definition.getId(),
                        definition.getVariableName(),
                        definition.hasValue(),
                        definition.getAvailability(),
                        WiredVariableTextConnectorSupport.isTextConnected(room, definition),
                        false);
            }

            if (extra instanceof WiredExtraVariableReference
                    && ((WiredExtraVariableReference) extra).isUserReference()) {
                WiredExtraVariableReference reference = (WiredExtraVariableReference) extra;
                return new WiredVariableDefinitionInfo(
                        reference.getId(),
                        reference.getVariableName(),
                        reference.hasValue(),
                        reference.getAvailability(),
                        false,
                        reference.isReadOnly());
            }
        }

        if (targetType == TARGET_ROOM) {
            if (extra instanceof WiredExtraRoomVariable) {
                WiredExtraRoomVariable definition = (WiredExtraRoomVariable) extra;
                return new WiredVariableDefinitionInfo(
                        definition.getId(),
                        definition.getVariableName(),
                        definition.hasValue(),
                        definition.getAvailability(),
                        WiredVariableTextConnectorSupport.isTextConnected(room, definition),
                        false);
            }

            if (extra instanceof WiredExtraVariableReference
                    && ((WiredExtraVariableReference) extra).isRoomReference()) {
                WiredExtraVariableReference reference = (WiredExtraVariableReference) extra;
                return new WiredVariableDefinitionInfo(
                        reference.getId(),
                        reference.getVariableName(),
                        reference.hasValue(),
                        reference.getAvailability(),
                        false,
                        reference.isReadOnly());
            }
        }

        return null;
    }

    private static int createSyntheticItemId(int targetType, int baseDefinitionItemId, int subvariableType) {
        int offset =
                switch (targetType) {
                    case TARGET_FURNI -> SYNTHETIC_FURNI_OFFSET;
                    case TARGET_ROOM -> SYNTHETIC_ROOM_OFFSET;
                    default -> SYNTHETIC_USER_OFFSET;
                };

        return offset + (baseDefinitionItemId * SYNTHETIC_STRIDE) + (subvariableType + 1);
    }

    private static int createTimeSyntheticItemId(int target, int base, int sub) {
        int offset = target == TARGET_USER ? 1_000_000_000 : target == TARGET_FURNI ? 1_300_000_000 : 1_600_000_000;
        if (base <= 0 || base >= 9_375_000) return 0;
        return offset + base * 32 + sub;
    }

    private static DecodedSyntheticId decodeSyntheticId(int syntheticItemId) {
        if (syntheticItemId >= 1_000_000_000 && syntheticItemId < 1_900_000_000) {
            int target = syntheticItemId >= 1_600_000_000
                    ? TARGET_ROOM
                    : syntheticItemId >= 1_300_000_000 ? TARGET_FURNI : TARGET_USER;
            int offset = target == TARGET_USER ? 1_000_000_000 : target == TARGET_FURNI ? 1_300_000_000 : 1_600_000_000;
            int local = syntheticItemId - offset;
            return local / 32 > 0 ? new DecodedSyntheticId(target, local / 32, local % 32) : null;
        }
        if (syntheticItemId >= SYNTHETIC_ROOM_OFFSET) {
            return decodeSyntheticId(syntheticItemId, TARGET_ROOM, SYNTHETIC_ROOM_OFFSET);
        }

        if (syntheticItemId >= SYNTHETIC_FURNI_OFFSET) {
            return decodeSyntheticId(syntheticItemId, TARGET_FURNI, SYNTHETIC_FURNI_OFFSET);
        }

        if (syntheticItemId >= SYNTHETIC_USER_OFFSET) {
            return decodeSyntheticId(syntheticItemId, TARGET_USER, SYNTHETIC_USER_OFFSET);
        }

        return null;
    }

    private static DecodedSyntheticId decodeSyntheticId(int syntheticItemId, int targetType, int offset) {
        int localValue = syntheticItemId - offset;
        if (localValue < 0) {
            return null;
        }

        int encodedSubvariable = localValue % SYNTHETIC_STRIDE;
        int baseDefinitionItemId = localValue / SYNTHETIC_STRIDE;
        int subvariableType = encodedSubvariable - 1;

        if (baseDefinitionItemId <= 0
                || subvariableType < 0
                || subvariableType >= WiredExtraVariableLevelUpSystem.SUBVARIABLE_COUNT) {
            return null;
        }

        return new DecodedSyntheticId(targetType, baseDefinitionItemId, subvariableType);
    }

    private static String getSubvariableKey(int subvariableType) {
        return switch (subvariableType) {
            case WiredExtraVariableLevelUpSystem.SUB_CURRENT_LEVEL -> "current_level";
            case WiredExtraVariableLevelUpSystem.SUB_CURRENT_XP -> "current_xp";
            case WiredExtraVariableLevelUpSystem.SUB_LEVEL_PROGRESS -> "level_progress";
            case WiredExtraVariableLevelUpSystem.SUB_LEVEL_PROGRESS_PERCENT -> "level_progress_percent";
            case WiredExtraVariableLevelUpSystem.SUB_TOTAL_XP_REQUIRED -> "total_xp_required";
            case WiredExtraVariableLevelUpSystem.SUB_XP_REMAINING -> "xp_remaining";
            case WiredExtraVariableLevelUpSystem.SUB_IS_AT_MAX -> "is_at_max";
            case WiredExtraVariableLevelUpSystem.SUB_MAX_LEVEL -> "max_level";
            default -> "value";
        };
    }

    private static LevelProgress calculateProgress(WiredExtraVariableLevelUpSystem config, int rawValue) {
        var anchors = config.getMode() == WiredExtraVariableLevelUpSystem.MODE_MANUAL
                ? parseAnchors(config.getInterpolationText())
                : new LinkedHashMap<Integer, Integer>();
        anchors.putIfAbsent(1, 0);
        int maxLevel = config.getMode() == WiredExtraVariableLevelUpSystem.MODE_MANUAL
                ? anchors.keySet().stream().mapToInt(Integer::intValue).max().orElse(1)
                : config.getMaxLevel();
        int maxXp = config.getMode() == WiredExtraVariableLevelUpSystem.MODE_LINEAR
                ? clamp((long) maxLevel * config.getStepSize())
                : xpForLevel(config, anchors, maxLevel);
        int currentXp = Math.min(Math.max(0, rawValue), maxXp);
        int low = 1, high = maxLevel;
        while (low < high) {
            int middle = low + (int) (((long) high - low + 1) / 2);
            if (currentXp >= xpForLevel(config, anchors, middle)) low = middle;
            else high = middle - 1;
        }
        boolean maxed = low >= maxLevel;
        int threshold = xpForLevel(config, anchors, low);
        int next = maxed ? threshold : xpForLevel(config, anchors, low + 1);
        int progress = maxed ? 0 : Math.max(0, currentXp - threshold);
        int required = maxed ? 0 : Math.max(0, next - threshold);
        int percentage = maxed ? 0 : required == 0 ? 100 : (int) ((progress * 100L) / required);
        return new LevelProgress(
                low, currentXp, progress, percentage, required, maxed ? 0 : next - currentXp, maxed, maxLevel);
    }

    private static int xpForLevel(WiredExtraVariableLevelUpSystem config, Map<Integer, Integer> anchors, int level) {
        if (level <= 1) return 0;
        if (config.getMode() == WiredExtraVariableLevelUpSystem.MODE_LINEAR)
            return clamp((long) (level - 1) * config.getStepSize());
        if (config.getMode() == WiredExtraVariableLevelUpSystem.MODE_EXPONENTIAL) {
            double strength = config.getIncreaseFactor() / 100D;
            double threshold = strength == 0
                    ? (double) config.getFirstLevelXp() * (level - 1)
                    : config.getFirstLevelXp() * ((Math.pow(1 + strength, level - 1) - 1 + 1e-9) / strength);
            return clamp((long) threshold);
        }
        var entries = new ArrayList<>(anchors.entrySet());
        entries.sort(Map.Entry.comparingByKey());
        int previousLevel = 1, previousXp = 0;
        for (var entry : entries) {
            if (entry.getKey() == level) return entry.getValue();
            if (entry.getKey() > level) {
                double step = (entry.getValue() - previousXp) / (double) (entry.getKey() - previousLevel);
                return clamp(previousXp + (long) (step * (level - previousLevel)));
            }
            previousLevel = entry.getKey();
            previousXp = entry.getValue();
        }
        return previousXp;
    }

    private static LinkedHashMap<Integer, Integer> parseAnchors(String interpolationText) {
        LinkedHashMap<Integer, Integer> result = new LinkedHashMap<>();
        if (interpolationText == null || interpolationText.trim().isEmpty()) {
            return result;
        }

        for (String rawLine : interpolationText.split("\n")) {
            if (rawLine == null) {
                continue;
            }

            String line = rawLine.trim();
            if (line.isEmpty()) {
                continue;
            }

            int separatorIndex = line.indexOf('=');
            if (separatorIndex < 0) {
                separatorIndex = line.indexOf(',');
            }

            if (separatorIndex <= 0) {
                continue;
            }

            Integer level = parseInteger(line.substring(0, separatorIndex));
            Integer xp = parseInteger(line.substring(separatorIndex + 1));

            if (level == null || xp == null || level <= 0 || xp < 0) {
                continue;
            }

            result.put(level, xp);
        }

        return result;
    }

    private static Integer parseInteger(String value) {
        try {
            return (value == null || value.trim().isEmpty()) ? null : Integer.parseInt(value.trim());
        } catch (NumberFormatException ignored) {
            return null;
        }
    }

    private static int clamp(long value) {
        if (value > Integer.MAX_VALUE) {
            return Integer.MAX_VALUE;
        }

        if (value < Integer.MIN_VALUE) {
            return Integer.MIN_VALUE;
        }

        return (int) value;
    }

    public static class DerivedDefinition {
        private final int syntheticItemId;
        private final int baseDefinitionItemId;
        private final int subvariableType;
        private final String variableName;
        private final WiredVariableDefinitionInfo baseDefinition;
        private final InteractionWiredExtra levelSystem;

        public DerivedDefinition(
                int syntheticItemId,
                int baseDefinitionItemId,
                int subvariableType,
                String variableName,
                WiredVariableDefinitionInfo baseDefinition,
                InteractionWiredExtra levelSystem) {
            this.syntheticItemId = syntheticItemId;
            this.baseDefinitionItemId = baseDefinitionItemId;
            this.subvariableType = subvariableType;
            this.variableName = variableName;
            this.baseDefinition = baseDefinition;
            this.levelSystem = levelSystem;
        }

        public int getBaseDefinitionItemId() {
            return this.baseDefinitionItemId;
        }

        public int getSubvariableType() {
            return this.subvariableType;
        }

        public WiredVariableDefinitionInfo getBaseDefinition() {
            return this.baseDefinition;
        }

        public InteractionWiredExtra getLevelSystem() {
            return this.levelSystem;
        }
    }

    public static class LevelEntry {
        private final int level;
        private final int requiredXp;

        public LevelEntry(int level, int requiredXp) {
            this.level = level;
            this.requiredXp = requiredXp;
        }

        public int getLevel() {
            return this.level;
        }

        public int getRequiredXp() {
            return this.requiredXp;
        }
    }

    private static class LevelProgress {
        private final int currentLevel;
        private final int currentXp;
        private final int progressXp;
        private final int progressPercent;
        private final int totalXpRequired;
        private final int xpRemaining;
        private final boolean isAtMax;
        private final int maxLevel;

        private LevelProgress(
                int currentLevel,
                int currentXp,
                int progressXp,
                int progressPercent,
                int totalXpRequired,
                int xpRemaining,
                boolean isAtMax,
                int maxLevel) {
            this.currentLevel = currentLevel;
            this.currentXp = currentXp;
            this.progressXp = progressXp;
            this.progressPercent = progressPercent;
            this.totalXpRequired = totalXpRequired;
            this.xpRemaining = xpRemaining;
            this.isAtMax = isAtMax;
            this.maxLevel = maxLevel;
        }
    }

    private static class DecodedSyntheticId {
        private final int targetType;
        private final int baseDefinitionItemId;
        private final int subvariableType;

        private DecodedSyntheticId(int targetType, int baseDefinitionItemId, int subvariableType) {
            this.targetType = targetType;
            this.baseDefinitionItemId = baseDefinitionItemId;
            this.subvariableType = subvariableType;
        }
    }
}
