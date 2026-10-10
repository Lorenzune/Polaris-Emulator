package com.eu.habbo.habbohotel.users;

import com.eu.habbo.Emulator;
import com.eu.habbo.WiredPlatform;
import com.eu.habbo.database.SqlQueries;
import com.eu.habbo.habbohotel.economy.EconomyLedger;
import com.eu.habbo.habbohotel.economy.EconomyOperation;
import com.eu.habbo.habbohotel.economy.EconomyOperationId;
import com.eu.habbo.habbohotel.modtool.ModToolBan;
import com.eu.habbo.habbohotel.permissions.Permission;
import com.eu.habbo.habbohotel.permissions.PermissionsManager;
import com.eu.habbo.habbohotel.permissions.Rank;
import com.eu.habbo.habbohotel.permissions.TemporaryRankStore;
import com.eu.habbo.messages.ServerMessage;
import com.eu.habbo.messages.outgoing.catalog.CatalogModeComposer;
import com.eu.habbo.messages.outgoing.catalog.CatalogUpdatedComposer;
import com.eu.habbo.messages.outgoing.catalog.DiscountComposer;
import com.eu.habbo.messages.outgoing.catalog.GiftConfigurationComposer;
import com.eu.habbo.messages.outgoing.catalog.RecyclerLogicComposer;
import com.eu.habbo.messages.outgoing.catalog.marketplace.MarketplaceConfigComposer;
import com.eu.habbo.messages.outgoing.generic.alerts.GenericAlertComposer;
import com.eu.habbo.messages.outgoing.modtool.ModToolComposer;
import com.eu.habbo.messages.outgoing.users.UserPerksComposer;
import com.eu.habbo.messages.outgoing.users.UserPermissionsComposer;
import com.eu.habbo.plugin.events.users.UserRankChangedEvent;
import com.eu.habbo.plugin.events.users.UserRegisteredEvent;
import com.eu.habbo.threading.ThreadPooling;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.AbstractMap;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executor;
import java.util.concurrent.ScheduledFuture;
import java.util.function.IntPredicate;
import javax.sql.DataSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class HabboManager {

    private static final Logger LOGGER = LoggerFactory.getLogger(HabboManager.class);

    // Configuration. Loaded from database & updated accordingly.
    public static volatile String WELCOME_MESSAGE = "";
    public static boolean NAMECHANGE_ENABLED = false;

    private final ConcurrentHashMap<Integer, Habbo> onlineHabbos;
    private final ConcurrentHashMap<String, Habbo> onlineHabbosByName;
    private final ConcurrentHashMap<Integer, String> usernameCache = new ConcurrentHashMap<>();
    private final DisconnectPersistenceGate disconnectPersistence;
    private DataSource rankDataSource;
    private PermissionsManager rankPermissions;
    private ThreadPooling rankThreading;
    private ScheduledFuture<?> rankExpiryTask;
    private volatile boolean rankExpiryDisposed;

    public HabboManager() {
        this(Runnable::run);
    }

    public HabboManager(Executor persistenceExecutor) {
        long millis = System.currentTimeMillis();

        this.onlineHabbos = new ConcurrentHashMap<>();
        this.onlineHabbosByName = new ConcurrentHashMap<>();
        this.disconnectPersistence = new DisconnectPersistenceGate(persistenceExecutor);

        LOGGER.info("Habbo Manager -> Loaded! ({} MS)", System.currentTimeMillis() - millis);
    }

    public static HabboInfo getOfflineHabboInfo(int id) {
        try {
            return SqlQueries.queryOne("SELECT * FROM users WHERE id = ? LIMIT 1", HabboInfo::new, id)
                    .orElse(null);
        } catch (SqlQueries.DataAccessException e) {
            LOGGER.error("Caught SQL exception", e);
            return null;
        }
    }

    public static HabboInfo getOfflineHabboInfo(String username) {
        try {
            return SqlQueries.queryOne("SELECT * FROM users WHERE username = ? LIMIT 1", HabboInfo::new, username)
                    .orElse(null);
        } catch (SqlQueries.DataAccessException e) {
            LOGGER.error("Caught SQL exception", e);
            return null;
        }
    }

    public synchronized void addHabbo(Habbo habbo) {
        if (this.rankDataSource != null) {
            try (Connection connection = this.rankDataSource.getConnection()) {
                refreshTemporaryRankForLogin(connection, habbo.getHabboInfo());
            } catch (SQLException failure) {
                throw new IllegalStateException("Cannot establish current rank", failure);
            }
        }
        this.onlineHabbos.put(habbo.getHabboInfo().getId(), habbo);
        this.onlineHabbosByName.put(habbo.getHabboInfo().getUsername().toLowerCase(), habbo);
    }

    public void removeHabbo(Habbo habbo) {
        this.onlineHabbos.remove(habbo.getHabboInfo().getId());
        this.onlineHabbosByName.remove(habbo.getHabboInfo().getUsername().toLowerCase());
    }

    public Habbo getHabbo(int id) {
        return this.onlineHabbos.get(id);
    }

    public Habbo getHabbo(String username) {
        return this.onlineHabbosByName.get(username.toLowerCase());
    }

    public Habbo loadHabbo(String sso) {
        Habbo habbo;
        int userId = 0;

        try (Connection connection = Emulator.getDatabase().getDataSource().getConnection();
                PreparedStatement statement = connection.prepareStatement(
                        "SELECT id FROM users WHERE auth_ticket = ? AND (auth_ticket_expires_at IS NULL OR auth_ticket_expires_at >= NOW()) LIMIT 1")) {
            statement.setString(1, sso);
            try (ResultSet s = statement.executeQuery()) {
                if (s.next()) {
                    userId = s.getInt("id");
                }
            }
            statement.close();
        } catch (SQLException e) {
            habbo = null;
            LOGGER.error("Caught SQL exception", e);
        }

        if (!this.awaitDisconnectPersistence(userId)) {
            return null;
        }

        habbo = this.cloneCheck(userId);
        if (habbo != null) {
            habbo.alert(Emulator.getTexts().getValue("loggedin.elsewhere"));
            Emulator.getGameServer().getGameClientManager().forceDisposeClient(habbo.getClient());
            habbo = null;
        }

        if (!this.awaitDisconnectPersistence(userId)) {
            return null;
        }

        ModToolBan ban = Emulator.getGameEnvironment().getModToolManager().checkForBan(userId);
        if (ban != null) {
            return null;
        }

        try (Connection connection = Emulator.getDatabase().getDataSource().getConnection();
                PreparedStatement statement = connection.prepareStatement(
                        "SELECT * FROM users WHERE auth_ticket = ? AND (auth_ticket_expires_at IS NULL OR auth_ticket_expires_at >= NOW()) LIMIT 1")) {
            statement.setString(1, sso);
            try (ResultSet set = statement.executeQuery()) {
                if (set.next()) {
                    habbo = new Habbo(set);
                    refreshTemporaryRankForLogin(connection, habbo.getHabboInfo());

                    if (habbo.getHabboInfo().firstVisit) {
                        Emulator.getPluginManager().fireEvent(new UserRegisteredEvent(habbo));
                    }

                    // NB: il ticket SSO NON viene svuotato qui di proposito. Dietro
                    // Cloudflare il WebSocket viene droppato e il client ritenta più
                    // volte con lo STESSO ticket: se lo consumassimo al primo uso, i
                    // retry (e l'hard-refresh) fallirebbero con "non-existing SSO token".
                    // Il ticket resta valido fino alla scadenza (auth_ticket_expires_at,
                    // TTL gestito dal CMS) o finché il CMS non ne scrive uno nuovo / logout.
                }
            }
        } catch (SQLException e) {
            habbo = null;
            LOGGER.error("Caught SQL exception", e);
        } catch (Exception ex) {
            habbo = null;
            LOGGER.error("Caught exception", ex);
        }

        return habbo;
    }

    private boolean awaitDisconnectPersistence(int userId) {
        if (this.disconnectPersistence.await(userId)) {
            return true;
        }
        LOGGER.warn("Interrupted while waiting for disconnect persistence for user {}", userId);
        return false;
    }

    DisconnectPersistenceGate.Registration beginDisconnectPersistence(int userId) {
        return this.disconnectPersistence.begin(userId);
    }

    void submitDisconnectPersistence(DisconnectPersistenceGate.Registration registration, Runnable persistence) {
        this.disconnectPersistence.submit(registration, persistence);
    }

    void cancelDisconnectPersistence(DisconnectPersistenceGate.Registration registration) {
        this.disconnectPersistence.cancel(registration);
    }

    public HabboInfo getHabboInfo(int id) {
        if (this.getHabbo(id) == null) {
            return getOfflineHabboInfo(id);
        }
        return this.getHabbo(id).getHabboInfo();
    }

    public String getCachedUsername(int id) {
        String cached = this.usernameCache.get(id);
        if (cached != null) return cached;

        Habbo online = this.getHabbo(id);
        if (online != null) {
            String name = online.getHabboInfo().getUsername();
            this.usernameCache.put(id, name);
            return name;
        }

        HabboInfo offline = getOfflineHabboInfo(id);
        if (offline != null) {
            String name = offline.getUsername();
            this.usernameCache.put(id, name);
            return name;
        }
        return "Unknown";
    }

    public String getCachedLook(int id) {
        Habbo online = this.getHabbo(id);
        if (online != null) {
            return online.getHabboInfo().getLook();
        }

        HabboInfo offline = getOfflineHabboInfo(id);
        if (offline != null) {
            return offline.getLook();
        }
        return "";
    }

    public int getOnlineCount() {
        return this.onlineHabbos.size();
    }

    public Habbo cloneCheck(int id) {
        return Emulator.getGameServer().getGameClientManager().getHabbo(id);
    }

    public void sendPacketToHabbosWithPermission(ServerMessage message, String perm) {
        for (Habbo habbo : this.onlineHabbos.values()) {
            if (habbo.hasPermission(perm)) {
                habbo.getClient().sendResponse(message);
            }
        }
    }

    public ConcurrentHashMap<Integer, Habbo> getOnlineHabbos() {
        return this.onlineHabbos;
    }

    public synchronized void dispose() {
        this.rankExpiryDisposed = true;
        if (this.rankExpiryTask != null) this.rankExpiryTask.cancel(false);

        //

        LOGGER.info("Habbo Manager -> Disposed!");
    }

    public List<HabboInfo> getCloneAccounts(Habbo habbo, int limit) {
        try {
            return SqlQueries.query(
                    "SELECT * FROM users WHERE (ip_register = ? OR ip_current = ?) AND id != ? ORDER BY id DESC LIMIT ?",
                    HabboInfo::new,
                    habbo.getHabboInfo().getIpRegister(),
                    habbo.getHabboInfo().getIpLogin(),
                    habbo.getHabboInfo().getId(),
                    limit);
        } catch (SqlQueries.DataAccessException e) {
            LOGGER.error("Caught SQL exception", e);
            return new ArrayList<>();
        }
    }

    public List<Map.Entry<Integer, String>> getNameChanges(int userId, int limit) {
        try {
            return SqlQueries.query(
                    "SELECT timestamp, new_name FROM namechange_log WHERE user_id = ? ORDER by timestamp DESC LIMIT ?",
                    rs -> new AbstractMap.SimpleEntry<>(rs.getInt("timestamp"), rs.getString("new_name")),
                    userId,
                    limit);
        } catch (SqlQueries.DataAccessException e) {
            LOGGER.error("Caught SQL exception", e);
            return Collections.emptyList();
        }
    }

    public synchronized void setRank(int userId, int rankId) throws Exception {
        Habbo habbo = this.getHabbo(userId);

        if (!Emulator.getGameEnvironment().getPermissionsManager().rankExists(rankId)) {
            throw new Exception("Rank ID (" + rankId + ") does not exist");
        }
        Rank newRank = Emulator.getGameEnvironment().getPermissionsManager().getRank(rankId);

        DataSource source = this.rankDataSource != null
                ? this.rankDataSource
                : WiredPlatform.database().getDataSource();
        try (Connection connection = source.getConnection()) {
            TemporaryRankStore.permanent(connection, userId, rankId);
        }
        publishRank(habbo, newRank);
    }

    private void publishRank(Habbo habbo, Rank newRank) {
        publishRank(habbo, newRank, null);
    }

    private void publishRank(Habbo habbo, Rank newRank, TemporaryRankStore.State lease) {
        Rank oldRank = habbo != null ? habbo.getHabboInfo().getStoredRank() : null;
        if (habbo != null) {
            if (lease != null && lease.previousRank() != null)
                habbo.getHabboInfo()
                        .setTemporaryRank(
                                newRank, fallbackRank(lease.previousRank()), lease.expiresAt(), this::fallbackRank);
            else habbo.getHabboInfo().setRank(newRank);
        }
        if (habbo != null && habbo.getHabboStats() != null) {
            if (!oldRank.getBadge().isEmpty()) {
                habbo.deleteBadge(habbo.getInventory().getBadgesComponent().getBadge(oldRank.getBadge()));
            }
            if (oldRank.getRoomEffect() > 0) {
                habbo.getInventory().getEffectsComponent().effects.remove(oldRank.getRoomEffect());
            }

            if (!newRank.getBadge().isEmpty()) {
                habbo.addBadge(newRank.getBadge());
            }

            if (newRank.getRoomEffect() > 0) {
                habbo.getInventory()
                        .getEffectsComponent()
                        .createRankEffect(habbo.getHabboInfo().getRank().getRoomEffect());
            }

            habbo.getClient().sendResponse(new UserPermissionsComposer(habbo));
            habbo.getClient().sendResponse(new UserPerksComposer(habbo));

            if (habbo.hasPermission(Permission.ACC_SUPPORTTOOL)) {
                habbo.getClient().sendResponse(new ModToolComposer(habbo));
            }
            habbo.getHabboInfo().run();

            habbo.getClient().sendResponse(new CatalogUpdatedComposer());
            habbo.getClient().sendResponse(new CatalogModeComposer(0));
            habbo.getClient().sendResponse(new DiscountComposer());
            habbo.getClient().sendResponse(new MarketplaceConfigComposer());
            habbo.getClient().sendResponse(new GiftConfigurationComposer());
            habbo.getClient().sendResponse(new RecyclerLogicComposer());
            habbo.alert(Emulator.getTexts()
                    .getValue("commands.generic.cmd_give_rank.new_rank")
                    .replace("id", newRank.getName()));
        }

        Emulator.getPluginManager().fireEvent(new UserRankChangedEvent(habbo));
    }

    /** Starts one bounded shared-pool task, never one timer/thread per user. */
    public synchronized void startTemporaryRanks() {
        if (this.rankDataSource != null || this.rankExpiryDisposed) return;
        this.rankDataSource = WiredPlatform.database().getDataSource();
        this.rankPermissions = WiredPlatform.gameEnvironment().getPermissionsManager();
        this.rankThreading = WiredPlatform.threading();
        scheduleRankExpiry();
    }

    private void scheduleRankExpiry() {
        if (!this.rankExpiryDisposed) this.rankExpiryTask = this.rankThreading.run(this::expireTemporaryRanks, 60_000L);
    }

    private void expireTemporaryRanks() {
        try {
            List<Integer> due = new ArrayList<>();
            try (Connection connection = this.rankDataSource.getConnection();
                    PreparedStatement query = connection.prepareStatement(
                            "SELECT tr.user_id FROM user_temporary_ranks tr INNER JOIN users u ON u.id = tr.user_id WHERE tr.expires_at <= ? ORDER BY tr.expires_at, tr.user_id LIMIT 100")) {
                query.setLong(1, System.currentTimeMillis());
                try (ResultSet rows = query.executeQuery()) {
                    while (rows.next()) due.add(rows.getInt(1));
                }
            }
            for (int userId : due) {
                synchronized (this) {
                    if (this.rankExpiryDisposed) break;
                    try (Connection connection = this.rankDataSource.getConnection()) {
                        var state = TemporaryRankStore.refresh(connection, userId, System.currentTimeMillis());
                        Habbo online = this.getHabbo(userId);
                        if (online != null) publishRank(online, fallbackRank(state.rank()), state);
                    }
                }
            }
        } catch (Exception failure) {
            LOGGER.error("Temporary rank expiry will retry; online leases still expire locally", failure);
        } finally {
            synchronized (this) {
                scheduleRankExpiry();
            }
        }
    }

    private Rank fallbackRank(int rankId) {
        Rank rank = this.rankPermissions.getRank(rankId);
        if (rank != null) return rank;
        Rank base = this.rankPermissions.getRank(1);
        return base == null ? new Rank(1) : base;
    }

    public synchronized void grantTemporaryRank(
            int userId, int rankId, long duration, int actorId, String reason, IntPredicate allowed) throws Exception {
        if (this.rankDataSource == null || !this.rankPermissions.rankExists(rankId))
            throw new IllegalStateException("Temporary ranks unavailable");
        try (Connection connection = this.rankDataSource.getConnection()) {
            var state = TemporaryRankStore.grant(
                    connection, userId, rankId, duration, actorId, reason, System.currentTimeMillis(), allowed);
            Habbo online = this.getHabbo(userId);
            if (online != null) publishRank(online, fallbackRank(state.rank()), state);
        }
    }

    public synchronized void refreshTemporaryRankForResume(HabboInfo info) throws SQLException {
        if (this.rankDataSource == null) return;
        try (Connection connection = this.rankDataSource.getConnection()) {
            refreshTemporaryRankForLogin(connection, info);
        }
    }

    public void refreshTemporaryRankForLogin(Connection connection, HabboInfo info) throws SQLException {
        if (this.rankDataSource == null) return;
        var state = TemporaryRankStore.refresh(connection, info.getId(), System.currentTimeMillis());
        info.setRank(fallbackRank(state.rank()));
        if (state.previousRank() != null)
            info.setTemporaryRank(
                    fallbackRank(state.rank()),
                    fallbackRank(state.previousRank()),
                    state.expiresAt(),
                    this::fallbackRank);
    }

    public void giveCredits(int userId, int credits) {
        Habbo habbo = this.getHabbo(userId);
        if (habbo != null) {
            habbo.giveCredits(credits, "staff.command.credits");
        } else {
            try {
                EconomyLedger.execute(new EconomyOperation(
                        EconomyOperationId.create("credits:" + userId),
                        userId,
                        userId,
                        credits > 0 ? "credit_grant" : "credit_debit",
                        "staff.command.credits",
                        EconomyLedger.CREDITS,
                        credits,
                        null,
                        "offline"));
            } catch (Exception e) {
                LOGGER.error("Unable to apply audited offline credit mutation for user {}", userId, e);
            }
        }
    }

    public void staffAlert(String message) {
        message = Emulator.getTexts().getValue("commands.generic.cmd_staffalert.title") + "\r\n" + message;
        ServerMessage msg = new GenericAlertComposer(message).compose();
        Emulator.getGameEnvironment().getHabboManager().sendPacketToHabbosWithPermission(msg, "cmd_staffalert");
    }
}
