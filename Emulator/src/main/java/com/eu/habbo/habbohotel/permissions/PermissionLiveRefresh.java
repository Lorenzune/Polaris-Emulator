package com.eu.habbo.habbohotel.permissions;

import com.eu.habbo.habbohotel.commands.CommandHandler;
import com.eu.habbo.habbohotel.users.Habbo;
import com.eu.habbo.habbohotel.users.HabboManager;
import com.eu.habbo.messages.outgoing.commands.AvailableCommandsComposer;
import com.eu.habbo.messages.outgoing.users.UserPerksComposer;
import com.eu.habbo.messages.outgoing.users.UserPermissionsComposer;
import com.eu.habbo.threading.ThreadPooling;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.concurrent.ScheduledFuture;
import org.slf4j.LoggerFactory;

/** One shared, bounded refresh task; authorization expiry itself is checked on every use. */
public final class PermissionLiveRefresh implements Runnable {
    private record Signature(
            int rank, Map<String, Permission> rankValues, boolean available, Map<String, PermissionSetting> personal) {}

    private final PermissionsManager permissions;
    private final HabboManager users;
    private final CommandHandler commands;
    private final ThreadPooling threading;
    private final Cache<Integer, Signature> seen = Caffeine.newBuilder()
            .maximumSize(20_000)
            .expireAfterAccess(java.time.Duration.ofMinutes(10))
            .build();
    private Iterator<Habbo> pending;
    private volatile boolean disposed;
    private ScheduledFuture<?> task;

    public PermissionLiveRefresh(
            PermissionsManager permissions, HabboManager users, CommandHandler commands, ThreadPooling threading) {
        this.permissions = permissions;
        this.users = users;
        this.commands = commands;
        this.threading = threading;
    }

    public synchronized void start() {
        if (!disposed && task == null) task = threading.run(this, 1000L);
    }

    public synchronized void dispose() {
        disposed = true;
        if (task != null) task.cancel(false);
        pending = null;
        seen.invalidateAll();
    }

    @Override
    public void run() {
        try {
            if (disposed) return;
            if (pending == null || !pending.hasNext())
                pending = users.getOnlineHabbos().values().iterator();
            int checked = 0;
            while (!disposed && checked++ < 500 && pending.hasNext()) {
                Habbo habbo = pending.next();
                if (habbo == null || habbo.getClient() == null || habbo.getHabboInfo() == null) continue;
                refresh(habbo);
            }
        } catch (RuntimeException failure) {
            LoggerFactory.getLogger(PermissionLiveRefresh.class).warn("Permission UI refresh will retry", failure);
        } finally {
            synchronized (this) {
                if (!disposed) task = threading.run(this, 1000L);
            }
        }
    }

    void refresh(Habbo habbo) {
        int userId = habbo.getHabboInfo().getId();
        long now = System.currentTimeMillis();
        var snapshot = permissions.getOverrides().snapshot(userId);
        Map<String, PermissionSetting> active = Map.of();
        if (!snapshot.values().isEmpty()) {
            Map<String, PermissionSetting> values = new HashMap<>();
            for (var value : snapshot.values().values())
                if (value.activeAt(now)) values.put(value.key(), value.setting());
            active = Map.copyOf(values);
        }
        Rank rank = habbo.getHabboInfo().getRank();
        var signature = new Signature(rank.getId(), rank.getPermissions(), snapshot.available(), active);
        Signature old = seen.getIfPresent(userId);
        // Initial login already sent these packets. Later changes (including expiry)
        // refresh the UI; the server has no dependence on this polling task.
        if (old != null && !old.equals(signature)) {
            habbo.getClient().sendResponse(new UserPermissionsComposer(habbo));
            habbo.getClient().sendResponse(new UserPerksComposer(habbo));
            habbo.getClient()
                    .sendResponse(new AvailableCommandsComposer(
                            commands.getCommandsForUser(habbo), permissions.getAllRanks()));
        }
        seen.put(userId, signature);
    }
}
