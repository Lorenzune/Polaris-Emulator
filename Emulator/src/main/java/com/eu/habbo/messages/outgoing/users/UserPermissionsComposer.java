package com.eu.habbo.messages.outgoing.users;

import com.eu.habbo.habbohotel.permissions.Permission;
import com.eu.habbo.habbohotel.permissions.Rank;
import com.eu.habbo.habbohotel.users.Habbo;
import com.eu.habbo.messages.ServerMessage;
import com.eu.habbo.messages.outgoing.MessageComposer;
import com.eu.habbo.messages.outgoing.Outgoing;
import java.util.Map;

/**
 * Sends the full per-user permission state to the connected client.
 *
 * Wire layout (each trailing block is guarded by `bytesAvailable` on
 * the client so older Nitro builds keep parsing the prefix and stop):
 *
 *   int     clubLevel
 *   int     rank.level                           // mapped to securityLevel on the client
 *   bool    isAmbassador                         // legacy ACC_AMBASSADOR flag
 *   --- rank metadata (Arcturus ≥ 4.2.10) ---
 *   int     rank.id
 *   string  rank.name                            // permission_ranks.rank_name
 *   string  rank.badge
 *   string  rank.prefix
 *   string  rank.prefixColor
 *   --- resolved permission map (Arcturus ≥ 4.2.10) ---
 *   int     count
 *   loop:   string permission_key + int value   // 1 = ALLOWED, 2 = ROOM_OWNER
 *
 * The resolved map includes rank and plugin grants, with active per-user
 * overrides taking precedence. Unavailable override storage yields an empty map.
 * The packet layout is unchanged. Login, rank changes, permission reloads and
 * the shared expiry refresh keep the client view synchronized.
 */
public class UserPermissionsComposer extends MessageComposer {
    private final int clubLevel;

    private final Habbo habbo;

    public UserPermissionsComposer(Habbo habbo) {
        this.clubLevel = habbo.getHabboStats().hasActiveClub() ? 2 : 0;
        this.habbo = habbo;
    }

    @Override
    protected ServerMessage composeInternal() {
        this.response.init(Outgoing.UserPermissionsComposer);
        this.response.appendInt(this.clubLevel);

        Rank rank = this.habbo.getHabboInfo().getRank();

        this.response.appendInt(rank.getLevel());
        this.response.appendBoolean(this.habbo.hasPermission(Permission.ACC_AMBASSADOR));

        // Rank metadata
        this.response.appendInt(rank.getId());
        this.response.appendString(rank.getName());
        this.response.appendString(rank.getBadge());
        this.response.appendString(rank.getPrefix());
        this.response.appendString(rank.getPrefixColor());

        Map<String, Integer> resolved = com.eu.habbo.WiredPlatform.gameEnvironment()
                .getPermissionsManager()
                .getEffectivePermissions(this.habbo);

        this.response.appendInt(resolved.size());

        for (Map.Entry<String, Integer> entry : resolved.entrySet()) {
            this.response.appendString(entry.getKey());
            this.response.appendInt(entry.getValue());
        }

        return this.response;
    }

    public int getClubLevel() {
        return clubLevel;
    }

    public Habbo getHabbo() {
        return habbo;
    }
}
