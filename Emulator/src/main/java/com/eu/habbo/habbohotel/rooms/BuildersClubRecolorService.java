package com.eu.habbo.habbohotel.rooms;

import com.eu.habbo.WiredPlatform;
import com.eu.habbo.database.Database;
import com.eu.habbo.habbohotel.GameEnvironment;
import com.eu.habbo.habbohotel.catalog.CatalogManager;
import com.eu.habbo.habbohotel.catalog.CatalogPageType;
import com.eu.habbo.habbohotel.items.FurnitureType;
import com.eu.habbo.habbohotel.items.Item;
import com.eu.habbo.habbohotel.items.interactions.InteractionDefault;
import com.eu.habbo.habbohotel.users.Habbo;
import com.eu.habbo.habbohotel.users.HabboItem;
import com.eu.habbo.messages.outgoing.rooms.items.AddFloorItemComposer;
import com.eu.habbo.messages.outgoing.rooms.items.RemoveFloorItemComposer;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class BuildersClubRecolorService {
    private static final Logger LOGGER = LoggerFactory.getLogger(BuildersClubRecolorService.class);

    public static final int SCOPE_THIS = 0;

    public static final int SCOPE_SAME_IN_ROOM = 1;

    static final int MAX_ITEMS_PER_REQUEST = 500;
    static final long COOLDOWN_MS = 500L;

    static final long COOLDOWN_PER_ITEM_MS = 10L;

    private static final ConcurrentHashMap<Integer, Long> USER_NEXT = new ConcurrentHashMap<>();

    private static final ConcurrentHashMap<Integer, Long> ROOM_NEXT = new ConcurrentHashMap<>();
    private static final int MAX_TRACKED = 10_000;

    public enum Result {
        DONE,
        TOO_FAST,
        NO_RIGHTS,
        NOT_FOUND,
        NOT_RECOLORABLE,
        NOT_BUILDERS_CLUB,
        NO_SUCH_COLOR,
        LIMIT_EXCEEDED
    }

    private BuildersClubRecolorService() {}

    public static Result recolor(Habbo habbo, int itemId, int colorIndex, int scope) {
        if (habbo == null || habbo.getHabboInfo() == null) {
            return Result.NO_RIGHTS;
        }
        Room room = habbo.getHabboInfo().getCurrentRoom();
        if (room == null) {
            return Result.NO_RIGHTS;
        }

        if (scope != SCOPE_THIS && scope != SCOPE_SAME_IN_ROOM) return Result.NOT_RECOLORABLE;
        synchronized (room) {
            return recolorInRoom(habbo, room, itemId, colorIndex, scope);
        }
    }

    private static Result recolorInRoom(Habbo habbo, Room room, int itemId, int colorIndex, int scope) {
        int userId = habbo.getHabboInfo().getId();
        long now = System.currentTimeMillis();
        if (!admit(userId, room.getId(), now)) {
            return Result.TOO_FAST;
        }
        if (!room.isLoaded() || !BuildersClubRoomSupport.canPlaceInRoom(habbo, room)) {
            return Result.NO_RIGHTS;
        }

        HabboItem clicked = room.getHabboItem(itemId);
        if (clicked == null
                || clicked.getBaseItem() == null
                || clicked.getBaseItem().getType() != FurnitureType.FLOOR) {
            return Result.NOT_FOUND;
        }

        if (clicked.isLimited()) return Result.NOT_RECOLORABLE;

        String family = familyOf(clicked.getBaseItem().getName());
        if (family == null) {
            return Result.NOT_RECOLORABLE;
        }
        if (colorIndex < 0 || clicked.getBaseItem().getName().equalsIgnoreCase(family + "*" + colorIndex)) {
            return Result.NO_SUCH_COLOR;
        }

        GameEnvironment environment = WiredPlatform.gameEnvironment();
        Database database = WiredPlatform.database();
        if (environment == null || database == null) {
            return Result.NOT_FOUND;
        }

        Item target = environment.getItemManager().getItem(family + "*" + colorIndex);
        if (!interchangeable(clicked.getBaseItem(), target)
                || !targetAllowedInCatalog(
                        environment.getCatalogManager(),
                        target.getId(),
                        habbo.getHabboInfo().getRank().getId())) {
            return Result.NO_SUCH_COLOR;
        }

        try (Connection connection = database.getDataSource().getConnection()) {
            if (!offeredInBuildersClub(connection, target.getId())) {
                return Result.NO_SUCH_COLOR;
            }

            Set<Integer> buildersClubItems = buildersClubItemsIn(connection, room.getId());
            if (!buildersClubItems.contains(clicked.getId())) {
                return Result.NOT_BUILDERS_CLUB;
            }

            List<HabboItem> items = new ArrayList<>();
            if (scope == SCOPE_SAME_IN_ROOM) {
                int sourceBaseId = clicked.getBaseItem().getId();
                for (HabboItem item : room.getFloorItems()) {
                    if (item != null
                            && item.getBaseItem() != null
                            && item.getBaseItem().getId() == sourceBaseId
                            && !item.isLimited()
                            && buildersClubItems.contains(item.getId())) {
                        if (items.size() >= MAX_ITEMS_PER_REQUEST) return Result.LIMIT_EXCEEDED;
                        items.add(item);
                    }
                }
            } else {
                items.add(clicked);
            }

            List<HabboItem> stored = store(connection, items, target.getId(), room.getId());
            charge(userId, room.getId(), now, stored.size());
            for (HabboItem item : stored) {
                if (room.getHabboItem(item.getId()) != item) {
                    continue;
                }
                item.setBaseItem(target);
                room.sendComposer(new RemoveFloorItemComposer(item, true).compose());
                room.sendComposer(new AddFloorItemComposer(item, room.getFurniOwnerName(item.getUserId())).compose());
            }
            return Result.DONE;
        } catch (SQLException e) {
            LOGGER.error("Could not recolor Builders Club furni {} in room {}", itemId, room.getId(), e);
            return Result.NOT_FOUND;
        }
    }

    static boolean targetAllowedInCatalog(CatalogManager manager, int baseItemId, int rankId) {
        var pages = manager.getCatalogPagesMap(CatalogPageType.BUILDER);
        synchronized (pages) {
            for (var page : pages.values()) {
                if (!page.isEnabled() || !page.isVisible() || page.getRank() > rankId) continue;
                var offers = page.getCatalogItems();
                synchronized (offers) {
                    for (var offer : offers.values()) {
                        if (offer.getAmount() != 1 || offer.getBaseItems().size() != 1) continue;
                        for (Item item : offer.getBaseItems()) if (item.getId() == baseItemId) return true;
                    }
                }
            }
        }
        return false;
    }

    static String familyOf(String itemName) {
        if (itemName == null) {
            return null;
        }
        int star = itemName.lastIndexOf('*');
        if (star <= 0 || star == itemName.length() - 1) {
            return null;
        }
        for (int i = star + 1; i < itemName.length(); i++) {
            if (!Character.isDigit(itemName.charAt(i))) {
                return null;
            }
        }
        return itemName.substring(0, star);
    }

    static boolean interchangeable(Item from, Item to) {
        return from != null
                && to != null
                && from.getId() != to.getId()
                && from.getType() == FurnitureType.FLOOR
                && to.getType() == FurnitureType.FLOOR
                && from.getWidth() == to.getWidth()
                && from.getLength() == to.getLength()
                && Double.compare(from.getHeight(), to.getHeight()) == 0
                && from.allowStack() == to.allowStack()
                && from.allowWalk() == to.allowWalk()
                && from.allowSit() == to.allowSit()
                && from.allowLay() == to.allowLay()
                && from.getStateCount() == to.getStateCount()
                && from.getInteractionType() != null
                && to.getInteractionType() != null
                && from.getInteractionType().getType() == InteractionDefault.class
                && to.getInteractionType().getType() == InteractionDefault.class;
    }

    /** True when neither the user nor the room is still waiting; then the user waits the base cooldown. */
    static synchronized boolean admit(int userId, int roomId, long now) {
        if (USER_NEXT.size() >= MAX_TRACKED) {
            USER_NEXT.entrySet().removeIf(entry -> entry.getValue() <= now);
        }
        if (ROOM_NEXT.size() >= MAX_TRACKED) {
            ROOM_NEXT.entrySet().removeIf(entry -> entry.getValue() <= now);
        }
        if ((!USER_NEXT.containsKey(userId) && USER_NEXT.size() >= MAX_TRACKED)
                || (!ROOM_NEXT.containsKey(roomId) && ROOM_NEXT.size() >= MAX_TRACKED)) return false;
        if (ROOM_NEXT.getOrDefault(roomId, 0L) > now) {
            return false;
        }
        boolean[] admitted = {false};
        USER_NEXT.compute(userId, (key, next) -> {
            if (next != null && next > now) {
                return next;
            }
            admitted[0] = true;
            return now + COOLDOWN_MS;
        });
        return admitted[0];
    }

    static synchronized void charge(int userId, int roomId, long now, int count) {
        long cost = (long) count * COOLDOWN_PER_ITEM_MS;
        USER_NEXT.merge(userId, now + Math.max(COOLDOWN_MS, cost), Math::max);
        if (count > 1) {
            ROOM_NEXT.merge(roomId, now + cost, Math::max);
        }
    }

    static boolean offeredInBuildersClub(Connection connection, int itemId) throws SQLException {
        try (PreparedStatement statement =
                connection.prepareStatement("SELECT 1 FROM catalog_items_bc WHERE item_ids = ? LIMIT 1")) {
            statement.setString(1, String.valueOf(itemId));
            try (ResultSet set = statement.executeQuery()) {
                return set.next();
            }
        }
    }

    static Set<Integer> buildersClubItemsIn(Connection connection, int roomId) throws SQLException {
        Set<Integer> ids = new HashSet<>();
        try (PreparedStatement statement =
                connection.prepareStatement("SELECT item_id FROM builders_club_items WHERE room_id = ?")) {
            statement.setInt(1, roomId);
            try (ResultSet set = statement.executeQuery()) {
                while (set.next()) {
                    ids.add(set.getInt(1));
                }
            }
        }
        return ids;
    }

    static List<HabboItem> store(Connection connection, List<HabboItem> items, int baseItemId, int roomId)
            throws SQLException {
        List<HabboItem> stored = new ArrayList<>();
        if (items.isEmpty()) {
            return stored;
        }
        boolean autoCommit = connection.getAutoCommit();
        if (!autoCommit) throw new SQLException("Recolor requires its own transaction");
        connection.setAutoCommit(false);
        try (PreparedStatement statement = connection.prepareStatement(
                "UPDATE items SET item_id = ? WHERE id = ? AND room_id = ? AND item_id = ?"
                        + " AND EXISTS (SELECT 1 FROM builders_club_items bc WHERE bc.item_id = items.id AND bc.room_id = items.room_id)")) {
            for (HabboItem item : items) {
                statement.setInt(1, baseItemId);
                statement.setInt(2, item.getId());
                statement.setInt(3, roomId);
                statement.setInt(4, item.getBaseItem().getId());
                statement.addBatch();
            }
            int[] counts = statement.executeBatch();
            if (counts.length != items.size()) throw new SQLException("Incomplete recolor batch");
            for (int count : counts) {
                if (count != 1) throw new SQLException("Recolor target changed");
            }
            connection.commit();
            stored.addAll(items);
        } catch (SQLException | RuntimeException failure) {
            connection.rollback();
            throw failure;
        } finally {
            connection.setAutoCommit(autoCommit);
        }
        return stored;
    }
}
