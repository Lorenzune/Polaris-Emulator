package com.eu.habbo.habbohotel.mentions;

import com.eu.habbo.Emulator;
import com.eu.habbo.habbohotel.messenger.MessengerBuddy;
import com.eu.habbo.habbohotel.rooms.Room;
import com.eu.habbo.habbohotel.rooms.RoomChatMessage;
import com.eu.habbo.habbohotel.rooms.RoomChatMessageBubbles;
import com.eu.habbo.habbohotel.rooms.RoomChatType;
import com.eu.habbo.habbohotel.users.Habbo;
import com.eu.habbo.habbohotel.users.HabboManager;
import com.eu.habbo.messages.outgoing.rooms.users.RoomUserWhisperComposer;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class MentionManager {

    private static final Logger LOGGER = LoggerFactory.getLogger(MentionManager.class);

    private static final int ROOM_NAME_MAX_LENGTH = 64;
    private static final int MESSAGE_MAX_LENGTH = 255;
    private final ConcurrentHashMap<Integer, Long> cooldowns = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<Integer, Long> roomBroadcastCooldowns = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<Integer, Long> requestListCooldowns = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<Integer, Long> markReadCooldowns = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<Integer, Long> markAllCooldowns = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<Integer, Long> followCooldowns = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<Long, Long> recipientCooldowns = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<Integer, Long> settingsCooldowns = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<Integer, Long> deleteCooldowns = new ConcurrentHashMap<>();

    private volatile long lastPrune = System.currentTimeMillis();
    private static final long PRUNE_INTERVAL_MS = 5 * 60_000L;

    public boolean isEnabled() {
        return configuration().getInt("mentions.enabled", 1) == 1;
    }

    public enum BroadcastScope {
        NONE,
        ROOM,
        FRIENDS,
        EVERYONE
    }

    public static final String PERMISSION_EVERYONE = "acc_mention_everyone";
    public static final String PERMISSION_FRIENDS = "acc_mention_friends";

    private Set<String> parseAliases(String configKey, String defaultValue) {
        Set<String> aliases = new HashSet<>();
        String raw = configuration().getValue(configKey, defaultValue);
        for (String alias : raw.split(",")) {
            String trimmed = alias.trim().toLowerCase();
            if (!trimmed.isEmpty()) {
                aliases.add(trimmed);
            }
        }
        return aliases;
    }

    private Set<String> roomAliases() {
        return parseAliases("mentions.room.aliases", "room,stanza");
    }

    private Set<String> friendsAliases() {
        return parseAliases("mentions.friends.aliases", "friends,amici");
    }

    private Set<String> everyoneAliases() {
        return parseAliases("mentions.everyone.aliases", "all,everyone,tutti");
    }

    private BroadcastScope classifyAlias(String alias, Set<String> everyone, Set<String> friends, Set<String> room) {
        if (alias.isEmpty()) return BroadcastScope.NONE;
        if (everyone.contains(alias)) return BroadcastScope.EVERYONE;
        if (friends.contains(alias)) return BroadcastScope.FRIENDS;
        if (room.contains(alias)) return BroadcastScope.ROOM;
        return BroadcastScope.NONE;
    }

    public void process(Habbo sender, Room room, String message, RoomChatType type) {
        try {
            if (!this.isEnabled()) {
                return;
            }

            if (sender == null || room == null || message == null) {
                return;
            }

            if (message.isEmpty() || message.indexOf('@') < 0) {
                return;
            }

            int senderId = sender.getHabboInfo().getId();
            long now = System.currentTimeMillis();
            long cooldownMs = Math.max(3000, configuration().getInt("mentions.cooldown.ms", 3000));
            Long last = this.cooldowns.get(senderId);
            if (last != null && (now - last) < cooldownMs) {
                return;
            }

            Set<String> roomAliases = this.roomAliases();
            Set<String> friendsAliases = this.friendsAliases();
            Set<String> everyoneAliases = this.everyoneAliases();

            BroadcastScope broadcastScope = BroadcastScope.NONE;
            LinkedHashSet<String> directTokens = new LinkedHashSet<>();

            for (String token : message.split("\\s+")) {
                if (token.length() < 2 || token.charAt(0) != '@') {
                    continue;
                }

                String raw = token.substring(1);
                String aliasCandidate = trimTrailingPunctuation(raw).toLowerCase();

                BroadcastScope scope = this.classifyAlias(aliasCandidate, everyoneAliases, friendsAliases, roomAliases);

                if (scope != BroadcastScope.NONE) {
                    if (scope.ordinal() > broadcastScope.ordinal()) {
                        broadcastScope = scope;
                    }
                } else if (!raw.isEmpty()) {
                    directTokens.add(raw);
                }
            }

            if (broadcastScope == BroadcastScope.EVERYONE && !sender.hasPermission(PERMISSION_EVERYONE)) {
                broadcastScope = BroadcastScope.NONE;
            } else if (broadcastScope == BroadcastScope.FRIENDS && !sender.hasPermission(PERMISSION_FRIENDS)) {
                broadcastScope = BroadcastScope.NONE;
            }

            if (broadcastScope == BroadcastScope.NONE && directTokens.isEmpty()) {
                return;
            }

            if (broadcastScope != BroadcastScope.NONE) {
                long roomCooldownMs = configuration().getInt("mentions.room.cooldown.ms", 15000);
                Long lastRoom = this.roomBroadcastCooldowns.get(senderId);
                if (lastRoom != null && (now - lastRoom) < roomCooldownMs) {
                    return;
                }
            }

            int maxTargets = configuration().getInt("mentions.max.targets", 50);
            if (maxTargets <= 0) maxTargets = 1;
            int maxDirectTokens = Math.min(directTokens.size(), maxTargets);

            List<Habbo> targets = new ArrayList<>();
            Set<Integer> seen = new HashSet<>();

            switch (broadcastScope) {
                case EVERYONE:
                    this.collectEveryoneTargets(senderId, targets, seen, maxTargets);
                    break;
                case FRIENDS:
                    this.collectFriendsTargets(sender, senderId, targets, seen, maxTargets);
                    break;
                case ROOM:
                    this.collectRoomTargets(room, senderId, targets, seen, maxTargets, true);
                    break;
                case NONE:
                default:
                    int processed = 0;
                    for (String token : directTokens) {
                        if (processed++ >= maxDirectTokens) break;
                        Habbo habbo = this.resolveHabbo(room, token);
                        if (habbo == null || habbo.getHabboInfo().getId() == senderId) {
                            continue;
                        }
                        if (!acceptsMention(habbo, false)) {
                            continue;
                        }
                        if (seen.add(habbo.getHabboInfo().getId())) {
                            targets.add(habbo);
                        }
                        if (targets.size() >= maxTargets) {
                            break;
                        }
                    }
                    break;
            }

            if (targets.isEmpty()) {
                return;
            }

            if (!tryAcquire(this.cooldowns, senderId, cooldownMs)) return;
            if (broadcastScope != BroadcastScope.NONE) this.roomBroadcastCooldowns.put(senderId, now);
            this.pruneCooldownsIfDue(now);

            int mentionType =
                    (broadcastScope != BroadcastScope.NONE) ? HabboMention.TYPE_ROOM : HabboMention.TYPE_DIRECT;
            int timestamp = Emulator.getIntUnixTimestamp();
            String roomName = truncate(room.getName(), ROOM_NAME_MAX_LENGTH);
            String storedMessage = truncate(message, MESSAGE_MAX_LENGTH);

            Set<Integer> mutedRecipients = mutePreferences().mutedRecipients(senderId);
            for (Habbo target : targets) {
                if (mutedRecipients.contains(target.getHabboInfo().getId())) continue;
                long pair = ((long) senderId << 32) | (target.getHabboInfo().getId() & 0xffffffffL);
                long recipientWindow = Math.max(10000, configuration().getInt("mentions.recipient.cooldown.ms", 10000));
                if (this.recipientCooldowns.size() >= 100000)
                    this.recipientCooldowns.entrySet().removeIf(e -> now - e.getValue() >= recipientWindow);
                if (this.recipientCooldowns.size() >= 100000) continue;
                if (!acquire(this.recipientCooldowns, pair, now, recipientWindow)) continue;
                this.sendWhisperNotification(target, sender, room, storedMessage);
                this.store(target, sender, room, storedMessage, mentionType, timestamp, roomName);
            }
        } catch (Exception e) {
            LOGGER.error("Failed to process mentions.", e);
        }
    }

    private void sendWhisperNotification(Habbo target, Habbo sender, Room sourceRoom, String message) {
        if (target == null || target.getClient() == null || target.getHabboInfo() == null) {
            return;
        }
        if (target.getHabboInfo().getId() == sender.getHabboInfo().getId()) {
            return;
        }

        String notification = "@" + sender.getHabboInfo().getUsername() + " ti ha taggato: " + message;

        Room targetRoom = target.getHabboInfo().getCurrentRoom();
        if (targetRoom == sourceRoom && sender.getRoomUnit().isInRoom()) {
            target.getClient()
                    .sendResponse(new RoomUserWhisperComposer(
                            new RoomChatMessage(notification, sender, target, RoomChatMessageBubbles.NORMAL)));
            return;
        }

        target.whisper(notification, RoomChatMessageBubbles.NORMAL);
    }

    private void collectRoomTargets(
            Room room, int senderId, List<Habbo> targets, Set<Integer> seen, int maxTargets, boolean isBroadcast) {
        for (Habbo habbo : room.getHabbos()) {
            if (habbo == null || habbo.getHabboInfo().getId() == senderId) continue;
            if (!acceptsMention(habbo, isBroadcast)) continue;
            if (seen.add(habbo.getHabboInfo().getId())) targets.add(habbo);
            if (targets.size() >= maxTargets) break;
        }
    }

    private void collectFriendsTargets(
            Habbo sender, int senderId, List<Habbo> targets, Set<Integer> seen, int maxTargets) {
        if (sender.getMessenger() == null) return;
        HabboManager habboManager = environment().getHabboManager();
        for (MessengerBuddy buddy : sender.getMessenger().getFriends().values()) {
            if (buddy == null) continue;
            int buddyId = buddy.getId();
            if (buddyId == senderId) continue;
            Habbo online = habboManager.getHabbo(buddyId);
            if (online == null) continue;
            if (!acceptsMention(online, true)) continue;
            if (seen.add(buddyId)) targets.add(online);
            if (targets.size() >= maxTargets) break;
        }
    }

    private void collectEveryoneTargets(int senderId, List<Habbo> targets, Set<Integer> seen, int maxTargets) {
        for (Habbo habbo : environment().getHabboManager().getOnlineHabbos().values()) {
            if (habbo == null || habbo.getHabboInfo().getId() == senderId) continue;
            if (!acceptsMention(habbo, true)) continue;
            if (seen.add(habbo.getHabboInfo().getId())) targets.add(habbo);
            if (targets.size() >= maxTargets) break;
        }
    }

    private boolean acceptsMention(Habbo recipient, boolean isBroadcast) {
        if (recipient == null) return false;
        if (recipient.getClient() == null) return false;
        if (recipient.getHabboStats() == null) return false;
        if (!recipient.getHabboStats().mentionsEnabled()) return false;
        if (isBroadcast && !recipient.getHabboStats().massMentionsEnabled()) return false;
        return true;
    }

    private void store(
            Habbo target, Habbo sender, Room room, String message, int mentionType, int timestamp, String roomName) {
        try (Connection connection = database().getDataSource().getConnection();
                PreparedStatement statement = connection.prepareStatement(
                        "INSERT INTO habbo_mentions (target_user_id, sender_user_id, sender_username, room_id, room_name, message, mention_type, timestamp, `read`) VALUES (?, ?, ?, ?, ?, ?, ?, ?, 0)",
                        Statement.RETURN_GENERATED_KEYS)) {
            statement.setInt(1, target.getHabboInfo().getId());
            statement.setInt(2, sender.getHabboInfo().getId());
            statement.setString(3, sender.getHabboInfo().getUsername());
            statement.setInt(4, room.getId());
            statement.setString(5, roomName);
            statement.setString(6, message);
            statement.setInt(7, mentionType);
            statement.setInt(8, timestamp);
            statement.executeUpdate();

            int generatedId = 0;
            try (ResultSet keys = statement.getGeneratedKeys()) {
                if (keys.next()) {
                    generatedId = keys.getInt(1);
                }
            }

            if (generatedId <= 0) {
                return;
            }

            HabboMention mention = new HabboMention(
                    target.getHabboInfo().getId(),
                    generatedId,
                    sender,
                    room,
                    roomName,
                    message,
                    mentionType,
                    timestamp);

            if (target.getClient() != null) {
                target.getClient()
                        .sendResponse(new com.eu.habbo.messages.outgoing.mentions.MentionReceivedComposer(mention));
            }
        } catch (SQLException e) {
            LOGGER.error("Failed to store mention.", e);
        }
    }

    public List<HabboMention> getMentions(int userId, int limit) {
        List<HabboMention> mentions = new ArrayList<>();
        if (limit <= 0) limit = 50;
        if (limit > 200) limit = 200;
        try (Connection connection = database().getDataSource().getConnection();
                PreparedStatement statement = connection.prepareStatement(
                        "SELECT habbo_mentions.*, users.look AS sender_figure FROM habbo_mentions LEFT JOIN users ON users.id = habbo_mentions.sender_user_id WHERE target_user_id = ? AND NOT EXISTS (SELECT 1 FROM user_mention_mutes m WHERE m.user_id = habbo_mentions.target_user_id AND m.muted_user_id = habbo_mentions.sender_user_id) ORDER BY habbo_mentions.id DESC LIMIT ?")) {
            statement.setInt(1, userId);
            statement.setInt(2, limit);
            try (ResultSet set = statement.executeQuery()) {
                while (set.next()) {
                    mentions.add(new HabboMention(set));
                }
            }
        } catch (SQLException e) {
            LOGGER.error("Failed to load mentions.", e);
        }
        return mentions;
    }

    public void markRead(int userId, int mode, int mentionId) {
        if (mode != 0 && mode != 1) return;
        if (mode == 1 && mentionId <= 0) return;

        String query = mode == 1
                ? "UPDATE habbo_mentions SET `read` = 1 WHERE target_user_id = ? AND id = ? AND `read` = 0"
                : "UPDATE habbo_mentions SET `read` = 1 WHERE target_user_id = ? AND `read` = 0";
        try (Connection connection = database().getDataSource().getConnection();
                PreparedStatement statement = connection.prepareStatement(query)) {
            statement.setInt(1, userId);
            if (mode == 1) {
                statement.setInt(2, mentionId);
            }
            statement.executeUpdate();
        } catch (SQLException e) {
            LOGGER.error("Failed to mark mentions as read.", e);
        }
    }

    public void delete(int userId, int mentionId) {
        if (mentionId <= 0) return;
        try (Connection connection = database().getDataSource().getConnection();
                PreparedStatement statement =
                        connection.prepareStatement("DELETE FROM habbo_mentions WHERE target_user_id = ? AND id = ?")) {
            statement.setInt(1, userId);
            statement.setInt(2, mentionId);
            statement.executeUpdate();
        } catch (SQLException e) {
            LOGGER.error("Failed to delete mention.", e);
        }
    }

    public boolean tryAcquireRequestList(int userId) {
        long cooldownMs = configuration().getInt("mentions.request.cooldown.ms", 2000);
        return tryAcquire(this.requestListCooldowns, userId, cooldownMs);
    }

    public boolean tryAcquireMarkRead(int userId, int mode) {
        long cooldownMs;
        ConcurrentHashMap<Integer, Long> bucket;
        if (mode == 1) {
            cooldownMs = configuration().getInt("mentions.markread.cooldown.ms", 500);
            bucket = this.markReadCooldowns;
        } else {
            cooldownMs = configuration().getInt("mentions.markall.cooldown.ms", 5000);
            bucket = this.markAllCooldowns;
        }
        return tryAcquire(bucket, userId, cooldownMs);
    }

    public boolean tryAcquireFollow(int userId) {
        return tryAcquire(this.followCooldowns, userId, 1000);
    }

    public boolean tryAcquireSettings(int userId) {
        return tryAcquire(this.settingsCooldowns, userId, 300);
    }

    public boolean tryAcquireDelete(int userId) {
        long cooldownMs = configuration().getInt("mentions.delete.cooldown.ms", 500);
        return tryAcquire(this.deleteCooldowns, userId, cooldownMs);
    }

    private boolean tryAcquire(ConcurrentHashMap<Integer, Long> bucket, int userId, long cooldownMs) {
        long now = System.currentTimeMillis();
        if (!acquire(bucket, userId, now, cooldownMs)) return false;
        this.pruneCooldownsIfDue(now);
        return true;
    }

    static <K> boolean acquire(ConcurrentHashMap<K, Long> bucket, K key, long now, long window) {
        java.util.concurrent.atomic.AtomicBoolean accepted = new java.util.concurrent.atomic.AtomicBoolean(false);
        bucket.compute(key, (id, last) -> {
            if (last != null && now - last < window) return last;
            accepted.set(true);
            return now;
        });
        return accepted.get();
    }

    private void pruneCooldownsIfDue(long now) {
        if (now - this.lastPrune < PRUNE_INTERVAL_MS) return;
        this.lastPrune = now;

        long mentionWindow = configuration().getInt("mentions.cooldown.ms", 3000);
        long roomWindow = configuration().getInt("mentions.room.cooldown.ms", 15000);
        long requestWindow = configuration().getInt("mentions.request.cooldown.ms", 2000);
        long markReadWindow = configuration().getInt("mentions.markread.cooldown.ms", 500);
        long markAllWindow = configuration().getInt("mentions.markall.cooldown.ms", 5000);
        long deleteWindow = configuration().getInt("mentions.delete.cooldown.ms", 500);

        prune(this.cooldowns, now, mentionWindow);
        prune(this.roomBroadcastCooldowns, now, roomWindow);
        prune(this.requestListCooldowns, now, requestWindow);
        prune(this.markReadCooldowns, now, markReadWindow);
        prune(this.markAllCooldowns, now, markAllWindow);
        prune(this.deleteCooldowns, now, deleteWindow);
        prune(this.settingsCooldowns, now, 300);
        prune(this.followCooldowns, now, 1000);
        long recipientWindow = Math.max(10000, configuration().getInt("mentions.recipient.cooldown.ms", 10000));
        this.recipientCooldowns.entrySet().removeIf(e -> now - e.getValue() >= recipientWindow);
    }

    private static void prune(ConcurrentHashMap<Integer, Long> bucket, long now, long windowMs) {
        Iterator<Map.Entry<Integer, Long>> it = bucket.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<Integer, Long> entry = it.next();
            Long value = entry.getValue();
            if (value == null || (now - value) >= windowMs) {
                it.remove();
            }
        }
    }

    private static final String TRAILING_PUNCTUATION = ".,!?;:)]}\"'";

    private static String trimTrailingPunctuation(String value) {
        int end = value.length();
        while (end > 0 && TRAILING_PUNCTUATION.indexOf(value.charAt(end - 1)) >= 0) {
            end--;
        }
        return value.substring(0, end);
    }

    private static String truncate(String value, int max) {
        if (value == null) return "";
        if (value.length() <= max) return value;
        return value.substring(0, max);
    }

    private boolean isBotOrPetName(Room room, String token) {
        if (room == null || token == null || token.isEmpty()) return false;

        List<com.eu.habbo.habbohotel.bots.Bot> bots = room.getBots(token);
        if (bots != null && !bots.isEmpty()) return true;

        if (room.getUnitManager() != null && room.getUnitManager().getPets() != null) {
            for (com.eu.habbo.habbohotel.pets.Pet pet : room.getUnitManager().getPets()) {
                if (pet != null && pet.getName() != null && pet.getName().equalsIgnoreCase(token)) {
                    return true;
                }
            }
        }

        return false;
    }

    private Habbo resolveHabbo(Room room, String rawToken) {
        if (isBotOrPetName(room, rawToken)) {
            return null;
        }
        String trimmedForBotCheck = trimTrailingPunctuation(rawToken);
        if (!trimmedForBotCheck.equals(rawToken) && isBotOrPetName(room, trimmedForBotCheck)) {
            return null;
        }

        Habbo habbo = room.getHabbo(rawToken);
        if (habbo != null) {
            return habbo;
        }

        HabboManager habboManager = environment().getHabboManager();
        habbo = habboManager.getHabbo(rawToken);
        if (habbo != null) {
            return habbo;
        }
        String trimmed = trimTrailingPunctuation(rawToken);
        if (!trimmed.isEmpty() && !trimmed.equals(rawToken)) {
            habbo = room.getHabbo(trimmed);
            if (habbo != null) {
                return habbo;
            }
            return habboManager.getHabbo(trimmed);
        }
        return null;
    }

    private static com.eu.habbo.core.ConfigurationManager configuration() {
        return Emulator.getConfig();
    }

    private static com.eu.habbo.database.Database database() {
        return Emulator.getDatabase();
    }

    private static com.eu.habbo.habbohotel.GameEnvironment environment() {
        return Emulator.getGameEnvironment();
    }

    public static MentionManager runtime() {
        return environment().getMentionManager();
    }

    public MentionMuteRepository mutePreferences() {
        return new MentionMuteRepository(database().getDataSource());
    }

    public Habbo findOnlineHabbo(int id) {
        return environment().getHabboManager().getHabbo(id);
    }

    public int storeLimit() {
        return configuration().getInt("mentions.store.limit", 50);
    }
}
