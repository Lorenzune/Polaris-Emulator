package com.eu.habbo.habbohotel.quests;

import com.eu.habbo.Emulator;
import com.eu.habbo.habbohotel.economy.EconomyLedger;
import com.eu.habbo.habbohotel.economy.EconomyOperation;
import com.eu.habbo.habbohotel.gameclients.GameClient;
import com.eu.habbo.habbohotel.items.interactions.wired.extra.WiredExtraQuestBase;
import com.eu.habbo.habbohotel.users.Habbo;
import com.eu.habbo.habbohotel.users.HabboBadge;
import com.eu.habbo.habbohotel.users.LedgerWalletMutation;
import com.eu.habbo.habbohotel.wired.core.WiredManager;
import com.eu.habbo.messages.outgoing.inventory.InventoryBadgesComposer;
import com.eu.habbo.messages.outgoing.inventory.InventoryRefreshComposer;
import com.eu.habbo.messages.outgoing.quests.WiredQuestStateComposer;
import com.eu.habbo.messages.outgoing.users.UserCreditsComposer;
import com.eu.habbo.messages.outgoing.users.UserCurrencyComposer;
import com.google.gson.reflect.TypeToken;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.slf4j.LoggerFactory;

/** Hotel-wide durable quest progress. Completion, ledger rewards, items and badges commit together. */
public final class QuestManager {
    private static final QuestManager INSTANCE = new QuestManager();

    public static QuestManager getInstance() {
        return INSTANCE;
    }

    private volatile Map<String, QuestDefinition> definitions = Map.of();
    private volatile boolean loaded;
    private final Map<Integer, Map<String, QuestProgress>> cache = new ConcurrentHashMap<>();

    public record Row(QuestDefinition definition, QuestProgress progress, int target) {}

    public record State(boolean canEdit, List<Row> quests, List<String> completed, String error) {}

    public boolean canEdit(GameClient client) {
        return client != null
                && client.getHabbo() != null
                && client.getHabbo().hasPermission(com.eu.habbo.habbohotel.permissions.Permission.ACC_QUEST_EDITOR);
    }

    private synchronized void load() {
        if (loaded) return;
        Map<String, QuestDefinition> next = new LinkedHashMap<>();
        try (var c = connection();
                var s = c.prepareStatement("SELECT code,configuration FROM wired_quest_definitions");
                var rs = s.executeQuery()) {
            while (rs.next()) {
                var d = WiredManager.getGson().fromJson(rs.getString(2), QuestDefinition.class);
                d.validate();
                next.put(d.code, d);
            }
            definitions = Map.copyOf(next);
            loaded = true;
        } catch (Exception e) {
            LoggerFactory.getLogger(QuestManager.class).error("Cannot load wired quests", e);
        }
    }

    private Connection connection() throws SQLException {
        return Emulator.getDatabase().getDataSource().getConnection();
    }

    public QuestDefinition definition(String code, boolean chain) {
        load();
        var d = definitions.get(code);
        return d != null
                        && QuestRules.available(d, definitions.values(), Emulator.getIntUnixTimestamp())
                        && d.chain == chain
                ? d
                : null;
    }

    public QuestDefinition definitionForBox(WiredExtraQuestBase box) {
        var d = definition(
                box.getQuestCode(),
                box instanceof com.eu.habbo.habbohotel.items.interactions.wired.extra.WiredExtraQuestChain);
        return d != null && d.roomId > 0 && d.roomId == box.getRoomId() ? d : null;
    }

    public QuestProgress progress(int userId, String code) {
        if (userId <= 0) return new QuestProgress();
        try {
            return data(userId).getOrDefault(code, new QuestProgress()).copy();
        } catch (SQLException e) {
            return new QuestProgress();
        }
    }

    private Map<String, QuestProgress> parse(String json) {
        Map<String, QuestProgress> result = WiredManager.getGson()
                .fromJson(json, new TypeToken<LinkedHashMap<String, QuestProgress>>() {}.getType());
        return result == null ? new LinkedHashMap<>() : result;
    }

    private Map<String, QuestProgress> data(int userId) throws SQLException {
        var existing = cache.get(userId);
        if (existing != null) return existing;
        try (var c = connection()) {
            var result = read(c, userId);
            if (cache.size() > 4000) cache.clear();
            cache.put(userId, result);
            return result;
        }
    }

    private Map<String, QuestProgress> read(Connection c, int userId) throws SQLException {
        try (var s = c.prepareStatement("SELECT progress FROM wired_quest_progress WHERE user_id=?")) {
            s.setInt(1, userId);
            try (var rs = s.executeQuery()) {
                return rs.next() ? parse(rs.getString(1)) : new LinkedHashMap<>();
            }
        }
    }

    public State state(GameClient client, List<String> completed, String error) {
        load();
        var result = new ArrayList<Row>();
        boolean edit = canEdit(client);
        var data = client != null && client.getHabbo() != null
                ? client.getHabbo().getHabboInfo().getId()
                : 0;
        for (var d : definitions.values())
            if (edit || QuestRules.available(d, definitions.values(), Emulator.getIntUnixTimestamp()))
                result.add(new Row(d, progress(data, d.code), QuestRules.target(d, definitions.values())));
        result.sort(Comparator.comparing((Row r) -> r.definition.chainCode)
                .thenComparingInt(r -> r.definition.position)
                .thenComparing(r -> r.definition.code));
        return new State(edit, result, completed, error);
    }

    public void send(GameClient client) {
        if (client != null) client.sendResponse(new WiredQuestStateComposer(state(client, List.of(), "")));
    }

    public synchronized void save(GameClient client, QuestDefinition d) throws SQLException {
        if (!canEdit(client)) throw new IllegalArgumentException("Permessi insufficienti");
        load();
        if (d == null) throw new IllegalArgumentException("Definizione missione mancante");
        d.validate();
        if (d.roomId == 0) {
            var room = client.getHabbo().getHabboInfo().getCurrentRoom();
            if (room == null)
                throw new IllegalArgumentException(
                        "Inserisci la stanza autorizzata o salva dalla stanza della missione");
            d.roomId = room.getId();
        }
        if (Emulator.getGameEnvironment().getRoomManager().getRoom(d.roomId) == null)
            throw new IllegalArgumentException("Stanza autorizzata inesistente");
        if (d.chain && !d.chainCode.isEmpty())
            throw new IllegalArgumentException("Una catena non può appartenere a un'altra catena");
        if (!d.chainCode.isEmpty()) {
            var chain = definitions.get(d.chainCode);
            if (chain == null || !chain.chain) throw new IllegalArgumentException("Catena inesistente");
            if (definitions.values().stream()
                    .anyMatch(q -> !q.code.equals(d.code)
                            && q.enabled
                            && q.chainCode.equals(d.chainCode)
                            && q.position == d.position))
                throw new IllegalArgumentException("Ordine già usato nella catena");
        }
        var old = definitions.get(d.code);
        if (old != null && old.chain != d.chain)
            throw new IllegalArgumentException("Il tipo di una definizione esistente non può cambiare");
        if (d.reward.kind.equals("item")) {
            var item = Emulator.getGameEnvironment().getItemManager().getItem(d.reward.itemCode);
            if (item == null
                    || (item.getType() != com.eu.habbo.habbohotel.items.FurnitureType.FLOOR
                            && item.getType() != com.eu.habbo.habbohotel.items.FurnitureType.WALL))
                throw new IllegalArgumentException("Furni della ricompensa inesistente o non valido");
        }
        if (definitions.size() >= 500 && !definitions.containsKey(d.code))
            throw new IllegalArgumentException("Limite missioni raggiunto");
        try (var c = connection();
                var s = c.prepareStatement(
                        "INSERT INTO wired_quest_definitions(code,configuration,updated_at) VALUES(?,?,?) ON DUPLICATE KEY UPDATE configuration=VALUES(configuration),updated_at=VALUES(updated_at)")) {
            s.setString(1, d.code);
            s.setString(2, WiredManager.getGson().toJson(d));
            s.setInt(3, Emulator.getIntUnixTimestamp());
            s.executeUpdate();
        }
        var next = new LinkedHashMap<>(definitions);
        next.put(d.code, d);
        definitions = Map.copyOf(next);
        send(client);
        for (var room : Emulator.getGameEnvironment().getRoomManager().getActiveRooms())
            room.getUserVariableManager().requestBroadcast();
    }

    public boolean mutate(Habbo user, WiredExtraQuestBase box, String action, Integer value, boolean override) {
        if (user == null || box == null || definitionForBox(box) == null) return false;
        return mutate(user, box.getQuestCode(), action, value == null ? 0 : value, override);
    }

    public boolean mutate(Habbo user, String code, String action, int value, boolean override) {
        if (user == null) return false;
        load();
        try {
            boolean changed =
                    LedgerWalletMutation.coordinated(user, () -> mutateLocked(user, code, action, value, override));
            if (!changed) send(user.getClient());
            return changed;
        } catch (Exception e) {
            LoggerFactory.getLogger(QuestManager.class)
                    .error(
                            "Quest mutation failed for {} / {}",
                            user.getHabboInfo().getId(),
                            code,
                            e);
            if (user.getClient() != null)
                user.getClient()
                        .sendResponse(new WiredQuestStateComposer(state(
                                user.getClient(),
                                List.of(),
                                e instanceof IllegalArgumentException
                                        ? e.getMessage()
                                        : "Operazione non riuscita. Nessuna ricompensa trasferita.")));
            return false;
        }
    }

    private boolean mutateLocked(Habbo user, String code, String action, int value, boolean override)
            throws SQLException {
        int userId = user.getHabboInfo().getId();
        Map<String, QuestProgress> before;
        QuestRules.Change change;
        var itemIds = new ArrayList<Integer>();
        var balances = new HashMap<Integer, Integer>();
        var badgeCodes = new ArrayList<String>();
        try (var c = connection()) {
            c.setAutoCommit(false);
            try {
                try (var s = c.prepareStatement("SELECT id FROM users WHERE id=? FOR UPDATE")) {
                    s.setInt(1, userId);
                    try (var rs = s.executeQuery()) {
                        if (!rs.next()) throw new SQLException("User not found");
                    }
                }
                before = read(c, userId);
                change = QuestRules.apply(
                        definitions.values(), before, code, action, value, override, Emulator.getIntUnixTimestamp());
                if (!change.changed()) {
                    c.rollback();
                    return false;
                }
                for (var d : change.completed()) grant(c, user, d, itemIds, balances, badgeCodes);
                try (var s = c.prepareStatement(
                        "INSERT INTO wired_quest_progress(user_id,progress,updated_at) VALUES(?,?,?) ON DUPLICATE KEY UPDATE progress=VALUES(progress),updated_at=VALUES(updated_at)")) {
                    s.setInt(1, userId);
                    s.setString(2, WiredManager.getGson().toJson(change.progress()));
                    s.setInt(3, Emulator.getIntUnixTimestamp());
                    s.executeUpdate();
                }
                c.commit();
            } catch (Exception e) {
                c.rollback();
                throw e;
            }
        }
        cache.put(userId, change.progress());
        // All inventory and UI publication happens after the durable commit.
        try {
            balances.forEach((type, balance) -> LedgerWalletMutation.applyCommitted(user, type, balance));
            for (int id : itemIds) {
                var item = Emulator.getGameEnvironment().getItemManager().loadHabboItem(id);
                if (item != null) user.getInventory().getItemsComponent().addItem(item);
            }
            if (!itemIds.isEmpty()) user.getClient().sendResponse(new InventoryRefreshComposer());
            if (!badgeCodes.isEmpty())
                try (var c = connection();
                        var s = c.prepareStatement("SELECT * FROM users_badges WHERE user_id=?")) {
                    s.setInt(1, userId);
                    try (var rs = s.executeQuery()) {
                        while (rs.next())
                            if (badgeCodes.contains(rs.getString("badge_code")))
                                user.getInventory().getBadgesComponent().addBadge(new HabboBadge(rs, user));
                    }
                }
            if (!badgeCodes.isEmpty()) user.getClient().sendResponse(new InventoryBadgesComposer(user));
            if (!balances.isEmpty()) {
                user.getClient().sendResponse(new UserCreditsComposer(user));
                user.getClient().sendResponse(new UserCurrencyComposer(user));
            }
        } catch (Exception e) {
            LoggerFactory.getLogger(QuestManager.class).error("Quest committed; failed to refresh rewards", e);
        }
        try {
            if (user.getClient() != null)
                user.getClient()
                        .sendResponse(new WiredQuestStateComposer(state(
                                user.getClient(),
                                change.completed().stream().map(d -> d.code).toList(),
                                "")));
            var room = user.getHabboInfo().getCurrentRoom();
            if (room != null) room.getUserVariableManager().questProgressChanged(userId, before, change.progress());
        } catch (Exception e) {
            LoggerFactory.getLogger(QuestManager.class).error("Quest committed; failed to refresh progress", e);
        }
        return true;
    }

    private void grant(
            Connection c,
            Habbo user,
            QuestDefinition d,
            List<Integer> items,
            Map<Integer, Integer> balances,
            List<String> badges)
            throws SQLException {
        int userId = user.getHabboInfo().getId();
        var r = d.reward;
        if (r.kind.equals("currency")) {
            String key = java.util
                    .UUID
                    .nameUUIDFromBytes((userId + ":" + d.code).getBytes(java.nio.charset.StandardCharsets.UTF_8))
                    .toString();
            var result = EconomyLedger.apply(
                    c,
                    new EconomyOperation(
                            "quest:" + key,
                            userId,
                            userId,
                            "quest_reward",
                            "quest_complete",
                            r.currencyType,
                            r.amount,
                            null,
                            d.code));
            balances.put(r.currencyType, result.balanceAfter());
        } else if (r.kind.equals("item")) {
            var base = Emulator.getGameEnvironment().getItemManager().getItem(r.itemCode);
            if (base == null) throw new SQLException("Reward item removed");
            for (int i = 0; i < r.amount; i++)
                try (var s = c.prepareStatement(
                        "INSERT INTO items(user_id,item_id,extra_data,limited_data) VALUES(?,?,?,'0:0')",
                        Statement.RETURN_GENERATED_KEYS)) {
                    s.setInt(1, userId);
                    s.setInt(2, base.getId());
                    s.setString(3, "");
                    s.executeUpdate();
                    try (var rs = s.getGeneratedKeys()) {
                        if (!rs.next()) throw new SQLException("Missing reward item id");
                        items.add(rs.getInt(1));
                    }
                }
        } else if (r.kind.equals("badge")) {
            try (var s = c.prepareStatement("SELECT id FROM users_badges WHERE user_id=? AND badge_code=?")) {
                s.setInt(1, userId);
                s.setString(2, r.badge);
                try (var rs = s.executeQuery()) {
                    if (rs.next()) return;
                }
            }
            try (var s = c.prepareStatement("INSERT INTO users_badges(user_id,badge_code,slot_id) VALUES(?,?,0)")) {
                s.setInt(1, userId);
                s.setString(2, r.badge);
                s.executeUpdate();
            }
            badges.add(r.badge);
        }
    }
}
