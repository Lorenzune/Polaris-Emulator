package com.eu.habbo.habbohotel.commands;

import com.eu.habbo.WiredPlatform;
import com.eu.habbo.habbohotel.permissions.PermissionAuditLog;
import com.eu.habbo.habbohotel.permissions.PermissionOverridePolicy;
import com.eu.habbo.habbohotel.permissions.PermissionSetting;
import com.eu.habbo.habbohotel.permissions.Rank;
import com.eu.habbo.habbohotel.users.Habbo;
import com.eu.habbo.habbohotel.users.HabboInfo;
import java.sql.SQLException;

/** Shared transactional administration for the command and housekeeping. */
public final class PermissionOverrideAdministration {
    private PermissionOverrideAdministration() {}

    public static boolean change(
            Habbo actor,
            HabboInfo info,
            String key,
            PermissionSetting setting,
            long expires,
            String via,
            String reason,
            String... requiredPermissions)
            throws SQLException {
        var manager = WiredPlatform.gameEnvironment().getPermissionsManager();
        if (requiredPermissions.length == 0
                || java.util.Arrays.stream(requiredPermissions)
                        .anyMatch(permission -> !actor.hasPermission(permission)))
            throw new SecurityException("Autorizzazione negata");
        if (!manager.isPermissionDefined(key)) throw new IllegalArgumentException("Chiave permesso sconosciuta");
        if (!CommandTargetGuard.canTarget(actor, info))
            throw new SecurityException("Non puoi modificare questo utente");
        var auditActor = new PermissionAuditLog.Actor(
                actor.getHabboInfo().getId(), actor.getHabboInfo().getUsername(), via, reason);
        final PermissionSetting requested = setting;
        final long expiry = expires;
        try (var connection = WiredPlatform.database().getDataSource().getConnection()) {
            boolean changed = manager.getOverrides()
                    .change(
                            connection,
                            info.getId(),
                            key,
                            setting,
                            expires,
                            auditActor,
                            (actorRank, targetRank, previous) -> {
                                // Revalidate against locked persistent ranks as well as live permissions.
                                var verified = manager.getOverrides()
                                        .readForAdministration(
                                                connection, actor.getHabboInfo().getId());
                                if (actorRank != actor.getHabboInfo().getRank().getId()
                                        || java.util.Arrays.stream(requiredPermissions)
                                                .anyMatch(permission ->
                                                        !manager.hasPermission(actor, permission, false, verified))
                                        || !manager.isPermissionDefined(key)
                                        || !CommandTargetGuard.canAssignRank(actor, manager.getRank(targetRank)))
                                    throw new SecurityException("Autorizzazione o gerarchia cambiata");
                                if (requested != null)
                                    PermissionOverridePolicy.authorizeGrant(
                                            actor,
                                            manager,
                                            key,
                                            requested,
                                            expiry,
                                            System.currentTimeMillis(),
                                            verified,
                                            requiredPermissions);
                                else {
                                    Rank rank = manager.getRank(targetRank);
                                    PermissionSetting restored = rank.hasPermission(key, false)
                                            ? PermissionSetting.ALLOWED
                                            : rank.hasPermission(key, true)
                                                    ? PermissionSetting.ROOM_OWNER
                                                    : PermissionSetting.DISALLOWED;
                                    // Plugin grants must also be considered when removing a denial.
                                    Habbo online = WiredPlatform.gameEnvironment()
                                            .getHabboManager()
                                            .getHabbo(info.getId());
                                    if (online != null && manager.hasBasePermission(online, key, false))
                                        restored = PermissionSetting.ALLOWED;
                                    if (online == null
                                            && !WiredPlatform.pluginManager()
                                                    .getPlugins()
                                                    .isEmpty()) {
                                        // Offline plugins cannot be evaluated safely; require the
                                        // full scope rather than silently lifting a stronger denial.
                                        restored = PermissionSetting.ALLOWED;
                                    }
                                    PermissionOverridePolicy.authorizeGrant(
                                            actor,
                                            manager,
                                            key,
                                            restored,
                                            0,
                                            System.currentTimeMillis(),
                                            verified,
                                            requiredPermissions);
                                }
                            });
            return changed;
        }
    }
}
