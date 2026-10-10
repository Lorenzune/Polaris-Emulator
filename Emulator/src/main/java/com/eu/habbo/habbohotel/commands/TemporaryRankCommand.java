package com.eu.habbo.habbohotel.commands;

import com.eu.habbo.WiredPlatform;
import com.eu.habbo.habbohotel.gameclients.GameClient;
import com.eu.habbo.habbohotel.permissions.TemporaryRankStore;
import com.eu.habbo.habbohotel.users.HabboManager;

/** Uses the existing give-rank authorization; temporary grants cannot exceed it. */
public final class TemporaryRankCommand extends Command {
    public TemporaryRankCommand() {
        super("cmd_give_rank", new String[] {"temprank", "ranktemporaneo"});
    }

    @Override
    public boolean handle(GameClient client, String[] params) throws Exception {
        if (params.length < 4) {
            client.getHabbo().whisper("Uso: :temprank nome rank durata [motivo] (30m, 2h, 7d; massimo 30 giorni)");
            return true;
        }
        var actor = client.getHabbo();
        var environment = WiredPlatform.gameEnvironment();
        var permissions = environment.getPermissionsManager();
        try {
            if (!actor.hasPermission(this.permission)) throw new SecurityException("Non autorizzato");
            int rankId;
            try {
                rankId = Integer.parseInt(params[2]);
            } catch (NumberFormatException ignored) {
                var named = permissions.getRankByName(params[2]);
                if (named == null) throw new IllegalArgumentException("Rank sconosciuto");
                rankId = named.getId();
            }
            var target = HabboManager.getOfflineHabboInfo(params[1]);
            if (!CommandTargetGuard.canTarget(actor, target)
                    || !CommandTargetGuard.canAssignRank(actor, permissions.getRank(rankId)))
                throw new SecurityException("Non puoi modificare questo rank o utente");
            long duration = TemporaryRankStore.durationMillis(params[3]);
            String reason =
                    params.length > 4 ? String.join(" ", java.util.Arrays.copyOfRange(params, 4, params.length)) : "";
            environment
                    .getHabboManager()
                    .grantTemporaryRank(
                            target.getId(),
                            rankId,
                            duration,
                            actor.getHabboInfo().getId(),
                            reason,
                            current -> actor.hasPermission(this.permission)
                                    && CommandTargetGuard.canAssignRank(actor, permissions.getRank(current)));
            actor.whisper("Rank temporaneo assegnato a " + target.getUsername() + " per " + params[3] + ".");
        } catch (IllegalArgumentException | SecurityException failure) {
            actor.whisper(failure.getMessage());
        }
        return true;
    }
}
