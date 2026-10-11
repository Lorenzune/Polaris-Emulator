package com.eu.habbo.habbohotel.permissions;

import com.eu.habbo.WiredPlatform;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.IntFunction;
import java.util.function.LongSupplier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Bounded cache. Failure is distinct from an empty result and denies authorization. */
public final class UserPermissionOverrides {
    private static final Logger LOGGER = LoggerFactory.getLogger(UserPermissionOverrides.class);
    static final long CACHE_MS = 300_000;
    static final long RETRY_MS = 5_000;
    static final int MAX_PER_USER = 256;

    public record UserOverride(
            String key, PermissionSetting setting, long expiresAt, String reason, int createdBy, String createdByName) {
        public boolean activeAt(long now) {
            return expiresAt == 0 || expiresAt > now;
        }
    }

    public record Snapshot(boolean available, Map<String, UserOverride> values, long cacheUntil, long revision) {
        public UserOverride active(String key, long now) {
            UserOverride value = values.get(key);
            return value != null && value.activeAt(now) ? value : null;
        }
    }

    @FunctionalInterface
    public interface MutationGuard {
        void authorize(int actorRank, int targetRank, UserOverride previous) throws SQLException;
    }

    private final Cache<Integer, Snapshot> cache = Caffeine.newBuilder()
            .maximumWeight(200_000)
            .weigher((Integer user, Snapshot value) ->
                    Math.max(10, value.values().size() + 1))
            .expireAfterAccess(java.time.Duration.ofMinutes(5))
            .build();
    private final Object[] locks = new Object[256];
    private final IntFunction<List<UserOverride>> loader;
    private final LongSupplier clock;
    private final java.util.concurrent.atomic.AtomicLong revision = new java.util.concurrent.atomic.AtomicLong();

    public UserPermissionOverrides() {
        this(UserPermissionOverrides::load, System::currentTimeMillis);
    }

    UserPermissionOverrides(IntFunction<List<UserOverride>> loader, LongSupplier clock) {
        this.loader = loader;
        this.clock = clock;
        java.util.Arrays.setAll(locks, index -> new Object());
    }

    public Snapshot snapshot(int userId) {
        long now = clock.getAsLong();
        Snapshot value = cache.getIfPresent(userId);
        if (value != null && now < value.cacheUntil() && value.revision() == revision.get()) return value;
        synchronized (lock(userId)) {
            value = cache.getIfPresent(userId);
            if (value != null && now < value.cacheUntil() && value.revision() == revision.get()) return value;
            long version = revision.get();
            try {
                List<UserOverride> rows = loader.apply(userId);
                if (rows.size() > MAX_PER_USER) throw new IllegalStateException("Too many permission overrides");
                Map<String, UserOverride> values = new HashMap<>();
                for (UserOverride row : rows) values.put(row.key(), row);
                value = new Snapshot(true, Map.copyOf(values), now + CACHE_MS, version);
            } catch (RuntimeException failed) {
                LOGGER.warn(
                        "Permission overrides unavailable for user {}; checks denied until retry: {}",
                        userId,
                        failed.getClass().getSimpleName());
                value = new Snapshot(false, Map.of(), now + RETRY_MS, version);
            }
            if (revision.get() != version) return new Snapshot(false, Map.of(), now, revision.get());
            cache.put(userId, value);
            return value;
        }
    }

    public void invalidate(int userId) {
        synchronized (lock(userId)) {
            cache.invalidate(userId);
        }
    }

    public void invalidateAll() {
        revision.incrementAndGet();
        cache.invalidateAll();
    }

    private Object lock(int userId) {
        return locks[Math.floorMod(userId, locks.length)];
    }

    public boolean change(
            Connection connection,
            int userId,
            String key,
            PermissionSetting setting,
            long expiresAt,
            PermissionAuditLog.Actor actor,
            MutationGuard guard)
            throws SQLException {
        if (userId <= 0
                || actor.id() <= 0
                || userId == actor.id()
                || key == null
                || !key.matches("(?:acc|cmd)_[a-z0-9_]{1,60}")
                || key.length() > 64
                || expiresAt < 0
                || setting != null && expiresAt != 0 && expiresAt <= clock.getAsLong())
            throw new IllegalArgumentException("Invalid permission override");
        if (!connection.getAutoCommit()) throw new SQLException("Override requires its own transaction");
        {
            connection.setAutoCommit(false);
            try {
                Map<Integer, Integer> ranks = new HashMap<>();
                try (PreparedStatement query = connection.prepareStatement(
                        "SELECT id, `rank` FROM users WHERE id IN (?, ?) ORDER BY id FOR UPDATE")) {
                    query.setInt(1, actor.id());
                    query.setInt(2, userId);
                    try (ResultSet row = query.executeQuery()) {
                        while (row.next()) ranks.put(row.getInt(1), row.getInt(2));
                    }
                }
                if (ranks.size() != 2) throw new SQLException("Unknown actor or target");
                UserOverride previous = readOne(connection, userId, key);
                guard.authorize(ranks.get(actor.id()), ranks.get(userId), previous);
                if (setting == null && (previous == null || !previous.activeAt(clock.getAsLong()))) {
                    connection.commit();
                    return false;
                }
                if (setting != null) {
                    // Expired entries cannot consume the per-user administration limit.
                    try (PreparedStatement cleanup = connection.prepareStatement(
                            "DELETE FROM user_permission_overrides WHERE user_id = ? AND expires_at > 0 AND expires_at <= ?")) {
                        cleanup.setInt(1, userId);
                        cleanup.setLong(2, clock.getAsLong());
                        cleanup.executeUpdate();
                    }
                    try (PreparedStatement count = connection.prepareStatement(
                            "SELECT COUNT(*) FROM user_permission_overrides WHERE user_id = ?")) {
                        count.setInt(1, userId);
                        try (ResultSet row = count.executeQuery()) {
                            if (row.next()
                                    && row.getInt(1) >= MAX_PER_USER
                                    && (previous == null || !previous.activeAt(clock.getAsLong())))
                                throw new IllegalArgumentException("Too many overrides; remove old entries first");
                        }
                    }
                    try (PreparedStatement update = connection.prepareStatement(
                            "INSERT INTO user_permission_overrides (user_id, permission_key, value, expires_at, reason, created_by, created_by_name, created_at) VALUES (?, ?, ?, ?, ?, ?, ?, ?) ON DUPLICATE KEY UPDATE value=VALUES(value), expires_at=VALUES(expires_at), reason=VALUES(reason), created_by=VALUES(created_by), created_by_name=VALUES(created_by_name), created_at=VALUES(created_at)")) {
                        update.setInt(1, userId);
                        update.setString(2, key);
                        update.setInt(3, valueOf(setting));
                        update.setLong(4, expiresAt);
                        update.setString(5, PermissionAuditLog.clip(actor.reason(), 255));
                        update.setInt(6, actor.id());
                        update.setString(7, PermissionAuditLog.clip(actor.name(), 64));
                        update.setLong(8, clock.getAsLong());
                        update.executeUpdate();
                    }
                } else {
                    try (PreparedStatement delete = connection.prepareStatement(
                            "DELETE FROM user_permission_overrides WHERE user_id = ? AND permission_key = ?")) {
                        delete.setInt(1, userId);
                        delete.setString(2, key);
                        delete.executeUpdate();
                    }
                }
                PermissionAuditLog.append(
                        connection,
                        actor,
                        setting == null ? "override_unset" : "override_set",
                        "user",
                        userId,
                        key,
                        describe(previous),
                        setting == null ? "-" : valueOf(setting) + ";expires=" + expiresAt);
                connection.commit();
                invalidate(userId);
                return true;
            } catch (SQLException | RuntimeException failure) {
                connection.rollback();
                throw failure;
            } finally {
                connection.setAutoCommit(true);
            }
        }
    }

    private static UserOverride readOne(Connection connection, int userId, String key) throws SQLException {
        try (PreparedStatement query = connection.prepareStatement(
                "SELECT permission_key, value, expires_at, reason, created_by, created_by_name FROM user_permission_overrides WHERE user_id = ? AND permission_key = ? FOR UPDATE")) {
            query.setInt(1, userId);
            query.setString(2, key);
            try (ResultSet row = query.executeQuery()) {
                return row.next() ? map(row) : null;
            }
        }
    }

    /** Fresh principal state on the already locked administration transaction. */
    public Snapshot readForAdministration(Connection connection, int userId) throws SQLException {
        Map<String, UserOverride> values = new HashMap<>();
        long now = clock.getAsLong();
        try (PreparedStatement query = connection.prepareStatement(
                "SELECT permission_key, value, expires_at, reason, created_by, created_by_name FROM user_permission_overrides WHERE user_id = ? AND (expires_at = 0 OR expires_at > ?) ORDER BY permission_key LIMIT 257")) {
            query.setInt(1, userId);
            query.setLong(2, now);
            try (ResultSet rows = query.executeQuery()) {
                while (rows.next()) {
                    var value = map(rows);
                    values.put(value.key(), value);
                }
            }
        }
        if (values.size() > MAX_PER_USER) throw new SQLException("Too many actor overrides");
        return new Snapshot(true, Map.copyOf(values), now, revision.get());
    }

    private static List<UserOverride> load(int userId) {
        try (Connection connection = WiredPlatform.database().getDataSource().getConnection();
                PreparedStatement query = connection.prepareStatement(
                        "SELECT permission_key, value, expires_at, reason, created_by, created_by_name FROM user_permission_overrides WHERE user_id = ? AND (expires_at = 0 OR expires_at > ?) ORDER BY permission_key LIMIT 257")) {
            query.setInt(1, userId);
            query.setLong(2, System.currentTimeMillis());
            List<UserOverride> values = new ArrayList<>();
            try (ResultSet row = query.executeQuery()) {
                while (row.next()) values.add(map(row));
            }
            return values;
        } catch (SQLException failure) {
            throw new IllegalStateException("Override database unavailable", failure);
        }
    }

    private static UserOverride map(ResultSet row) throws SQLException {
        int value = row.getInt("value");
        long expires = row.getLong("expires_at");
        if (value < 0 || value > 2 || expires < 0) throw new SQLException("Invalid stored override");
        return new UserOverride(
                row.getString("permission_key"),
                PermissionSetting.fromString("" + value),
                expires,
                row.getString("reason"),
                row.getInt("created_by"),
                row.getString("created_by_name"));
    }

    public static int valueOf(PermissionSetting setting) {
        return switch (setting) {
            case DISALLOWED -> 0;
            case ALLOWED -> 1;
            case ROOM_OWNER -> 2;
        };
    }

    public static String describe(UserOverride value) {
        return value == null ? "-" : valueOf(value.setting()) + ";expires=" + value.expiresAt();
    }

    public static long durationMillis(String input) {
        if ("perm".equals(input) || "0".equals(input)) return 0;
        if (input == null || !input.matches("[1-9][0-9]{0,5}[mhdw]"))
            throw new IllegalArgumentException("Usa 30m, 12h, 7d, 2w oppure perm");
        long units = Long.parseLong(input.substring(0, input.length() - 1));
        long scale =
                switch (input.charAt(input.length() - 1)) {
                    case 'm' -> 60_000L;
                    case 'h' -> 3_600_000L;
                    case 'd' -> 86_400_000L;
                    default -> 604_800_000L;
                };
        long duration = Math.multiplyExact(units, scale);
        if (duration > 30L * 86_400_000L) throw new IllegalArgumentException("Durata massima: 30 giorni");
        return duration;
    }
}
