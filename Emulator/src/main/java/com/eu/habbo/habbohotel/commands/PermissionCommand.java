package com.eu.habbo.habbohotel.commands;

import com.eu.habbo.WiredPlatform;
import com.eu.habbo.habbohotel.gameclients.GameClient;
import com.eu.habbo.habbohotel.permissions.PermissionAuditLog;
import com.eu.habbo.habbohotel.permissions.PermissionSetting;
import com.eu.habbo.habbohotel.permissions.PermissionsManager;
import com.eu.habbo.habbohotel.permissions.Rank;
import com.eu.habbo.habbohotel.permissions.UserPermissionOverrides;
import com.eu.habbo.habbohotel.users.Habbo;
import com.eu.habbo.habbohotel.users.HabboInfo;
import com.eu.habbo.habbohotel.users.HabboManager;
import com.eu.habbo.messages.outgoing.users.UserPerksComposer;
import com.eu.habbo.messages.outgoing.users.UserPermissionsComposer;
import java.sql.SQLException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/** Emulator-local administration; the actor always comes from the authenticated client. */
public final class PermissionCommand extends Command {
    private static final String USAGE = ":perm check nome chiave | list nome [pagina] | log [nome|*] [primaId]"
            + " | set nome chiave 0|1|2 [30m|12h|7d|2w|perm] [motivo] | unset nome chiave [motivo]";

    public PermissionCommand() {
        super("cmd_perm", new String[] {"perm"});
    }

    @Override
    public boolean handle(GameClient client, String[] params) {
        Habbo actor = client.getHabbo();
        if (actor == null || !actor.hasPermission(permission)) return true;
        try {
            if (params.length < 2) {
                actor.whisper(USAGE);
                return true;
            }
            switch (params[1]) {
                case "check" -> check(actor, params);
                case "list" -> list(actor, params);
                case "log" -> log(actor, params);
                case "set", "unset" -> change(actor, params);
                default -> actor.whisper(USAGE);
            }
        } catch (IllegalArgumentException | SecurityException failure) {
            actor.whisper(failure.getMessage());
        } catch (SQLException failure) {
            org.slf4j.LoggerFactory.getLogger(PermissionCommand.class)
                    .error("Permission administration failed", failure);
            actor.whisper("Operazione non confermata: database/audit non disponibile.");
        }
        return true;
    }

    private static PermissionsManager permissions() {
        return WiredPlatform.gameEnvironment().getPermissionsManager();
    }

    private static HabboInfo target(String username) {
        HabboInfo info = HabboManager.getOfflineHabboInfo(username);
        if (info == null) throw new IllegalArgumentException("Utente non trovato");
        return info;
    }

    private static void check(Habbo actor, String[] params) {
        if (params.length != 4) throw new IllegalArgumentException(USAGE);
        HabboInfo target = target(params[2]);
        PermissionsManager manager = permissions();
        var personal = manager.getOverrides().snapshot(target.getId());
        long now = System.currentTimeMillis();
        var override = personal.active(params[3], now);
        Rank rank = target.getRank();
        List<String> lines = new ArrayList<>();
        lines.add("Permesso: " + target.getUsername() + " / " + params[3]);
        lines.add("Rank: " + rank.getId() + " " + rank.getName());
        var base = rank.getPermissions().get(params[3]);
        lines.add("Valore rank: " + (base == null ? "non definito" : UserPermissionOverrides.valueOf(base.setting)));
        if (!manager.isPermissionDefined(params[3])) lines.add("Chiave non definita; non assegnabile come override.");
        lines.add("Override: "
                + (override == null
                        ? "nessuno"
                        : UserPermissionOverrides.describe(override) + " / " + override.reason()));
        Habbo online = WiredPlatform.gameEnvironment().getHabboManager().getHabbo(target.getId());
        if (!personal.available()) lines.add("Esito: NEGATO, override non verificabili (errore DB).");
        else if (online != null) {
            lines.add("Esito senza diritti stanza: " + online.hasPermission(params[3], false));
            lines.add("Esito con diritti stanza: " + online.hasPermission(params[3], true));
            lines.add("Esito include i plugin; una negazione individuale ha precedenza.");
        } else {
            PermissionSetting setting = override == null
                    ? (base == null ? PermissionSetting.DISALLOWED : base.setting)
                    : override.setting();
            lines.add("Esito rank/override: " + setting + "; plugin non verificati: utente offline.");
        }
        actor.alert(String.join("\r", lines));
    }

    private static void list(Habbo actor, String[] params) {
        if (params.length < 3 || params.length > 4) throw new IllegalArgumentException(USAGE);
        var target = target(params[2]);
        int page = params.length == 4 ? Integer.parseInt(params[3]) : 1;
        if (page < 1 || page > 18) throw new IllegalArgumentException("Pagina non valida");
        var snapshot = permissions().getOverrides().snapshot(target.getId());
        if (!snapshot.available()) throw new SecurityException("Override non verificabili: riprova più tardi");
        var values = snapshot.values().values().stream()
                .filter(value -> value.activeAt(System.currentTimeMillis()))
                .sorted(java.util.Comparator.comparing(UserPermissionOverrides.UserOverride::key))
                .toList();
        List<String> lines = new ArrayList<>();
        lines.add("Override di " + target.getUsername() + " — pagina " + page + ", totale " + values.size());
        values.stream()
                .skip((page - 1L) * 15)
                .limit(15)
                .forEach(value -> lines.add(
                        value.key() + " = " + UserPermissionOverrides.describe(value) + " / " + value.reason()));
        actor.alert(String.join("\r", lines));
    }

    private static void log(Habbo actor, String[] params) throws SQLException {
        if (params.length > 4) throw new IllegalArgumentException(USAGE);
        int targetId =
                params.length >= 3 && !"*".equals(params[2]) ? target(params[2]).getId() : 0;
        long before = params.length == 4 ? Long.parseLong(params[3]) : 0;
        if (before < 0) throw new IllegalArgumentException("ID cursore non valido");
        try (var connection = WiredPlatform.database().getDataSource().getConnection()) {
            List<String> lines = new ArrayList<>();
            for (var entry : PermissionAuditLog.recent(connection, targetId, before)) {
                lines.add("#" + entry.id() + " " + Instant.ofEpochSecond(entry.timestamp()) + " "
                        + entry.actorName() + " (" + entry.actorId() + ") " + entry.action() + " "
                        + entry.targetType() + ":" + entry.targetId() + " " + entry.subject() + " "
                        + entry.oldValue() + " -> " + entry.newValue() + " via " + entry.via() + " / "
                        + entry.reason());
            }
            if (lines.isEmpty()) lines.add("Nessuna voce di audit.");
            else lines.add("Per le voci precedenti: :perm log " + (targetId == 0 ? "*" : params[2]) + " <ultimo ID>");
            actor.alert(String.join("\r", lines));
        }
    }

    private static void change(Habbo actor, String[] params) throws SQLException {
        boolean set = "set".equals(params[1]);
        if (params.length < (set ? 5 : 4)) throw new IllegalArgumentException(USAGE);
        var manager = permissions();
        if (!actor.hasPermission("cmd_perm_edit")) throw new SecurityException("Serve cmd_perm_edit");
        HabboInfo info = target(params[2]);
        String key = params[3];
        if (!manager.isPermissionDefined(key)) throw new IllegalArgumentException("Chiave permesso sconosciuta");
        if (!CommandTargetGuard.canTarget(actor, info))
            throw new SecurityException("Non puoi modificare questo utente");
        PermissionSetting setting = null;
        long expires = 0;
        int reasonIndex = 4;
        if (set) {
            if (!params[4].matches("[012]")) throw new IllegalArgumentException("Valore ammesso: 0, 1 oppure 2");
            setting = PermissionSetting.fromString(params[4]);
            long duration = UserPermissionOverrides.durationMillis(params.length > 5 ? params[5] : "perm");
            expires = duration == 0 ? 0 : Math.addExact(System.currentTimeMillis(), duration);
            reasonIndex = 6;
        }
        String reason = params.length > reasonIndex
                ? String.join(" ", Arrays.copyOfRange(params, reasonIndex, params.length))
                : "";
        boolean changed = PermissionOverrideAdministration.change(
                actor, info, key, setting, expires, "perm", reason, "cmd_perm", "cmd_perm_edit");
        actor.whisper(changed ? "Override aggiornato e registrato nell'audit." : "Nessun override da rimuovere.");
        Habbo online = WiredPlatform.gameEnvironment().getHabboManager().getHabbo(info.getId());
        if (online != null && online.getClient() != null) {
            online.getClient().sendResponse(new UserPermissionsComposer(online));
            online.getClient().sendResponse(new UserPerksComposer(online));
            online.getClient()
                    .sendResponse(new com.eu.habbo.messages.outgoing.commands.AvailableCommandsComposer(
                            WiredPlatform.gameEnvironment().getCommandHandler().getCommandsForUser(online),
                            manager.getAllRanks()));
        }
    }
}
