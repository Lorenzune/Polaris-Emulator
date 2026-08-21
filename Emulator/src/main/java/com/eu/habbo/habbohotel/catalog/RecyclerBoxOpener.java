package com.eu.habbo.habbohotel.catalog;

import com.eu.habbo.Emulator;
import com.eu.habbo.habbohotel.achievements.AchievementInteractions;
import com.eu.habbo.habbohotel.gameclients.GameClient;
import com.eu.habbo.habbohotel.permissions.Permission;
import com.eu.habbo.habbohotel.rooms.Room;
import com.eu.habbo.habbohotel.rooms.RoomChatMessage;
import com.eu.habbo.habbohotel.rooms.RoomChatMessageBubbles;
import com.eu.habbo.habbohotel.rooms.RoomLayout;
import com.eu.habbo.habbohotel.rooms.RoomTile;
import com.eu.habbo.habbohotel.users.Habbo;
import com.eu.habbo.habbohotel.users.HabboItem;
import com.eu.habbo.messages.outgoing.inventory.AddHabboItemComposer;
import com.eu.habbo.messages.outgoing.inventory.InventoryRefreshComposer;
import com.eu.habbo.messages.outgoing.rooms.UpdateStackHeightComposer;
import com.eu.habbo.messages.outgoing.rooms.items.PresentItemOpenedComposer;
import com.eu.habbo.messages.outgoing.rooms.items.RemoveFloorItemComposer;
import com.eu.habbo.messages.outgoing.rooms.users.RoomUserWhisperComposer;

import java.awt.Rectangle;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/** Opens the classic Furni-Matic reward box from either recycler packet path or a normal furni double click. */
public final class RecyclerBoxOpener {
    private static final Set<String> RECYCLER_BOX_CLASSNAMES = Set.of("ecotron_box", "matic_box");
    private static final Set<Integer> OPENING_BOX_IDS = ConcurrentHashMap.newKeySet();

    private RecyclerBoxOpener() {}

    /**
     * @return true when the item is a recycler box, including unauthorized or
     * already-opened attempts that must not fall through to a normal toggle.
     */
    public static boolean tryOpen(GameClient client, Room room, HabboItem item) {
        if (item == null || !RECYCLER_BOX_CLASSNAMES.contains(item.getBaseItem().getName())) {
            return false;
        }

        if (client == null || room == null) {
            return true;
        }

        Habbo habbo = client.getHabbo();
        if (habbo == null
                || item.getUserId() != habbo.getHabboInfo().getId()
                || !(room.getOwnerId() == habbo.getHabboInfo().getId()
                        || habbo.hasPermission(Permission.ACC_ANYROOMOWNER)
                        || room.hasRights(habbo))) {
            return true;
        }

        if (!OPENING_BOX_IDS.add(item.getId())) {
            return true;
        }

        try {
            if (item.getExtradata() == null || item.getExtradata().isBlank()) {
                client.sendResponse(new RoomUserWhisperComposer(new RoomChatMessage(
                        Emulator.getTexts().getValue("error.recycler.box.empty"),
                        habbo,
                        habbo,
                        RoomChatMessageBubbles.BOT)));
            } else {
                HabboItem reward = Emulator.getGameEnvironment().getItemManager().handleOpenRecycleBox(habbo, item);

                if (reward != null) {
                    habbo.getInventory().getItemsComponent().addItem(reward);
                    client.sendResponse(new AddHabboItemComposer(reward));
                    client.sendResponse(new InventoryRefreshComposer());
                    client.sendResponse(new PresentItemOpenedComposer(reward, item.getExtradata(), true));
                    AchievementInteractions.progress(habbo, "RecycleBoxOpener");
                }
            }

            room.sendComposer(new RemoveFloorItemComposer(item).compose());
            room.removeHabboItem(item);
            item.setRoomId(0);
            refreshRemovedFloorArea(room, item);
        } finally {
            OPENING_BOX_IDS.remove(item.getId());
        }

        return true;
    }

    /**
     * `removeHabboItem` removes the object index but does not recalculate the
     * affected tiles. Mirror RoomItemOwnershipService's normal pickup path so
     * a consumed recycler box immediately frees every square it occupied.
     */
    private static void refreshRemovedFloorArea(Room room, HabboItem item) {
        RoomLayout layout = room.getLayout();
        if (layout == null) return;

        Rectangle rectangle = RoomLayout.getRectangle(
                item.getX(),
                item.getY(),
                item.getBaseItem().getWidth(),
                item.getBaseItem().getLength(),
                item.getRotation());
        Set<RoomTile> updatedTiles = new HashSet<>();

        for (short x = (short) rectangle.x; x < rectangle.x + rectangle.getWidth(); x++) {
            for (short y = (short) rectangle.y; y < rectangle.y + rectangle.getHeight(); y++) {
                RoomTile tile = layout.getTile(x, y);
                if (tile == null) continue;

                tile.setStackHeight(room.getStackHeight(x, y, false));
                updatedTiles.add(tile);
            }
        }

        if (updatedTiles.isEmpty()) return;

        room.sendComposer(new UpdateStackHeightComposer(room, updatedTiles).compose());
        room.updateTiles(updatedTiles);

        for (RoomTile tile : updatedTiles) {
            room.updateHabbosAt(tile.x, tile.y);
            room.updateBotsAt(tile.x, tile.y);
        }
    }
}
