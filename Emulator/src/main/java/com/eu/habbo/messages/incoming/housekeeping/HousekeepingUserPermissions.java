package com.eu.habbo.messages.incoming.housekeeping;

import com.eu.habbo.WiredPlatform;
import com.eu.habbo.database.SqlQueries;
import com.eu.habbo.habbohotel.commands.PermissionOverrideAdministration;
import com.eu.habbo.habbohotel.permissions.PermissionSetting;
import com.eu.habbo.habbohotel.permissions.UserPermissionOverrides;
import com.eu.habbo.habbohotel.users.Habbo;
import com.eu.habbo.habbohotel.users.HabboInfo;
import com.eu.habbo.messages.outgoing.housekeeping.HousekeepingActionResultComposer;
import com.eu.habbo.messages.outgoing.housekeeping.HousekeepingListComposer;
import com.eu.habbo.messages.outgoing.users.UserPerksComposer;
import com.eu.habbo.messages.outgoing.users.UserPermissionsComposer;
import java.sql.SQLException;
import java.util.List;

/** Personal exceptions use the same store, audit and policy as :perm. */
final class HousekeepingUserPermissions {
    static final String ACTION = "user.permission.set";

    private HousekeepingUserPermissions() {}

    static boolean editable(Habbo actor, HabboInfo target) {
        return target != null
                && actor.getHabboInfo().getId() != target.getId()
                && HousekeepingTargetRankGuard.canTargetRank(
                        actor, target.getRank().getId());
    }

    static HousekeepingListComposer list(String key, int userId, Habbo actor) {
        if (!actor.hasPermission(HousekeepingAreas.PERMISSIONS)
                || !actor.hasPermission(HousekeepingAreas.USER_PERMISSIONS))
            return HousekeepingListComposer.failure(key, userId, HousekeepingAccess.DENIED_MESSAGE);
        HabboInfo target = WiredPlatform.gameEnvironment().getHabboManager().getHabboInfo(userId);
        if (userId <= 0 || target == null)
            return HousekeepingListComposer.failure(key, userId, "housekeeping.error.invalid_input");
        var manager = WiredPlatform.gameEnvironment().getPermissionsManager();
        var personal = manager.getOverrides().snapshot(userId);
        if (!personal.available()) return HousekeepingListComposer.failure(key, userId, "housekeeping.list.failed");
        long now = System.currentTimeMillis();
        boolean canEdit = editable(actor, target);
        try {
            List<List<String>> rows = SqlQueries.query(
                    "SELECT permission_key, comment, max_value FROM permission_definitions ORDER BY permission_key",
                    row -> {
                        String permission = row.getString("permission_key");
                        var base = target.getRank().getPermissions().get(permission);
                        int rankValue = base == null ? 0 : UserPermissionOverrides.valueOf(base.setting);
                        var override = personal.active(permission, now);
                        int effective =
                                override == null ? rankValue : UserPermissionOverrides.valueOf(override.setting());
                        return List.of(
                                permission,
                                row.getString("comment") == null ? "" : row.getString("comment"),
                                "" + row.getInt("max_value"),
                                "" + rankValue,
                                override == null ? "-1" : "" + UserPermissionOverrides.valueOf(override.setting()),
                                "" + effective,
                                canEdit ? "1" : "0",
                                override == null ? "0" : "" + override.expiresAt());
                    });
            return new HousekeepingListComposer(
                    key,
                    userId,
                    true,
                    "",
                    List.of("permission", "comment", "max", "rank", "override", "effective", "editable", "expires"),
                    rows);
        } catch (SqlQueries.DataAccessException failure) {
            return HousekeepingListComposer.failure(key, userId, "housekeeping.list.failed");
        }
    }

    static HousekeepingActionResultComposer change(Habbo actor, int userId, String key, int value) {
        if (!actor.hasPermission(HousekeepingAreas.USER_PERMISSIONS)) return failure(HousekeepingAccess.DENIED_MESSAGE);
        try {
            var maximum = HousekeepingPermissionMatrix.maxValue(key);
            if (userId <= 0 || maximum.isEmpty() || value < -1 || value > Math.min(2, maximum.get()))
                return failure("housekeeping.error.invalid_input");
            var environment = WiredPlatform.gameEnvironment();
            HabboInfo target = environment.getHabboManager().getHabboInfo(userId);
            if (target == null) return failure("housekeeping.error.invalid_input");
            if (!editable(actor, target)) return failure("housekeeping.error.rank_too_high");
            PermissionOverrideAdministration.change(
                    actor,
                    target,
                    key,
                    value == -1 ? null : PermissionSetting.fromString("" + value),
                    0,
                    "housekeeping",
                    "",
                    "acc_housekeeping",
                    HousekeepingAreas.PERMISSIONS,
                    HousekeepingAreas.USER_PERMISSIONS);
            Habbo online = environment.getHabboManager().getHabbo(userId);
            if (online != null && online.getClient() != null) {
                online.getClient().sendResponse(new UserPermissionsComposer(online));
                online.getClient().sendResponse(new UserPerksComposer(online));
                online.getClient()
                        .sendResponse(new com.eu.habbo.messages.outgoing.commands.AvailableCommandsComposer(
                                environment.getCommandHandler().getCommandsForUser(online),
                                environment.getPermissionsManager().getAllRanks()));
            }
            return new HousekeepingActionResultComposer(ACTION, true, userId, "");
        } catch (SecurityException denied) {
            return failure(HousekeepingAccess.DENIED_MESSAGE);
        } catch (IllegalArgumentException invalid) {
            return failure("housekeeping.error.invalid_input");
        } catch (SQLException | SqlQueries.DataAccessException failed) {
            return failure("housekeeping.error.permission_failed");
        }
    }

    private static HousekeepingActionResultComposer failure(String message) {
        return new HousekeepingActionResultComposer(ACTION, false, 0, message);
    }
}
