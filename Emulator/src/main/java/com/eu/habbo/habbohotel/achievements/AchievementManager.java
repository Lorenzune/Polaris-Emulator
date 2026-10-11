package com.eu.habbo.habbohotel.achievements;

import com.eu.habbo.Emulator;
import com.eu.habbo.habbohotel.items.Item;
import com.eu.habbo.habbohotel.users.Habbo;
import com.eu.habbo.habbohotel.users.HabboBadge;
import com.eu.habbo.habbohotel.users.HabboItem;
import com.eu.habbo.habbohotel.users.inventory.BadgesComponent;
import com.eu.habbo.habbohotel.users.levels.AvatarLevelPlatform;
import com.eu.habbo.messages.outgoing.achievements.AchievementProgressComposer;
import com.eu.habbo.messages.outgoing.achievements.AchievementUnlockedComposer;
import com.eu.habbo.messages.outgoing.achievements.talenttrack.TalentLevelUpdateComposer;
import com.eu.habbo.messages.outgoing.inventory.AddHabboItemComposer;
import com.eu.habbo.messages.outgoing.inventory.InventoryRefreshComposer;
import com.eu.habbo.messages.outgoing.rooms.users.RoomUserDataComposer;
import com.eu.habbo.messages.outgoing.users.AddUserBadgeComposer;
import com.eu.habbo.plugin.Event;
import com.eu.habbo.plugin.events.users.achievements.UserAchievementLeveledEvent;
import com.eu.habbo.plugin.events.users.achievements.UserAchievementProgressEvent;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class AchievementManager {
    private static final Logger LOGGER = LoggerFactory.getLogger(AchievementManager.class);

    public static volatile boolean TALENTTRACK_ENABLED = false;

    private final Map<String, Achievement> achievements;
    private final Map<TalentTrackType, LinkedHashMap<Integer, TalentTrackLevel>> talentTrackLevels;

    public AchievementManager() {
        this.achievements = new HashMap<>();
        this.talentTrackLevels = new HashMap<>();
    }

    private static final class DatabaseLevel {
        private final int rewardAmount;
        private final int rewardType;
        private final int points;
        private final int progress;

        private DatabaseLevel(int rewardAmount, int rewardType, int points, int progress) {
            this.rewardAmount = rewardAmount;
            this.rewardType = rewardType;
            this.points = points;
            this.progress = progress;
        }
    }

    private static final class DatabaseAchievement {
        private final int id;
        private final String name;
        private final String category;
        private final Integer state;
        private final Map<Integer, DatabaseLevel> levels = new LinkedHashMap<>();

        private DatabaseAchievement(int id, String name, String category, Integer state) {
            this.id = id;
            this.name = name;
            this.category = category;
            this.state = state;
        }
    }

    public static void progressAchievement(int habboId, Achievement achievement) {
        progressAchievement(habboId, achievement, 1);
    }

    public static void progressAchievement(int habboId, Achievement achievement, int amount) {
        if (achievement != null && amount > 0) {
            Habbo habbo = AvatarLevelPlatform.game().getHabboManager().getHabbo(habboId);

            if (habbo != null && habbo.isOnline()) {
                progressAchievement(habbo, achievement, amount);
            } else {
                try (Connection connection =
                                AvatarLevelPlatform.database().getDataSource().getConnection();
                        PreparedStatement statement = connection.prepareStatement(""
                                + "INSERT INTO users_achievements_queue (user_id, achievement_id, amount) VALUES (?, ?, ?) "
                                + "ON DUPLICATE KEY UPDATE amount = LEAST(2147483647, CAST(amount AS SIGNED) + ?)")) {
                    statement.setInt(1, habboId);
                    statement.setInt(2, achievement.id);
                    statement.setInt(3, amount);
                    statement.setInt(4, amount);
                    statement.execute();
                } catch (SQLException e) {
                    LOGGER.error("Caught SQL exception", e);
                }
            }
        }
    }

    public static void progressAchievement(Habbo habbo, Achievement achievement) {
        progressAchievement(habbo, achievement, 1);
    }

    public static void progressAchievement(Habbo habbo, Achievement achievement, int amount) {
        if (habbo == null || amount < 0) return;
        if (!habbo.isOnline()) {
            progressAchievement(habbo.getHabboInfo().getId(), achievement, amount);
            return;
        }
        synchronized (habbo.getHabboStats().getAchievementProgress()) {
            progressAchievement(habbo, achievement, amount, false);
        }
    }

    /** Publish an absolute progress value which has already committed to the database. */
    public static void publishPersistedProgress(Habbo habbo, Achievement achievement, int progress) {
        if (habbo == null || achievement == null) return;
        synchronized (habbo.getHabboStats().getAchievementProgress()) {
            if (progress <= habbo.getHabboStats().getAchievementProgress(achievement)) return;
            progressAchievement(habbo, achievement, progress, true);
        }
    }

    private static void progressAchievement(Habbo habbo, Achievement achievement, int amount, boolean persisted) {
        if (achievement == null) return;

        if (achievement.levels.isEmpty()) return;

        if (habbo == null) return;

        if (!habbo.isOnline()) return;

        int currentProgress = habbo.getHabboStats().getAchievementProgress(achievement);

        if (currentProgress == -1) {
            currentProgress = 0;
        }

        if (!persisted && Emulator.getPluginManager().isRegistered(UserAchievementProgressEvent.class, true)) {
            Event userAchievementProgressedEvent = new UserAchievementProgressEvent(habbo, achievement, amount);
            Emulator.getPluginManager().fireEvent(userAchievementProgressedEvent);

            if (userAchievementProgressedEvent.isCancelled()) return;
        }

        AchievementLevel oldLevel = achievement.getLevelForProgress(currentProgress);
        AchievementLevel finalLevel = achievement.getLastLevel();

        int newProgress;
        try {
            if (persisted) newProgress = amount;
            else {
                try (Connection connection =
                        AvatarLevelPlatform.database().getDataSource().getConnection()) {
                    connection.setAutoCommit(false);
                    try {
                        newProgress = persistIncrement(
                                connection, habbo.getHabboInfo().getId(), achievement, amount);
                        connection.commit();
                    } catch (SQLException exception) {
                        connection.rollback();
                        throw exception;
                    }
                }
            }
        } catch (SQLException exception) {
            LOGGER.error(
                    "Achievement progress was not committed for {}",
                    habbo.getHabboInfo().getId(),
                    exception);
            return;
        }
        habbo.getHabboStats().setProgress(achievement, newProgress);
        if (oldLevel != null && finalLevel != null && oldLevel.level == finalLevel.level) return;

        AchievementLevel newLevel = achievement.getLevelForProgress(newProgress);

        if (AchievementManager.TALENTTRACK_ENABLED) {
            for (TalentTrackType type : TalentTrackType.values()) {
                if (AvatarLevelPlatform.game()
                        .getAchievementManager()
                        .talentTrackLevels
                        .containsKey(type)) {
                    for (Map.Entry<Integer, TalentTrackLevel> entry : AvatarLevelPlatform.game()
                            .getAchievementManager()
                            .talentTrackLevels
                            .get(type)
                            .entrySet()) {
                        if (entry.getValue().achievements.containsKey(achievement)) {
                            AvatarLevelPlatform.game()
                                    .getAchievementManager()
                                    .handleTalentTrackAchievement(habbo, type, achievement);
                            break;
                        }
                    }
                }
            }
        }

        if (newLevel == null
                || (oldLevel != null
                        && (oldLevel.level == newLevel.level
                                && finalLevel != null
                                && newLevel.level < finalLevel.level))) {
            habbo.getClient().sendResponse(new AchievementProgressComposer(habbo, achievement));
        } else {
            if (Emulator.getPluginManager().isRegistered(UserAchievementLeveledEvent.class, true)) {
                Event userAchievementLeveledEvent =
                        new UserAchievementLeveledEvent(habbo, achievement, oldLevel, newLevel);
                Emulator.getPluginManager().fireEvent(userAchievementLeveledEvent);

                if (userAchievementLeveledEvent.isCancelled()) return;
            }

            habbo.getClient().sendResponse(new AchievementProgressComposer(habbo, achievement));
            habbo.getClient().sendResponse(new AchievementUnlockedComposer(habbo, achievement));

            // Exception could possibly arise when the user disconnects while being in tour.
            // The achievement is then progressed but the user is already disposed so fetching
            // the badge would result in an nullpointer exception. This is normal behaviour.
            HabboBadge badge = null;

            if (oldLevel != null) {
                try {
                    badge = habbo.getInventory()
                            .getBadgesComponent()
                            .getBadge(("ACH_" + achievement.name + oldLevel.level).toLowerCase());
                } catch (Exception e) {
                    LOGGER.error("Caught exception", e);
                    return;
                }
            }

            String newBadgCode = "ACH_" + achievement.name + newLevel.level;
            boolean createdBadge = false;

            if (badge != null) {
                badge.setCode(newBadgCode);
                badge.needsInsert(false);
                badge.needsUpdate(true);
            } else {
                badge = habbo.getInventory().getBadgesComponent().getBadge(newBadgCode);

                if (badge == null) {
                    badge = new HabboBadge(0, newBadgCode, 0, habbo);
                    badge.needsInsert(true);
                    badge.needsUpdate(true);
                    habbo.getInventory().getBadgesComponent().addBadge(badge);
                    createdBadge = true;
                }
            }

            // Persist before publishing the badge. This guarantees a valid id for
            // the inventory packet and prevents a quick reconnect from loading the
            // previous achievement level.
            badge.run();

            if (createdBadge) habbo.getClient().sendResponse(new AddUserBadgeComposer(badge));

            BadgesComponent.synchronizeBadgeState(habbo);

            if (badge.getId() > 0) {
                habbo.getClient()
                        .sendResponse(new AddHabboItemComposer(
                                badge.getId(), AddHabboItemComposer.AddHabboItemCategory.BADGE));
            }

            habbo.getHabboStats().addAchievementScore(newLevel.points);

            if (newLevel.rewardAmount > 0) {
                habbo.givePoints(newLevel.rewardType, newLevel.rewardAmount, "achievement.reward");
            }

            if (habbo.getHabboInfo().getCurrentRoom() != null) {
                habbo.getHabboInfo().getCurrentRoom().sendComposer(new RoomUserDataComposer(habbo).compose());
            }
        }
    }

    private static int persistIncrement(Connection connection, int userId, Achievement achievement, int amount)
            throws SQLException {
        try (PreparedStatement update = connection.prepareStatement(
                "INSERT INTO users_achievements (user_id, achievement_name, progress) VALUES (?, ?, ?) "
                        + "ON DUPLICATE KEY UPDATE progress = LEAST(2147483647, CAST(progress AS SIGNED) + VALUES(progress))")) {
            update.setInt(1, userId);
            update.setString(2, achievement.name);
            update.setInt(3, amount);
            update.executeUpdate();
        }
        try (PreparedStatement query = connection.prepareStatement(
                "SELECT COALESCE(MAX(progress), 0) AS progress FROM users_achievements WHERE user_id = ? AND achievement_name = ?")) {
            query.setInt(1, userId);
            query.setString(2, achievement.name);
            try (ResultSet row = query.executeQuery()) {
                row.next();
                return row.getInt(1);
            }
        }
    }

    /** Delete the queue and apply it in one transaction, avoiding loss or double counting after a crash. */
    public static void consumeQueuedProgress(Habbo habbo) throws SQLException {
        Map<Achievement, Integer> committed = new LinkedHashMap<>();
        int userId = habbo.getHabboInfo().getId();
        synchronized (habbo.getHabboStats().getAchievementProgress()) {
            try (Connection connection =
                    AvatarLevelPlatform.database().getDataSource().getConnection()) {
                connection.setAutoCommit(false);
                try {
                    Map<Achievement, Integer> pending = new LinkedHashMap<>();
                    try (PreparedStatement query = connection.prepareStatement(
                            "SELECT achievement_id, amount FROM users_achievements_queue WHERE user_id = ? FOR UPDATE")) {
                        query.setInt(1, userId);
                        try (ResultSet rows = query.executeQuery()) {
                            while (rows.next()) {
                                Achievement definition = AvatarLevelPlatform.game()
                                        .getAchievementManager()
                                        .getAchievement(rows.getInt(1));
                                if (definition != null) pending.put(definition, rows.getInt(2));
                            }
                        }
                    }
                    for (var entry : pending.entrySet()) {
                        try (PreparedStatement delete = connection.prepareStatement(
                                "DELETE FROM users_achievements_queue WHERE user_id = ? AND achievement_id = ?")) {
                            delete.setInt(1, userId);
                            delete.setInt(2, entry.getKey().id);
                            delete.executeUpdate();
                        }
                        committed.put(
                                entry.getKey(),
                                persistIncrement(connection, userId, entry.getKey(), Math.max(0, entry.getValue())));
                    }
                    connection.commit();
                } catch (SQLException exception) {
                    connection.rollback();
                    throw exception;
                }
            }
            for (var entry : committed.entrySet()) publishPersistedProgress(habbo, entry.getKey(), entry.getValue());
        }
    }

    public static boolean hasAchieved(Habbo habbo, Achievement achievement) {
        int currentProgress = habbo.getHabboStats().getAchievementProgress(achievement);

        if (currentProgress == -1) {
            return false;
        }

        AchievementLevel level = achievement.getLevelForProgress(currentProgress);

        if (level == null) return false;

        AchievementLevel nextLevel = achievement.levels.get(level.level + 1);

        return nextLevel == null && currentProgress >= level.progress;
    }

    public static void createUserEntry(Habbo habbo, Achievement achievement) {
        try (Connection connection =
                        AvatarLevelPlatform.database().getDataSource().getConnection();
                PreparedStatement statement = connection.prepareStatement(
                        "INSERT INTO users_achievements (user_id, achievement_name, progress) VALUES (?, ?, ?)")) {
            statement.setInt(1, habbo.getHabboInfo().getId());
            statement.setString(2, achievement.name);
            statement.setInt(3, 0);
            statement.execute();
        } catch (SQLException e) {
            LOGGER.error("Caught SQL exception", e);
        }
    }

    public static void saveAchievements(Habbo habbo) {
        try (Connection connection =
                        AvatarLevelPlatform.database().getDataSource().getConnection();
                PreparedStatement statement = connection.prepareStatement(
                        "UPDATE users_achievements SET progress = GREATEST(progress, ?) WHERE achievement_name = ? AND user_id = ? LIMIT 1")) {
            statement.setInt(3, habbo.getHabboInfo().getId());
            Map<Achievement, Integer> copy;
            synchronized (habbo.getHabboStats().getAchievementProgress()) {
                copy = new HashMap<>(habbo.getHabboStats().getAchievementProgress());
            }
            for (Map.Entry<Achievement, Integer> map : copy.entrySet()) {
                statement.setInt(1, map.getValue());
                statement.setString(2, map.getKey().name);
                statement.addBatch();
            }
            statement.executeBatch();
        } catch (SQLException e) {
            LOGGER.error("Caught SQL exception", e);
        }
    }

    public static int getAchievementProgressForHabbo(int userId, Achievement achievement) {
        if (achievement == null) {
            return 0;
        }

        try (Connection connection =
                        AvatarLevelPlatform.database().getDataSource().getConnection();
                PreparedStatement statement = connection.prepareStatement(
                        "SELECT COALESCE(MAX(progress), 0) AS progress FROM users_achievements WHERE user_id = ? AND achievement_name = ?")) {
            statement.setInt(1, userId);
            statement.setString(2, achievement.name);
            try (ResultSet set = statement.executeQuery()) {
                if (set.next()) {
                    return set.getInt("progress");
                }
            }
        } catch (SQLException e) {
            LOGGER.error("Caught SQL exception", e);
        }

        return 0;
    }

    public void reload() {
        long millis = System.currentTimeMillis();
        synchronized (this.achievements) {
            try (Connection connection =
                    AvatarLevelPlatform.database().getDataSource().getConnection()) {
                Map<String, DatabaseAchievement> databaseAchievements = readDatabaseAchievements(connection);
                Map<String, Achievement> loadedAchievements = loadSwfAchievements(databaseAchievements);

                if (loadedAchievements == null) {
                    loadedAchievements = new LinkedHashMap<>();
                    for (DatabaseAchievement definition : databaseAchievements.values()) {
                        Achievement achievement = new Achievement(
                                definition.id,
                                definition.name,
                                definition.category,
                                definition.state == null ? (short) 1 : definition.state.shortValue(),
                                "",
                                0);
                        for (Map.Entry<Integer, DatabaseLevel> entry : definition.levels.entrySet()) {
                            DatabaseLevel level = entry.getValue();
                            achievement.addLevel(new AchievementLevel(
                                    entry.getKey(),
                                    level.rewardAmount,
                                    level.rewardType,
                                    level.points,
                                    level.progress));
                        }
                        loadedAchievements.put(achievement.name, achievement);
                    }
                    LOGGER.warn("Achievement SWF data unavailable; loaded legacy database definitions");
                }

                this.achievements.clear();
                this.achievements.putAll(loadedAchievements);

                synchronized (this.talentTrackLevels) {
                    this.talentTrackLevels.clear();

                    try (Statement statement = connection.createStatement();
                            ResultSet set =
                                    statement.executeQuery("SELECT * FROM achievements_talents ORDER BY level ASC")) {
                        while (set.next()) {
                            TalentTrackLevel level = new TalentTrackLevel(set);

                            if (!this.talentTrackLevels.containsKey(level.type)) {
                                this.talentTrackLevels.put(level.type, new LinkedHashMap<>());
                            }

                            this.talentTrackLevels.get(level.type).put(level.level, level);
                        }
                    }
                }
            } catch (SQLException e) {
                LOGGER.error("Caught SQL exception", e);
                LOGGER.error("Achievement Manager -> Failed to load!");
                return;
            }
        }

        var environment = AvatarLevelPlatform.game();
        if (environment != null && environment.getAvatarLevelManager() != null) {
            environment.getAvatarLevelManager().initializeDefinitions();
        }
        LOGGER.info("Achievement Manager -> Loaded! ({} MS)", System.currentTimeMillis() - millis);
    }

    private Map<String, DatabaseAchievement> readDatabaseAchievements(Connection connection) throws SQLException {
        Map<String, DatabaseAchievement> result = new LinkedHashMap<>();
        try (Statement statement = connection.createStatement();
                ResultSet set = statement.executeQuery("SELECT * FROM achievements")) {
            while (set.next()) {
                String name = set.getString("name");
                DatabaseAchievement definition = result.computeIfAbsent(
                        name,
                        ignored -> new DatabaseAchievement(
                                setInt(set, "id"), name, setString(set, "category"), setNullableInt(set, "state")));
                definition.levels.put(
                        set.getInt("level"),
                        new DatabaseLevel(
                                set.getInt("reward_amount"),
                                set.getInt("reward_type"),
                                set.getInt("points"),
                                set.getInt("progress_needed")));
            }
        }
        return result;
    }

    private Map<String, Achievement> loadSwfAchievements(Map<String, DatabaseAchievement> databaseAchievements) {
        Path source = resolveAchievementSource();
        String sourceDescription = source != null && Files.isRegularFile(source)
                ? source.toString()
                : "bundled gamedata/achievements.json";

        try (Reader reader = openAchievementSource(source)) {
            if (reader == null) return null;
            JsonElement root = JsonParser.parseReader(reader);
            if (!root.isJsonArray()) return null;

            Map<String, Achievement> result = new LinkedHashMap<>();
            for (JsonElement element : root.getAsJsonArray()) {
                if (!element.isJsonObject()) continue;
                JsonObject entry = element.getAsJsonObject();
                JsonObject metadata = entry.has("achievement") ? entry.getAsJsonObject("achievement") : null;
                if (metadata == null || !metadata.has("name") || !metadata.has("id")) continue;

                String sourceName = metadata.get("name").getAsString();
                DatabaseAchievement database = databaseAchievements.get(sourceName);
                if (database == null) {
                    int sourceId = metadata.get("id").getAsInt();
                    database = databaseAchievements.values().stream()
                            .filter(definition -> definition.id == sourceId)
                            .findFirst()
                            .orElse(null);
                }
                String name = database != null ? database.name : sourceName;
                String category = database != null && database.category != null && !database.category.isBlank()
                        ? database.category
                        : metadata.has("category") ? metadata.get("category").getAsString() : "other";
                short state = database != null && database.state != null
                        ? database.state.shortValue()
                        : stateOf(metadata.has("state") ? metadata.get("state").getAsString() : "ENABLED");
                Achievement achievement = new Achievement(metadata.get("id").getAsInt(), name, category, state, "", 0);
                JsonArray levels = entry.has("levelRequirements")
                                && entry.get("levelRequirements").isJsonArray()
                        ? entry.getAsJsonArray("levelRequirements")
                        : new JsonArray();
                for (JsonElement levelElement : levels) {
                    if (!levelElement.isJsonObject()) continue;
                    JsonObject levelObject = levelElement.getAsJsonObject();
                    int level = levelObject.get("level").getAsInt();
                    int progress = levelObject.get("requiredScore").getAsInt();
                    DatabaseLevel reward = database == null ? null : database.levels.get(level);
                    if (reward != null) progress = reward.progress;
                    achievement.addLevel(new AchievementLevel(
                            level,
                            reward == null ? 0 : reward.rewardAmount,
                            reward == null ? 0 : reward.rewardType,
                            reward == null ? 0 : reward.points,
                            progress));
                }
                if (achievement.levels.isEmpty() && database != null) {
                    for (Map.Entry<Integer, DatabaseLevel> databaseLevel : database.levels.entrySet()) {
                        DatabaseLevel reward = databaseLevel.getValue();
                        achievement.addLevel(new AchievementLevel(
                                databaseLevel.getKey(),
                                reward.rewardAmount,
                                reward.rewardType,
                                reward.points,
                                reward.progress));
                    }
                }
                result.put(name, achievement);
            }

            // Keep non-SWF rows as archived compatibility entries so existing server hooks
            // do not silently lose their progress while the JSON becomes authoritative.
            for (DatabaseAchievement definition : databaseAchievements.values()) {
                if (result.containsKey(definition.name)) continue;
                Achievement achievement = new Achievement(
                        definition.id,
                        definition.name,
                        definition.category,
                        definition.state == null ? (short) 2 : definition.state.shortValue(),
                        "",
                        0);
                for (Map.Entry<Integer, DatabaseLevel> entry : definition.levels.entrySet()) {
                    DatabaseLevel level = entry.getValue();
                    achievement.addLevel(new AchievementLevel(
                            entry.getKey(), level.rewardAmount, level.rewardType, level.points, level.progress));
                }
                result.put(achievement.name, achievement);
            }
            LOGGER.info("Achievement Manager -> Loaded SWF definitions from {}", sourceDescription);
            return result;
        } catch (Exception exception) {
            LOGGER.error("Could not load SWF achievement definitions from {}", source, exception);
            return null;
        }
    }

    static Reader openAchievementSource(Path source) throws java.io.IOException {
        if (source != null && Files.isRegularFile(source)) return Files.newBufferedReader(source);

        InputStream bundled = AchievementManager.class.getResourceAsStream("/gamedata/achievements.json");
        return bundled == null ? null : new InputStreamReader(bundled, StandardCharsets.UTF_8);
    }

    private Path resolveAchievementSource() {
        String configuredRoot = Emulator.getConfig().getValue("nitro.secure.gamedata.root", "");
        Path workingDirectory = Paths.get(System.getProperty("user.dir"));
        List<Path> candidates = new java.util.ArrayList<>();
        if (configuredRoot != null && !configuredRoot.isBlank()) {
            Path configured = Paths.get(configuredRoot);
            candidates.add((configured.isAbsolute() ? configured : workingDirectory.resolve(configured))
                    .resolve("achievements.json")
                    .normalize());
        }
        // The emulator and the shared Nitro checkout are sibling directories in
        // the normal deployment, so accept both the local and repository-level
        // gamedata locations without requiring a second copy of the catalogue.
        for (String relative : List.of("gamedata", "../gamedata", "../../gamedata", "../../../gamedata")) {
            candidates.add(workingDirectory
                    .resolve(relative)
                    .resolve("achievements.json")
                    .normalize());
        }
        for (Path candidate : candidates) {
            if (Files.isRegularFile(candidate)) return candidate;
        }
        return candidates.isEmpty() ? null : candidates.get(0);
    }

    private static short stateOf(String state) {
        if (state == null) return 1;
        switch (state.toUpperCase(Locale.ROOT)) {
            case "OBSOLETE":
                return -1;
            case "DISABLED":
                return 0;
            case "ARCHIVED":
                return 2;
            case "OFF_SEASON":
                return 3;
            case "WIRED":
                return 4;
            default:
                return 1;
        }
    }

    private static int setInt(ResultSet set, String column) {
        try {
            return set.getInt(column);
        } catch (SQLException ignored) {
            return 0;
        }
    }

    private static String setString(ResultSet set, String column) {
        try {
            return set.getString(column);
        } catch (SQLException ignored) {
            return "other";
        }
    }

    private static Integer setNullableInt(ResultSet set, String column) {
        try {
            int value = set.getInt(column);
            return set.wasNull() ? null : value;
        } catch (SQLException ignored) {
            return null;
        }
    }

    public Achievement getAchievement(String name) {
        return this.achievements.get(name);
    }

    public Achievement getAchievement(int id) {
        synchronized (this.achievements) {
            for (Map.Entry<String, Achievement> set : this.achievements.entrySet()) {
                if (set.getValue().id == id) {
                    return set.getValue();
                }
            }
        }

        return null;
    }

    public Map<String, Achievement> getAchievements() {
        return this.achievements;
    }

    public LinkedHashMap<Integer, TalentTrackLevel> getTalenTrackLevels(TalentTrackType type) {
        return this.talentTrackLevels.get(type);
    }

    public TalentTrackLevel calculateTalenTrackLevel(Habbo habbo, TalentTrackType type) {
        TalentTrackLevel level = null;

        for (Map.Entry<Integer, TalentTrackLevel> entry :
                this.talentTrackLevels.get(type).entrySet()) {
            final boolean[] allCompleted = {true};
            for (Map.Entry<Achievement, Integer> achievementEntry :
                    entry.getValue().achievements.entrySet()) {
                if (habbo.getHabboStats().getAchievementProgress(achievementEntry.getKey())
                        < achievementEntry.getValue()) {
                    allCompleted[0] = false;
                    break;
                }
            }

            if (allCompleted[0]) {
                if (level == null || level.level < entry.getValue().level) {
                    level = entry.getValue();
                }
            } else {
                break;
            }
        }

        return level;
    }

    public void handleTalentTrackAchievement(Habbo habbo, TalentTrackType type, Achievement achievement) {
        TalentTrackLevel currentLevel = this.calculateTalenTrackLevel(habbo, type);

        if (currentLevel != null) {
            if (currentLevel.level > habbo.getHabboStats().talentTrackLevel(type)) {
                for (int i = habbo.getHabboStats().talentTrackLevel(type); i <= currentLevel.level; i++) {
                    TalentTrackLevel level = this.getTalentTrackLevel(type, i);

                    if (level != null) {
                        if (level.items != null && !level.items.isEmpty()) {
                            for (Item item : level.items) {
                                HabboItem rewardItem = AvatarLevelPlatform.game()
                                        .getItemManager()
                                        .createItem(habbo.getHabboInfo().getId(), item, 0, 0, "");
                                habbo.getInventory().getItemsComponent().addItem(rewardItem);
                                habbo.getClient().sendResponse(new AddHabboItemComposer(rewardItem));
                                habbo.getClient().sendResponse(new InventoryRefreshComposer());
                            }
                        }

                        if (level.badges != null && level.badges.length > 0) {
                            for (String badge : level.badges) {
                                if (!badge.isEmpty()) {
                                    if (!habbo.getInventory()
                                            .getBadgesComponent()
                                            .hasBadge(badge)) {
                                        HabboBadge b = new HabboBadge(0, badge, 0, habbo);
                                        Emulator.getThreading().run(b);
                                        habbo.getInventory()
                                                .getBadgesComponent()
                                                .addBadge(b);
                                        habbo.getClient().sendResponse(new AddUserBadgeComposer(b));
                                    }
                                }
                            }
                        }

                        if (level.perks != null && level.perks.length > 0) {
                            for (String perk : level.perks) {
                                if (perk.equalsIgnoreCase("TRADE")) {
                                    habbo.getHabboStats().perkTrade = true;
                                }
                            }
                        }
                        habbo.getClient().sendResponse(new TalentLevelUpdateComposer(type, level));
                    }
                }
            }

            habbo.getHabboStats().setTalentLevel(type, currentLevel.level);
            Map<Integer, TalentTrackLevel> levels = this.talentTrackLevels.get(type);
            int finalLevel = levels == null || levels.isEmpty()
                    ? -1
                    : levels.keySet().stream().mapToInt(Integer::intValue).max().orElse(-1);
            if (type == TalentTrackType.CITIZENSHIP && currentLevel.level >= finalLevel && finalLevel >= 0) {
                AchievementInteractions.progress(habbo, "Citizenship");
            }
        }
    }

    public TalentTrackLevel getTalentTrackLevel(TalentTrackType type, int level) {
        return this.talentTrackLevels.get(type).get(level);
    }
}
