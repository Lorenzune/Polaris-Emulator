package com.eu.habbo.messages.incoming.catalog;

import com.eu.habbo.Emulator;
import com.eu.habbo.habbohotel.catalog.CatalogItem;
import com.eu.habbo.habbohotel.catalog.CatalogPage;
import com.eu.habbo.habbohotel.catalog.CatalogPageType;
import com.eu.habbo.habbohotel.items.FurnitureType;
import com.eu.habbo.habbohotel.items.Item;
import com.eu.habbo.habbohotel.rooms.BuildersClubRoomSupport;
import com.eu.habbo.habbohotel.rooms.FurnitureMovementError;
import com.eu.habbo.habbohotel.rooms.Room;
import com.eu.habbo.habbohotel.rooms.RoomTile;
import com.eu.habbo.habbohotel.users.HabboItem;
import com.eu.habbo.messages.incoming.MessageHandler;
import com.eu.habbo.messages.outgoing.generic.alerts.BubbleAlertComposer;
import com.eu.habbo.messages.outgoing.generic.alerts.BubbleAlertKeys;
import java.util.Iterator;

public class BuildersClubPlaceRoomItemEvent extends MessageHandler {
    @Override
    public void handle() throws Exception {
        int pageId = this.packet.readInt();
        int offerId = this.packet.readInt();
        String extraData = this.packet.readString();
        short x = this.packet.readInt().shortValue();
        short y = this.packet.readInt().shortValue();
        int rotation = this.packet.readInt();

        Room room = this.client.getHabbo().getHabboInfo().getCurrentRoom();
        int placementUserId = BuildersClubRoomSupport.getPlacementPoolUserId(this.client.getHabbo());

        if (room == null || !this.client.getHabbo().getRoomUnit().isInRoom()) {
            this.client.sendResponse(new BubbleAlertComposer(
                    BubbleAlertKeys.FURNITURE_PLACEMENT_ERROR.key, FurnitureMovementError.NO_RIGHTS.errorCode));
            return;
        }

        if (!BuildersClubRoomSupport.canPlaceInCurrentRoom(this.client.getHabbo())) {
            this.client.sendResponse(new BubbleAlertComposer(
                    BubbleAlertKeys.FURNITURE_PLACEMENT_ERROR.key, "builder.placement_widget.error.not_group_admin"));
            BuildersClubRoomSupport.sendPlacementStatus(this.client.getHabbo());
            return;
        }

        if (placementUserId <= 0) {
            this.client.sendResponse(new BubbleAlertComposer(
                    BubbleAlertKeys.FURNITURE_PLACEMENT_ERROR.key, FurnitureMovementError.NO_RIGHTS.errorCode));
            return;
        }

        if (!BuildersClubRoomSupport.hasActiveMembership(
                this.client.getHabbo().getHabboInfo().getId())) {
            int trackedFurniCount = BuildersClubRoomSupport.getTrackedFurniCount(placementUserId);

            if (trackedFurniCount >= BuildersClubRoomSupport.getFurniLimit(placementUserId)) {
                this.client.sendResponse(new BubbleAlertComposer(
                        BubbleAlertKeys.FURNITURE_PLACEMENT_ERROR.key, "room.error.max_furniture"));
                BuildersClubRoomSupport.sendPlacementStatus(this.client.getHabbo());
                return;
            }

            if (BuildersClubRoomSupport.hasPlacementVisitors(room, this.client.getHabbo())) {
                this.client.sendResponse(new BubbleAlertComposer(
                        BubbleAlertKeys.FURNITURE_PLACEMENT_ERROR.key, "builder.placement_widget.error.visitors"));
                return;
            }
        }

        CatalogItem catalogItem = resolveCatalogItem(pageId, offerId);
        Item baseItem = resolveBaseItem(catalogItem, FurnitureType.FLOOR);

        if (catalogItem == null || baseItem == null) {
            this.client.sendResponse(new BubbleAlertComposer(
                    BubbleAlertKeys.FURNITURE_PLACEMENT_ERROR.key, FurnitureMovementError.INVALID_MOVE.errorCode));
            return;
        }

        Integer blockColor = null;
        if (extraData != null && extraData.startsWith("blockcolor:")) {
            if (!com.eu.habbo.habbohotel.catalog.BuildersBlocksCatalogAccessPage.isRecolorableBlock(baseItem)
                    || !extraData.matches("blockcolor:#[0-9a-fA-F]{6}")) return;
            blockColor = Integer.parseInt(extraData.substring(12), 16);
            extraData = "";
        }

        RoomTile tile = room.getLayout().getTile(x, y);

        if (tile == null) {
            this.client.sendResponse(new BubbleAlertComposer(
                    BubbleAlertKeys.FURNITURE_PLACEMENT_ERROR.key, FurnitureMovementError.INVALID_MOVE.errorCode));
            return;
        }

        HabboItem item = Emulator.getGameEnvironment()
                .getItemManager()
                .createItem(
                        placementUserId,
                        baseItem,
                        0,
                        0,
                        (extraData != null && !extraData.isEmpty()) ? extraData : catalogItem.getExtradata());

        if (item == null) {
            this.client.sendResponse(new BubbleAlertComposer(
                    BubbleAlertKeys.FURNITURE_PLACEMENT_ERROR.key, FurnitureMovementError.INVALID_MOVE.errorCode));
            return;
        }

        item.setVirtualUserId(BuildersClubRoomSupport.VIRTUAL_OWNER_ID);

        FurnitureMovementError error = room.canPlaceFurnitureAt(item, this.client.getHabbo(), tile, rotation);

        if (!error.equals(FurnitureMovementError.NONE)) {
            Emulator.getGameEnvironment().getItemManager().deleteItem(item);
            this.client.sendResponse(
                    new BubbleAlertComposer(BubbleAlertKeys.FURNITURE_PLACEMENT_ERROR.key, error.errorCode));
            return;
        }

        error = room.placeFloorFurniAt(item, tile, rotation, this.client.getHabbo());

        if (!error.equals(FurnitureMovementError.NONE)) {
            Emulator.getGameEnvironment().getItemManager().deleteItem(item);
            this.client.sendResponse(
                    new BubbleAlertComposer(BubbleAlertKeys.FURNITURE_PLACEMENT_ERROR.key, error.errorCode));
            return;
        }

        BuildersClubRoomSupport.trackPlacedItem(item.getId(), placementUserId, room.getId());
        item.setBuildersClubItem(BuildersClubRoomSupport.isTrackedItem(item.getId()));

        if (blockColor != null) {
            synchronized (room) {
                try {
                    if (!room.getWiredRuntime()
                            .setBuildersBlockColor(
                                    java.util.List.of(item),
                                    com.eu.habbo.habbohotel.rooms.BuildersClubRecolorService.colorConfiguration(
                                            blockColor))) {
                        this.client.getHabbo().whisper("Blocco posato: limite colori della stanza raggiunto.");
                    }
                } catch (java.sql.SQLException failure) {
                    org.slf4j.LoggerFactory.getLogger(BuildersClubPlaceRoomItemEvent.class)
                            .error(
                                    "Could not save initial block color for item {} in room {}",
                                    item.getId(),
                                    room.getId(),
                                    failure);
                    this.client
                            .getHabbo()
                            .whisper("Blocco posato, ma il colore non è stato salvato. Riprova con Cambia colore.");
                }
            }
        }

        if (BuildersClubRoomSupport.syncRoom(room) == BuildersClubRoomSupport.SyncResult.LOCKED) {
            BuildersClubRoomSupport.sendRoomLockedBubble(room.getOwnerId());
        }

        BuildersClubRoomSupport.sendPlacementStatusForPool(room, placementUserId);
    }

    private CatalogItem resolveCatalogItem(int pageId, int offerId) {
        var manager = Emulator.getGameEnvironment().getCatalogManager();
        CatalogPage normalPage = manager.getCatalogPage(pageId, CatalogPageType.NORMAL);
        if (normalPage instanceof com.eu.habbo.habbohotel.catalog.BuildersBlocksCatalogAccessPage access) {
            CatalogPage source = manager.getCatalogPage(access.getSourcePageId(), access.getSourcePageType());
            if (!access.isEnabled()
                    || !access.isVisible()
                    || source == null
                    || !source.isEnabled()
                    || !source.isVisible()
                    || source.getRank()
                            > this.client.getHabbo().getHabboInfo().getRank().getId()) return null;
            for (CatalogItem item : access.getCatalogItems().values()) {
                if (item.getOfferId() == offerId || item.getId() == offerId) return item;
            }
            return null;
        }
        if (normalPage != null
                && normalPage.getId()
                        == com.eu.habbo.habbohotel.catalog.BuildersBlocksCatalogAccessPage.RECOLORABLE_SOURCE_PAGE_ID) {
            if (!normalPage.isEnabled()
                    || !normalPage.isVisible()
                    || normalPage.getRank()
                            > this.client.getHabbo().getHabboInfo().getRank().getId()) return null;
            for (CatalogItem item : normalPage.getCatalogItems().values()) {
                if ((item.getOfferId() == offerId || item.getId() == offerId)
                        && com.eu.habbo.habbohotel.catalog.BuildersBlocksCatalogAccessPage.isRecolorableOffer(item))
                    return item;
            }
            return null;
        }
        CatalogItem buildersClubItem = manager.getCatalogItem(offerId, CatalogPageType.BUILDER);

        if (buildersClubItem != null) {
            return buildersClubItem;
        }

        int catalogItemId = manager.offerDefs.get(offerId);

        if (catalogItemId > 0) {
            return manager.getCatalogItem(catalogItemId);
        }

        CatalogPage page = manager.getCatalogPage(pageId, CatalogPageType.BUILDER);

        if (page == null) {
            return null;
        }

        for (CatalogItem catalogItem : page.getCatalogItems().values()) {
            if (catalogItem.getOfferId() == offerId) {
                return catalogItem;
            }
        }

        return null;
    }

    private Item resolveBaseItem(CatalogItem catalogItem, FurnitureType expectedType) {
        if (catalogItem == null
                || catalogItem.getAmount() != 1
                || catalogItem.getBaseItems().size() != 1) {
            return null;
        }

        Iterator<Item> iterator = catalogItem.getBaseItems().iterator();

        if (!iterator.hasNext()) {
            return null;
        }

        Item baseItem = iterator.next();

        if (baseItem == null || baseItem.getType() != expectedType) {
            return null;
        }

        return baseItem;
    }
}
