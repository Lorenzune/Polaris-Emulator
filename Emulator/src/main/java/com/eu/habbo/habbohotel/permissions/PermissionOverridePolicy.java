package com.eu.habbo.habbohotel.permissions;

import com.eu.habbo.habbohotel.users.Habbo;

/** A delegated grant cannot exceed its author's scope or authorizing lease. */
public final class PermissionOverridePolicy {
    private PermissionOverridePolicy() {}

    public static void authorizeGrant(
            Habbo actor, PermissionsManager manager, String key, PermissionSetting setting, long expiresAt, long now) {
        authorizeGrant(
                actor,
                manager,
                key,
                setting,
                expiresAt,
                now,
                manager.getOverrides().snapshot(actor.getHabboInfo().getId()));
    }

    public static void authorizeGrant(
            Habbo actor,
            PermissionsManager manager,
            String key,
            PermissionSetting setting,
            long expiresAt,
            long now,
            UserPermissionOverrides.Snapshot personal) {
        authorizeGrant(actor, manager, key, setting, expiresAt, now, personal, "cmd_perm_edit", "cmd_perm");
    }

    public static void authorizeGrant(
            Habbo actor,
            PermissionsManager manager,
            String key,
            PermissionSetting setting,
            long expiresAt,
            long now,
            UserPermissionOverrides.Snapshot personal,
            String... requiredPermissions) {
        if (!personal.available()) throw new SecurityException("Permessi non verificabili");
        if (setting == PermissionSetting.DISALLOWED) return;
        boolean scoped = setting == PermissionSetting.ROOM_OWNER;
        var own = personal.active(key, now);
        boolean allowed = own == null
                ? manager.hasBasePermission(actor, key, scoped)
                : own.setting() == PermissionSetting.ALLOWED || own.setting() == PermissionSetting.ROOM_OWNER && scoped;
        if (!allowed) throw new SecurityException("Non possiedi questo permesso con lo scope richiesto");
        for (String source : java.util.stream.Stream.concat(
                        java.util.stream.Stream.of(key), java.util.Arrays.stream(requiredPermissions))
                .toList()) {
            var override = personal.active(source, now);
            if (override != null && override.expiresAt() > 0) limit(expiresAt, override.expiresAt());
            long lease = actor.getHabboInfo().getTemporaryRankExpiresAt();
            Rank current = actor.getHabboInfo().getRank();
            Rank permanent = actor.getHabboInfo().getPermanentRank();
            boolean roomScope = source.equals(key) && scoped;
            if (lease > now
                    && current.hasPermission(source, roomScope)
                    && (permanent == null || !permanent.hasPermission(source, roomScope))) limit(expiresAt, lease);
        }
    }

    private static void limit(long requested, long ceiling) {
        if (requested == 0 || requested > ceiling)
            throw new SecurityException("La concessione non può durare oltre i tuoi permessi temporanei");
    }
}
