package com.eu.habbo.habbohotel.users;

import com.eu.habbo.Emulator;
import com.eu.habbo.database.SqlQueries;
import com.eu.habbo.habbohotel.catalog.CatalogItem;
import com.eu.habbo.habbohotel.games.Game;
import com.eu.habbo.habbohotel.games.GamePlayer;
import com.eu.habbo.habbohotel.messenger.MessengerCategory;
import com.eu.habbo.habbohotel.navigation.NavigatorSavedSearch;
import com.eu.habbo.habbohotel.permissions.Rank;
import com.eu.habbo.habbohotel.pets.PetTasks;
import com.eu.habbo.habbohotel.pets.RideablePet;
import com.eu.habbo.habbohotel.rooms.Room;
import com.eu.habbo.habbohotel.rooms.RoomTile;
import com.eu.habbo.habbohotel.rooms.RoomUnit;
import com.eu.habbo.messages.outgoing.rooms.users.RoomUserStatusComposer;
import it.unimi.dsi.fastutil.ints.Int2IntOpenHashMap;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class HabboInfo implements Runnable {

    private static final Logger LOGGER = LoggerFactory.getLogger(HabboInfo.class);

    public boolean firstVisit = false;
    private String username;
    private String motto;
    private String look;
    private HabboGender gender;
    private String mail;
    private boolean mailVerified;
    private String sso;
    private String ipRegister;
    private String ipLogin;
    private int id;
    private int accountCreated;
    private volatile Rank rank;

    private record RankSnapshot(
            Rank current, Rank previous, long expiresAt, java.util.function.IntFunction<Rank> resolver) {}

    private volatile RankSnapshot rankSnapshot;
    private int credits;
    private int lastOnline;
    private int homeRoom;
    private boolean online;
    private int InfostandBg;
    private int InfostandStand;
    private int InfostandOverlay;
    private int InfostandCardBg;
    private int InfostandBorder;
    private int infostandDirection = 2;
    private int infostandHeadDirection = 2;
    private String infostandAction = "std";
    private int profileDirection = 2;
    private int profileHeadDirection = 2;
    private String profileAction = "std";
    private volatile int loadingRoom;
    private volatile long loadingRoomStartedAt;
    private Room currentRoom;
    private String roomEntryMethod = "door";
    private int roomEntryTeleportId = 0;
    private int roomQueueId;
    private RideablePet riding;
    private Class<? extends Game> currentGame;
    private Int2IntOpenHashMap currencies;
    // Serializes in-memory wallet reads and writes. Durable wallet changes are
    // committed through EconomyLedger and then published to this snapshot.
    private final Object currencyLock = new Object();
    private final Object ledgerMutationLock = new Object();
    private GamePlayer gamePlayer;
    private int photoRoomId;
    private int photoTimestamp;
    private String photoURL;
    private String photoJSON;
    private int webPublishTimestamp;
    private String machineID;
    private List<NavigatorSavedSearch> savedSearches = new ArrayList<>();
    private List<MessengerCategory> messengerCategories = new ArrayList<>();

    public HabboInfo(ResultSet set) {
        try {
            this.id = set.getInt("id");
            this.username = set.getString("username");
            this.motto = set.getString("motto");
            this.look = set.getString("look");
            this.gender = HabboGender.valueOf(set.getString("gender"));
            this.mail = set.getString("mail");
            try {
                this.mailVerified = set.getBoolean("mail_verified");
            } catch (SQLException ignored) {
                this.mailVerified = false;
            }
            this.sso = set.getString("auth_ticket");
            this.ipRegister = set.getString("ip_register");
            this.ipLogin = set.getString("ip_current");
            this.rank = Emulator.getGameEnvironment().getPermissionsManager().getRank(set.getInt("rank"));

            if (this.rank == null) {
                LOGGER.error("No existing rank found with id " + set.getInt("rank")
                        + ". Make sure an entry in the permissions table exists.");
                LOGGER.warn(this.username + " has an invalid rank with id " + set.getInt("rank")
                        + ". Make sure an entry in the permissions table exists.");
                this.rank =
                        Emulator.getGameEnvironment().getPermissionsManager().getRank(1);
            }

            this.accountCreated = set.getInt("account_created");
            this.credits = Math.max(0, set.getInt("credits"));
            this.homeRoom = set.getInt("home_room");
            this.lastOnline = set.getInt("last_online");
            this.machineID = set.getString("machine_id");
            this.online = false;
            this.InfostandBg = set.getInt("background_id");
            this.InfostandStand = set.getInt("background_stand_id");
            this.InfostandOverlay = set.getInt("background_overlay_id");
            this.InfostandCardBg = set.getInt("background_card_id");
            try {
                this.InfostandBorder = set.getInt("background_border_id");
            } catch (SQLException ignored) {
                this.InfostandBorder = 0;
            }
            try {
                this.infostandDirection = set.getInt("infostand_direction");
                this.infostandHeadDirection = set.getInt("infostand_head_direction");
                this.infostandAction = set.getString("infostand_action");
                if (this.infostandAction == null || this.infostandAction.isEmpty()) this.infostandAction = "std";
                this.profileDirection = set.getInt("profile_direction");
                this.profileHeadDirection = set.getInt("profile_head_direction");
                this.profileAction = set.getString("profile_action");
                if (this.profileAction == null || this.profileAction.isEmpty()) this.profileAction = "std";
            } catch (SQLException ignored) {
                this.infostandDirection = 2;
                this.infostandHeadDirection = 2;
                this.infostandAction = "std";
                this.profileDirection = 2;
                this.profileHeadDirection = 2;
                this.profileAction = "std";
            }
            this.currentRoom = null;
        } catch (SQLException e) {
            LOGGER.error("Caught SQL exception", e);
        }

        this.loadCurrencies();
        this.loadSavedSearches();
        this.loadMessengerCategories();
    }

    HabboInfo(int id, int credits) {
        this.id = id;
        this.credits = WalletBalanceMath.requireValidBalance(credits);
        this.gender = HabboGender.M;
        this.currencies = new Int2IntOpenHashMap();
    }

    private void loadCurrencies() {
        this.currencies = new Int2IntOpenHashMap();

        try {
            SqlQueries.forEach(
                    "SELECT * FROM users_currency WHERE user_id = ?",
                    rs -> this.currencies.put(rs.getInt("type"), Math.max(0, rs.getInt("amount"))),
                    this.id);
        } catch (SqlQueries.DataAccessException e) {
            LOGGER.error("Caught SQL exception", e);
        }
    }

    private void loadSavedSearches() {
        try {
            this.savedSearches = SqlQueries.query(
                    "SELECT * FROM users_saved_searches WHERE user_id = ?",
                    rs -> new NavigatorSavedSearch(
                            rs.getString("search_code"), rs.getString("filter"), rs.getInt("id")),
                    this.id);
        } catch (SqlQueries.DataAccessException e) {
            LOGGER.error("Caught SQL exception", e);
            this.savedSearches = new ArrayList<>();
        }
    }

    public void addSavedSearch(NavigatorSavedSearch search) {
        this.savedSearches.add(search);

        try (Connection connection = databaseSource().getConnection();
                PreparedStatement statement = connection.prepareStatement(
                        "INSERT INTO users_saved_searches (search_code, filter, user_id) VALUES (?, ?, ?)",
                        Statement.RETURN_GENERATED_KEYS)) {
            statement.setString(1, search.getSearchCode());
            statement.setString(2, search.getFilter());
            statement.setInt(3, this.id);
            int affectedRows = statement.executeUpdate();

            if (affectedRows == 0) {
                throw new SQLException("Creating saved search failed, no rows affected.");
            }

            try (ResultSet generatedKeys = statement.getGeneratedKeys()) {
                if (generatedKeys.next()) {
                    search.setId(generatedKeys.getInt(1));
                } else {
                    throw new SQLException("Creating saved search failed, no ID found.");
                }
            }
        } catch (SQLException e) {
            LOGGER.error("Caught SQL exception", e);
        }
    }

    public void deleteSavedSearch(NavigatorSavedSearch search) {
        this.savedSearches.remove(search);

        try {
            SqlQueries.update("DELETE FROM users_saved_searches WHERE id = ?", search.getId());
        } catch (SqlQueries.DataAccessException e) {
            LOGGER.error("Caught SQL exception", e);
        }
    }

    private void loadMessengerCategories() {
        try {
            this.messengerCategories = SqlQueries.query(
                    "SELECT * FROM messenger_categories WHERE user_id = ?",
                    rs -> new MessengerCategory(rs.getString("name"), rs.getInt("user_id"), rs.getInt("id")),
                    this.id);
        } catch (SqlQueries.DataAccessException e) {
            LOGGER.error("Caught SQL exception", e);
            this.messengerCategories = new ArrayList<>();
        }
    }

    public void addMessengerCategory(MessengerCategory category) {
        this.messengerCategories.add(category);

        try (Connection connection = databaseSource().getConnection();
                PreparedStatement statement = connection.prepareStatement(
                        "INSERT INTO messenger_categories (name, user_id) VALUES (?, ?)",
                        Statement.RETURN_GENERATED_KEYS)) {
            statement.setString(1, category.getName());
            statement.setInt(2, this.id);
            int affectedRows = statement.executeUpdate();

            if (affectedRows == 0) {
                throw new SQLException("Creating messenger category failed, no rows affected.");
            }

            try (ResultSet generatedKeys = statement.getGeneratedKeys()) {
                if (generatedKeys.next()) {
                    category.setId(generatedKeys.getInt(1));
                } else {
                    throw new SQLException("Creating messenger category failed, no ID found.");
                }
            }
        } catch (SQLException e) {
            LOGGER.error("Caught SQL exception", e);
        }
    }

    public void deleteMessengerCategory(MessengerCategory category) {
        try {
            SqlQueries.update(
                    "UPDATE messenger_friendships SET category = 0 WHERE user_one_id = ? AND category = ?",
                    this.id,
                    category.getId());
            if (SqlQueries.update(
                            "DELETE FROM messenger_categories WHERE id = ? AND user_id = ?", category.getId(), this.id)
                    > 0) {
                this.messengerCategories.remove(category);
            }
        } catch (SqlQueries.DataAccessException e) {
            LOGGER.error("Caught SQL exception", e);
        }
    }

    public MessengerCategory getMessengerCategory(int categoryId) {
        return this.messengerCategories.stream()
                .filter(category -> category.getId() == categoryId)
                .findFirst()
                .orElse(null);
    }

    public boolean renameMessengerCategory(MessengerCategory category, String name) {
        try {
            if (SqlQueries.update(
                            "UPDATE messenger_categories SET name = ? WHERE id = ? AND user_id = ?",
                            name,
                            category.getId(),
                            this.id)
                    <= 0) return false;
            category.setName(name);
            return true;
        } catch (SqlQueries.DataAccessException e) {
            LOGGER.error("Caught SQL exception", e);
            return false;
        }
    }

    public boolean moveMessengerFriendToCategory(int friendId, int categoryId) {
        try {
            return SqlQueries.update(
                            "UPDATE messenger_friendships SET category = ? WHERE user_one_id = ? AND user_two_id = ?",
                            categoryId,
                            this.id,
                            friendId)
                    > 0;
        } catch (SqlQueries.DataAccessException e) {
            LOGGER.error("Caught SQL exception", e);
            return false;
        }
    }

    public int getCurrencyAmount(int type) {
        synchronized (this.currencyLock) {
            return this.currencies.get(type);
        }
    }

    public Int2IntOpenHashMap getCurrencies() {
        // Return a snapshot under the lock: callers iterate this map, which would
        // otherwise corrupt during a concurrent addTo/put rehash.
        synchronized (this.currencyLock) {
            return new Int2IntOpenHashMap(this.currencies);
        }
    }

    public void addCurrencyAmount(int type, int amount) {
        applyLegacyWallet(new LegacyWalletMutation.Change(type, amount, false, true));
    }

    public boolean tryAddCurrencyAmount(int type, int amount) {
        return applyLegacyWallet(new LegacyWalletMutation.Change(type, amount, false, false));
    }

    /**
     * Atomically debits the two currencies used by a catalog order. The
     * affordability check and both mutations share the wallet lock, so two
     * concurrent purchase paths cannot both spend the same balance.
     */
    public boolean tryDebitCatalogPayment(int credits, int pointsType, int points) {
        if (credits < 0 || points < 0 || pointsType < 0) return false;
        return applyLegacyWallet(
                new LegacyWalletMutation.Change(-1, -credits, false, false),
                new LegacyWalletMutation.Change(pointsType, -points, false, false));
    }

    /** Restores a catalog debit after delivery failed. */
    public void refundCatalogPayment(int credits, int pointsType, int points) {
        if (credits < 0 || points < 0 || pointsType < 0) throw new IllegalArgumentException("Invalid refund");
        applyLegacyWallet(
                new LegacyWalletMutation.Change(-1, credits, false, false),
                new LegacyWalletMutation.Change(pointsType, points, false, false));
    }

    public void setCurrencyAmount(int type, int amount) {
        WalletBalanceMath.requireValidBalance(amount);
        applyLegacyWallet(new LegacyWalletMutation.Change(type, amount, true, false));
    }

    public int getId() {
        return this.id;
    }

    public String getUsername() {
        return this.username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public String getMotto() {
        return this.motto;
    }

    public void setMotto(String motto) {
        this.motto = motto;
    }

    public int getInfostandBg() {
        return InfostandBg;
    }

    public void setInfostandBg(int infostandBg) {
        InfostandBg = infostandBg;
    }

    public int getInfostandStand() {
        return InfostandStand;
    }

    public void setInfostandStand(int infostandStand) {
        InfostandStand = infostandStand;
    }

    public int getInfostandOverlay() {
        return InfostandOverlay;
    }

    public void setInfostandOverlay(int infostandOverlay) {
        InfostandOverlay = infostandOverlay;
    }

    public int getInfostandCardBg() {
        return InfostandCardBg;
    }

    public void setInfostandCardBg(int infostandCardBg) {
        InfostandCardBg = infostandCardBg;
    }

    public int getInfostandBorder() {
        return InfostandBorder;
    }

    public void setInfostandBorder(int infostandBorder) {
        InfostandBorder = infostandBorder;
    }

    public int getInfostandDirection() {
        return infostandDirection;
    }

    public void setInfostandDirection(int infostandDirection) {
        this.infostandDirection = infostandDirection;
    }

    public int getInfostandHeadDirection() {
        return infostandHeadDirection;
    }

    public void setInfostandHeadDirection(int infostandHeadDirection) {
        this.infostandHeadDirection = infostandHeadDirection;
    }

    public String getInfostandAction() {
        return infostandAction;
    }

    public void setInfostandAction(String infostandAction) {
        this.infostandAction = (infostandAction != null && !infostandAction.isEmpty()) ? infostandAction : "std";
    }

    public int getProfileDirection() {
        return profileDirection;
    }

    public void setProfileDirection(int profileDirection) {
        this.profileDirection = profileDirection;
    }

    public int getProfileHeadDirection() {
        return profileHeadDirection;
    }

    public void setProfileHeadDirection(int profileHeadDirection) {
        this.profileHeadDirection = profileHeadDirection;
    }

    public String getProfileAction() {
        return profileAction;
    }

    public void setProfileAction(String profileAction) {
        this.profileAction = (profileAction != null && !profileAction.isEmpty()) ? profileAction : "std";
    }

    public Rank getRank() {
        RankSnapshot snapshot = this.rankSnapshot;
        if (snapshot == null) return this.rank;
        if (snapshot.previous() != null && System.currentTimeMillis() >= snapshot.expiresAt())
            return snapshot.resolver() == null
                    ? snapshot.previous()
                    : snapshot.resolver().apply(snapshot.previous().getId());
        return snapshot.current();
    }

    Rank getStoredRank() {
        RankSnapshot snapshot = this.rankSnapshot;
        return snapshot == null ? this.rank : snapshot.current();
    }

    void setTemporaryRank(Rank assigned, Rank previous, long expiresAt) {
        setTemporaryRank(assigned, previous, expiresAt, null);
    }

    void setTemporaryRank(Rank assigned, Rank previous, long expiresAt, java.util.function.IntFunction<Rank> resolver) {
        this.rankSnapshot = new RankSnapshot(assigned, previous, expiresAt, resolver);
        this.rank = assigned;
    }

    public void setRank(Rank rank) {
        this.rankSnapshot = new RankSnapshot(rank, null, 0, null);
        this.rank = rank;
    }

    public String getLook() {
        return this.look;
    }

    public void setLook(String look) {
        this.look = look;
    }

    public HabboGender getGender() {
        return this.gender;
    }

    public void setGender(HabboGender gender) {
        this.gender = gender;
    }

    public String getMail() {
        return this.mail;
    }

    public void setMail(String mail) {
        this.mail = mail;
    }

    public boolean isMailVerified() {
        return this.mailVerified;
    }

    public String getSso() {
        return this.sso;
    }

    public void setSso(String sso) {
        this.sso = sso;
    }

    public String getIpRegister() {
        return this.ipRegister;
    }

    public void setIpRegister(String ipRegister) {
        this.ipRegister = ipRegister;
    }

    public String getIpLogin() {
        return this.ipLogin;
    }

    public void setIpLogin(String ipLogin) {
        this.ipLogin = ipLogin;
    }

    public int getAccountCreated() {
        return this.accountCreated;
    }

    public void setAccountCreated(int accountCreated) {
        this.accountCreated = accountCreated;
    }

    public boolean canBuy(CatalogItem item) {
        return this.getCredits() >= item.getCredits()
                && this.getCurrencyAmount(item.getPointsType()) >= item.getPoints();
    }

    public int getCredits() {
        synchronized (this.currencyLock) {
            return this.credits;
        }
    }

    public void setCredits(int credits) {
        WalletBalanceMath.requireValidBalance(credits);
        applyLegacyWallet(new LegacyWalletMutation.Change(-1, credits, true, false));
    }

    private static com.zaxxer.hikari.HikariDataSource databaseSource() {
        return Emulator.getDatabase().getDataSource();
    }

    private boolean applyLegacyWallet(LegacyWalletMutation.Change... changes) {
        return LegacyWalletMutation.apply(this, databaseSource(), changes);
    }

    public void addCredits(int credits) {
        applyLegacyWallet(new LegacyWalletMutation.Change(-1, credits, false, true));
    }

    void applyPersistedCredits(int credits) {
        synchronized (this.currencyLock) {
            this.credits = WalletBalanceMath.requireValidBalance(credits);
        }
    }

    void applyPersistedCurrencyAmount(int type, int amount) {
        synchronized (this.currencyLock) {
            this.currencies.put(type, WalletBalanceMath.requireValidBalance(amount));
        }
    }

    Object ledgerMutationLock() {
        return this.ledgerMutationLock;
    }

    public boolean tryAddCredits(int credits) {
        return applyLegacyWallet(new LegacyWalletMutation.Change(-1, credits, false, false));
    }

    public int getPixels() {
        return this.getCurrencyAmount(0);
    }

    public void setPixels(int pixels) {
        this.setCurrencyAmount(0, pixels);
    }

    public void addPixels(int pixels) {
        this.addCurrencyAmount(0, pixels);
    }

    public int getLastOnline() {
        return this.lastOnline;
    }

    public void setLastOnline(int lastOnline) {
        this.lastOnline = lastOnline;
    }

    public int getHomeRoom() {
        return this.homeRoom;
    }

    public void setHomeRoom(int homeRoom) {
        this.homeRoom = homeRoom;
    }

    public boolean isOnline() {
        return this.online;
    }

    public void setOnline(boolean value) {
        this.online = value;
    }

    public int getLoadingRoom() {
        return this.loadingRoom;
    }

    public long getLoadingRoomStartedAt() {
        return this.loadingRoomStartedAt;
    }

    public void setLoadingRoom(int loadingRoom) {
        this.loadingRoomStartedAt = loadingRoom > 0 ? System.currentTimeMillis() : 0;
        this.loadingRoom = loadingRoom;
    }

    public Room getCurrentRoom() {
        return this.currentRoom;
    }

    public void setCurrentRoom(Room room) {
        this.currentRoom = room;
    }

    public String getRoomEntryMethod() {
        return this.roomEntryMethod;
    }

    public void setRoomEntryMethod(String roomEntryMethod) {
        this.roomEntryMethod = roomEntryMethod;
    }

    public int getRoomEntryTeleportId() {
        return this.roomEntryTeleportId;
    }

    public void setRoomEntryTeleportId(int roomEntryTeleportId) {
        this.roomEntryTeleportId = roomEntryTeleportId;
    }

    public int getRoomQueueId() {
        return this.roomQueueId;
    }

    public void setRoomQueueId(int roomQueueId) {
        this.roomQueueId = roomQueueId;
    }

    public RideablePet getRiding() {
        return this.riding;
    }

    public void setRiding(RideablePet riding) {
        this.riding = riding;
    }

    public void dismountPet() {
        this.dismountPet(false);
    }

    public void dismountPet(boolean isRemoving) {
        if (this.getRiding() == null) return;

        Habbo habbo = this.getCurrentRoom().getHabbo(this.getId());
        if (habbo == null) return;

        RideablePet riding = this.getRiding();

        riding.setRider(null);
        riding.setTask(PetTasks.FREE);
        this.setRiding(null);

        Room room = this.getCurrentRoom();
        if (room != null) room.giveEffect(habbo, 0, -1);

        RoomUnit roomUnit = habbo.getRoomUnit();
        if (roomUnit == null) return;

        roomUnit.setZ(riding.getRoomUnit().getZ());
        roomUnit.setPreviousLocationZ(riding.getRoomUnit().getZ());
        roomUnit.stopWalking();
        if (riding.getRoomUnit() != null) {
            riding.getRoomUnit().setCanWalk(true);
            room.sendComposer(new RoomUserStatusComposer(riding.getRoomUnit()).compose());
        }
        room.sendComposer(new RoomUserStatusComposer(roomUnit).compose());
        List<RoomTile> availableTiles = isRemoving
                ? new ArrayList<>()
                : this.getCurrentRoom().getLayout().getWalkableTilesAround(roomUnit.getCurrentLocation());

        RoomTile tile = availableTiles.isEmpty() ? roomUnit.getCurrentLocation() : availableTiles.get(0);
        roomUnit.setGoalLocation(tile);
        roomUnit.statusUpdate(true);
    }

    public Class<? extends Game> getCurrentGame() {
        return this.currentGame;
    }

    public void setCurrentGame(Class<? extends Game> currentGame) {
        this.currentGame = currentGame;
    }

    public boolean isInGame() {
        return this.currentGame != null;
    }

    public synchronized GamePlayer getGamePlayer() {
        return this.gamePlayer;
    }

    public synchronized void setGamePlayer(GamePlayer gamePlayer) {
        this.gamePlayer = gamePlayer;
    }

    public int getPhotoRoomId() {
        return this.photoRoomId;
    }

    public void setPhotoRoomId(int roomId) {
        this.photoRoomId = roomId;
    }

    public int getPhotoTimestamp() {
        return this.photoTimestamp;
    }

    public void setPhotoTimestamp(int photoTimestamp) {
        this.photoTimestamp = photoTimestamp;
    }

    public String getPhotoURL() {
        return this.photoURL;
    }

    public void setPhotoURL(String photoURL) {
        this.photoURL = photoURL;
    }

    public String getPhotoJSON() {
        return this.photoJSON;
    }

    public void setPhotoJSON(String photoJSON) {
        this.photoJSON = photoJSON;
    }

    public int getWebPublishTimestamp() {
        return this.webPublishTimestamp;
    }

    public void setWebPublishTimestamp(int webPublishTimestamp) {
        this.webPublishTimestamp = webPublishTimestamp;
    }

    public String getMachineID() {
        return this.machineID;
    }

    public void setMachineID(String machineID) {
        this.machineID = machineID;
    }

    public List<NavigatorSavedSearch> getSavedSearches() {
        return this.savedSearches;
    }

    public List<MessengerCategory> getMessengerCategories() {
        return this.messengerCategories;
    }

    @Override
    public void run() {
        try {
            SqlQueries.update(
                    "UPDATE users SET motto = ?, online = ?, look = ?, gender = ?, last_login = ?, last_online = ?, home_room = ?, ip_current = ?, machine_id = ?, username = ?, background_id = ?, background_stand_id = ?, background_overlay_id = ?, background_card_id = ?, background_border_id = ?, infostand_direction = ?, infostand_head_direction = ?, infostand_action = ?, profile_direction = ?, profile_head_direction = ?, profile_action = ? WHERE id = ?",
                    this.motto,
                    this.online ? "1" : "0",
                    this.look,
                    this.gender.name(),
                    Emulator.getIntUnixTimestamp(),
                    this.lastOnline,
                    this.homeRoom,
                    this.ipLogin,
                    this.machineID,
                    this.username,
                    this.InfostandBg,
                    this.InfostandStand,
                    this.InfostandOverlay,
                    this.InfostandCardBg,
                    this.InfostandBorder,
                    this.infostandDirection,
                    this.infostandHeadDirection,
                    this.infostandAction,
                    this.profileDirection,
                    this.profileHeadDirection,
                    this.profileAction,
                    this.id);
        } catch (SqlQueries.DataAccessException e) {
            LOGGER.error("Caught SQL exception", e);
        }
    }

    public int getBonusRarePoints() {
        return this.getCurrencyAmount(Emulator.getConfig().getInt("hotelview.promotional.points.type"));
    }

    public HabboStats getHabboStats() {
        Habbo habbo = Emulator.getGameEnvironment().getHabboManager().getHabbo(this.getId());
        if (habbo != null) {
            return habbo.getHabboStats();
        }

        return HabboStats.load(this);
    }
}
