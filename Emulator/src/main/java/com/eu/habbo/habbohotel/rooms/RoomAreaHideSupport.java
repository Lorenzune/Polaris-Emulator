package com.eu.habbo.habbohotel.rooms;

import com.eu.habbo.habbohotel.gameclients.GameClient;
import com.eu.habbo.habbohotel.items.FurnitureType;
import com.eu.habbo.habbohotel.items.interactions.InteractionAreaHideControl;
import com.eu.habbo.habbohotel.users.HabboItem;
import com.eu.habbo.messages.outgoing.rooms.items.AddFloorItemComposer;
import com.eu.habbo.messages.outgoing.rooms.items.AddWallItemComposer;
import com.eu.habbo.messages.outgoing.rooms.items.AreaHideComposer;
import com.eu.habbo.messages.outgoing.rooms.items.RemoveFloorItemComposer;
import com.eu.habbo.messages.outgoing.rooms.items.RemoveWallItemComposer;
import java.awt.Rectangle;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.WeakHashMap;

public final class RoomAreaHideSupport {
    private static final Map<Room, HiddenItems> HIDDEN_ITEMS_BY_ROOM = new WeakHashMap<>();

    private RoomAreaHideSupport() {}

    public static boolean isControllerItem(HabboItem item) {
        return item instanceof InteractionAreaHideControl
                || hasInteractionName(item, "wf_conf_area_hide")
                || hasInteractionName(item, "conf_area_hide");
    }

    static boolean isRoomVisibilityController(HabboItem item) {
        return isControllerItem(item) || RoomConfInvisSupport.isControllerItem(item);
    }

    public static boolean isControllerActive(HabboItem item) {
        return isControllerItem(item) && getState(item) == 1;
    }

    public static boolean isTileHidden(Room room, int x, int y) {
        if (room == null) {
            return false;
        }

        for (HabboItem controller : room.getFloorItems()) {
            if (!isControllerActive(controller) || getWidth(controller) <= 0 || getLength(controller) <= 0) {
                continue;
            }

            boolean insideArea = x >= getRootX(controller)
                    && x < getRootX(controller) + getWidth(controller)
                    && y >= getRootY(controller)
                    && y < getRootY(controller) + getLength(controller);

            if (isInverted(controller) ? !insideArea : insideArea) {
                return true;
            }
        }

        return false;
    }

    public static int getState(HabboItem item) {
        return Math.min(1, readIntValue(item, "state", 0));
    }

    public static int getRootX(HabboItem item) {
        return readIntValue(item, "rootX", 0);
    }

    public static int getRootY(HabboItem item) {
        return readIntValue(item, "rootY", 0);
    }

    public static int getWidth(HabboItem item) {
        return readIntValue(item, "width", 0);
    }

    public static int getLength(HabboItem item) {
        return readIntValue(item, "length", 0);
    }

    public static boolean isInvisibilityEnabled(HabboItem item) {
        return readIntValue(item, "invisibility", 0) == 1;
    }

    public static boolean includesWallItems(HabboItem item) {
        return readIntValue(item, "wallItems", 0) == 1;
    }

    public static boolean isInverted(HabboItem item) {
        return readIntValue(item, "invert", 0) == 1;
    }

    public static void sendState(Room room, HabboItem item) {
        if (room == null || item == null || !isControllerItem(item)) {
            return;
        }

        room.sendComposer(new AreaHideComposer(item).compose());
        synchronizeClientVisibility(room);
    }

    public static void sendState(Room room, GameClient client) {
        if (room == null || client == null) {
            return;
        }

        for (HabboItem item : room.getFloorItems()) {
            if (!isControllerActive(item)) {
                continue;
            }

            client.sendResponse(new AreaHideComposer(item).compose());
        }

        HiddenItems hiddenItems = calculateHiddenItems(room);

        synchronized (HIDDEN_ITEMS_BY_ROOM) {
            HIDDEN_ITEMS_BY_ROOM.put(room, hiddenItems);
        }

        for (int itemId : hiddenItems.floorItemIds) {
            HabboItem item = room.getHabboItem(itemId);

            if (item != null) {
                client.sendResponse(new RemoveFloorItemComposer(item).compose());
            }
        }

        for (int itemId : hiddenItems.wallItemIds) {
            HabboItem item = room.getHabboItem(itemId);

            if (item != null) {
                client.sendResponse(new RemoveWallItemComposer(item).compose());
            }
        }
    }

    public static void synchronizeClientVisibility(Room room) {
        if (room == null || !room.isLoaded()) {
            return;
        }

        HiddenItems next = calculateHiddenItems(room);
        HiddenItems previous;

        synchronized (HIDDEN_ITEMS_BY_ROOM) {
            previous = HIDDEN_ITEMS_BY_ROOM.put(room, next);
        }

        if (previous == null) {
            previous = HiddenItems.empty();
        }

        for (int itemId : difference(previous.floorItemIds, next.floorItemIds)) {
            HabboItem item = room.getHabboItem(itemId);

            if (isFloorItem(item)) {
                room.sendComposer(new AddFloorItemComposer(item, getOwnerName(room, item)).compose());
            }
        }

        for (int itemId : difference(previous.wallItemIds, next.wallItemIds)) {
            HabboItem item = room.getHabboItem(itemId);

            if (isWallItem(item)) {
                room.sendComposer(new AddWallItemComposer(item, getOwnerName(room, item)).compose());
            }
        }

        for (int itemId : difference(next.floorItemIds, previous.floorItemIds)) {
            HabboItem item = room.getHabboItem(itemId);

            if (isFloorItem(item)) {
                room.sendComposer(new RemoveFloorItemComposer(item).compose());
            }
        }

        for (int itemId : difference(next.wallItemIds, previous.wallItemIds)) {
            HabboItem item = room.getHabboItem(itemId);

            if (isWallItem(item)) {
                room.sendComposer(new RemoveWallItemComposer(item).compose());
            }
        }
    }

    static boolean shouldHideFloorItem(HabboItem item, HabboItem controller) {
        if (!isFloorItem(item)
                || controller == null
                || item == controller
                || isRoomVisibilityController(item)
                || !isControllerActive(controller)
                || getWidth(controller) <= 0
                || getLength(controller) <= 0) {
            return false;
        }

        Rectangle itemBounds = RoomLayout.getRectangle(
                item.getX(),
                item.getY(),
                item.getBaseItem().getWidth(),
                item.getBaseItem().getLength(),
                item.getRotation());
        Rectangle hiddenArea =
                new Rectangle(getRootX(controller), getRootY(controller), getWidth(controller), getLength(controller));
        boolean intersects = itemBounds.intersects(hiddenArea);

        return isInverted(controller) ? !intersects : intersects;
    }

    static boolean shouldHideWallItem(HabboItem item, HabboItem controller) {
        return isWallItem(item)
                && controller != null
                && isControllerActive(controller)
                && includesWallItems(controller);
    }

    static boolean shouldHideController(HabboItem item, boolean confInvisControlActive) {
        return isControllerItem(item) && isInvisibilityEnabled(item) && confInvisControlActive;
    }

    private static HiddenItems calculateHiddenItems(Room room) {
        if (room == null) {
            return HiddenItems.empty();
        }

        Set<HabboItem> controllers = new HashSet<>();

        for (HabboItem item : room.getFloorItems()) {
            if (isControllerActive(item)) {
                controllers.add(item);
            }
        }

        boolean confInvisControlActive = RoomConfInvisSupport.hasActiveController(room);

        if (controllers.isEmpty() && !confInvisControlActive) {
            return HiddenItems.empty();
        }

        Set<Integer> hiddenFloorItemIds = new HashSet<>();
        Set<Integer> hiddenWallItemIds = new HashSet<>();

        for (HabboItem item : room.getFloorItems()) {
            if (shouldHideController(item, confInvisControlActive)) {
                // Keep hidden controllers in the room object collection so :furni can still list them.
                continue;
            }

            for (HabboItem controller : controllers) {
                if (shouldHideFloorItem(item, controller)) {
                    if (!isRoomVisibilityController(item)) {
                        hiddenFloorItemIds.add(item.getId());
                    }
                    break;
                }
            }
        }

        for (HabboItem item : room.getWallItems()) {
            for (HabboItem controller : controllers) {
                if (shouldHideWallItem(item, controller)) {
                    hiddenWallItemIds.add(item.getId());
                    break;
                }
            }
        }

        return new HiddenItems(hiddenFloorItemIds, hiddenWallItemIds);
    }

    private static Set<Integer> difference(Set<Integer> values, Set<Integer> excluded) {
        Set<Integer> result = new HashSet<>(values);
        result.removeAll(excluded);
        return result;
    }

    private static boolean isFloorItem(HabboItem item) {
        return item != null && item.getBaseItem() != null && item.getBaseItem().getType() == FurnitureType.FLOOR;
    }

    private static boolean isWallItem(HabboItem item) {
        return item != null && item.getBaseItem() != null && item.getBaseItem().getType() == FurnitureType.WALL;
    }

    private static String getOwnerName(Room room, HabboItem item) {
        if (room == null || item == null) {
            return "";
        }

        String ownerName = room.getFurniOwnerNames().get(item.getUserId());
        return ownerName == null ? "" : ownerName;
    }

    private static int readIntValue(HabboItem item, String key, int fallback) {
        if (!(item instanceof InteractionAreaHideControl) || key == null) {
            return fallback;
        }

        InteractionAreaHideControl areaHide = (InteractionAreaHideControl) item;
        String value = areaHide.values.get(key);

        try {
            return Math.max(0, Integer.parseInt(value));
        } catch (Exception ignored) {
            return fallback;
        }
    }

    private static boolean hasInteractionName(HabboItem item, String interactionName) {
        return item != null
                && item.getBaseItem() != null
                && item.getBaseItem().getType() == FurnitureType.FLOOR
                && item.getBaseItem().getInteractionType() != null
                && item.getBaseItem().getInteractionType().getName() != null
                && item.getBaseItem().getInteractionType().getName().equalsIgnoreCase(interactionName);
    }

    private record HiddenItems(Set<Integer> floorItemIds, Set<Integer> wallItemIds) {
        private static HiddenItems empty() {
            return new HiddenItems(Set.of(), Set.of());
        }
    }
}
