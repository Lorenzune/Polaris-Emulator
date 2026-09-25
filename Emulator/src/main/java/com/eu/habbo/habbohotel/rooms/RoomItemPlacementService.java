package com.eu.habbo.habbohotel.rooms;

import com.eu.habbo.Emulator;
import com.eu.habbo.habbohotel.items.interactions.InteractionBuildArea;
import com.eu.habbo.habbohotel.items.interactions.InteractionStackHelper;
import com.eu.habbo.habbohotel.items.interactions.InteractionStackWalkHelper;
import com.eu.habbo.habbohotel.items.interactions.InteractionTileWalkMagic;
import com.eu.habbo.habbohotel.items.interactions.InteractionYoutubeTV;
import com.eu.habbo.habbohotel.items.interactions.wired.effects.WiredEffectSendSignal;
import com.eu.habbo.habbohotel.items.interactions.wired.triggers.WiredTriggerReceiveSignal;
import com.eu.habbo.habbohotel.permissions.Permission;
import com.eu.habbo.habbohotel.users.Habbo;
import com.eu.habbo.habbohotel.users.HabboItem;
import com.eu.habbo.messages.outgoing.rooms.items.AddFloorItemComposer;
import com.eu.habbo.messages.outgoing.rooms.items.AddWallItemComposer;
import com.eu.habbo.plugin.Event;
import com.eu.habbo.plugin.events.furniture.FurnitureBuildheightEvent;
import com.eu.habbo.plugin.events.furniture.FurniturePlacedEvent;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import org.apache.commons.math3.util.Pair;

final class RoomItemPlacementService {

    private final Room room;
    private final RoomItemIndex index;
    private final RoomItemManager facade;

    RoomItemPlacementService(Room room, RoomItemIndex index, RoomItemManager facade) {
        this.room = room;
        this.index = index;
        this.facade = facade;
    }

    boolean hasObjectTypeAt(Class<?> type, int x, int y) {
        for (HabboItem item : this.facade.getItemsAt(x, y)) {
            if (item.getClass() == type) {
                return true;
            }
        }
        return false;
    }

    FurnitureMovementError canPlaceFurnitureAt(HabboItem item, Habbo habbo, RoomTile tile, int rotation) {
        if (this.isYoutubeInteractionLimitReached(item)) {
            return FurnitureMovementError.YOUTUBE_ALREADY_IN_ROOM;
        }
        if (this.facade.itemCount() >= Room.MAXIMUM_FURNI) {
            return FurnitureMovementError.MAX_ITEMS;
        }
        if (tile == null || tile.state == RoomTileState.INVALID) {
            return FurnitureMovementError.INVALID_MOVE;
        }

        rotation %= 8;
        if (this.room.hasRights(habbo)
                || this.room.getGuildRightLevel(habbo).isEqualOrGreaterThan(RoomRightLevels.GUILD_RIGHTS)
                || habbo.hasPermission(Permission.ACC_MOVEROTATE)
                || BuildersClubRoomSupport.canPlaceInRoom(habbo, this.room)) {
            return FurnitureMovementError.NONE;
        }

        if (habbo.getHabboStats().isRentingSpace()) {
            HabboItem rentSpace = this.facade.getHabboItem(habbo.getHabboStats().rentedItemId);
            if (rentSpace != null) {
                boolean insideRental = RoomLayout.squareInSquare(
                        RoomLayout.getRectangle(
                                rentSpace.getX(),
                                rentSpace.getY(),
                                rentSpace.getBaseItem().getWidth(),
                                rentSpace.getBaseItem().getLength(),
                                rentSpace.getRotation()),
                        RoomLayout.getRectangle(
                                tile.x,
                                tile.y,
                                item.getBaseItem().getWidth(),
                                item.getBaseItem().getLength(),
                                rotation));
                return insideRental ? FurnitureMovementError.NONE : FurnitureMovementError.NO_RIGHTS;
            }
        }

        for (HabboItem area : this.room.getRoomSpecialTypes().getItemsOfType(InteractionBuildArea.class)) {
            InteractionBuildArea buildArea = (InteractionBuildArea) area;
            if (buildArea.inSquare(tile)
                    && buildArea.isBuilder(habbo.getHabboInfo().getUsername())) {
                return FurnitureMovementError.NONE;
            }
        }
        return FurnitureMovementError.NO_RIGHTS;
    }

    FurnitureMovementError furnitureFitsAt(RoomTile tile, HabboItem item, int rotation, boolean checkForUnits) {
        RoomLayout layout = this.room.getLayout();
        if (!layout.fitsOnMap(
                tile, item.getBaseItem().getWidth(), item.getBaseItem().getLength(), rotation)) {
            return FurnitureMovementError.INVALID_MOVE;
        }

        if (this.isStackPlacementBypassItem(item)) {
            return FurnitureMovementError.NONE;
        }

        Set<RoomTile> occupiedTiles = layout.getTilesAt(
                tile, item.getBaseItem().getWidth(), item.getBaseItem().getLength(), rotation);
        for (RoomTile occupiedTile : occupiedTiles) {
            if (occupiedTile.state == RoomTileState.INVALID) {
                return FurnitureMovementError.INVALID_MOVE;
            }
            if (checkForUnits) {
                if (this.room.hasHabbosAt(occupiedTile.x, occupiedTile.y)) {
                    return FurnitureMovementError.TILE_HAS_HABBOS;
                }
                if (this.room.hasBotsAt(occupiedTile.x, occupiedTile.y)) {
                    return FurnitureMovementError.TILE_HAS_BOTS;
                }
                if (this.room.hasPetsAt(occupiedTile.x, occupiedTile.y)) {
                    return FurnitureMovementError.TILE_HAS_PETS;
                }
            }
        }

        List<Pair<RoomTile, Set<HabboItem>>> tileFurniture = new ArrayList<>();
        for (RoomTile occupiedTile : occupiedTiles) {
            tileFurniture.add(Pair.create(occupiedTile, this.facade.getItemsAt(occupiedTile)));
            HabboItem topItem = this.facade.getTopItemAt(occupiedTile.x, occupiedTile.y, item);
            if (topItem != null && !topItem.getBaseItem().allowStack() && !occupiedTile.getAllowStack()) {
                return FurnitureMovementError.CANT_STACK;
            }
        }

        return item.canStackAt(this.room, tileFurniture)
                ? FurnitureMovementError.NONE
                : FurnitureMovementError.CANT_STACK;
    }

    FurnitureMovementError placeFloorFurniture(HabboItem item, RoomTile tile, int rotation, Habbo owner) {
        if (this.isYoutubeInteractionLimitReached(item)) {
            return FurnitureMovementError.YOUTUBE_ALREADY_IN_ROOM;
        }

        RoomSpecialTypes specialTypes = this.room.getRoomSpecialTypes();
        if (specialTypes != null) {
            if (item instanceof WiredEffectSendSignal && specialTypes.isSignalSenderLimitReached()) {
                return FurnitureMovementError.MAX_SIGNAL_SENDERS;
            }
            if (item instanceof WiredTriggerReceiveSignal && specialTypes.isSignalReceiverLimitReached()) {
                return FurnitureMovementError.MAX_SIGNAL_RECEIVERS;
            }
        }

        boolean pluginHelper = false;
        if (Emulator.getPluginManager().isRegistered(FurniturePlacedEvent.class, true)) {
            FurniturePlacedEvent event =
                    Emulator.getPluginManager().fireEvent(new FurniturePlacedEvent(item, owner, tile));
            if (event.isCancelled()) {
                return FurnitureMovementError.CANCEL_PLUGIN_PLACE;
            }
            pluginHelper = event.hasPluginHelper();
        }

        RoomLayout layout = this.room.getLayout();
        Set<RoomTile> occupiedTiles = layout.getTilesAt(
                tile, item.getBaseItem().getWidth(), item.getBaseItem().getLength(), rotation);
        FurnitureMovementError fits = this.furnitureFitsAt(tile, item, rotation, true);
        if (fits != FurnitureMovementError.NONE && !pluginHelper) {
            return fits;
        }

        double height = tile.getStackHeight();
        for (RoomTile occupiedTile : occupiedTiles) {
            height = Math.max(height, occupiedTile.getStackHeight());
        }

        if (Emulator.getPluginManager().isRegistered(FurnitureBuildheightEvent.class, true)) {
            FurnitureBuildheightEvent event =
                    Emulator.getPluginManager().fireEvent(new FurnitureBuildheightEvent(item, owner, 0.00, height));
            if (event.hasChangedHeight()) {
                height = layout.getHeightAtSquare(tile.x, tile.y) + event.getUpdatedHeight();
            }
        }

        item.setZ(height);
        item.setX(tile.x);
        item.setY(tile.y);
        item.setRotation(rotation);
        this.ensureOwnerName(item, owner);
        item.needsUpdate(true);
        this.facade.addHabboItem(item);
        item.setRoomId(this.room.getId());
        item.onPlace(this.room);
        this.room.updateTiles(occupiedTiles);
        this.room.sendComposer(
                new AddFloorItemComposer(item, this.facade.getFurniOwnerName(item.getUserId())).compose());

        if (RoomConfInvisSupport.isControllerItem(item) || RoomConfInvisSupport.isTarget(item)) {
            RoomConfInvisSupport.sendState(this.room);
        }
        if (RoomAreaHideSupport.isControllerItem(item)) {
            RoomAreaHideSupport.sendState(this.room, item);
        } else {
            RoomAreaHideSupport.synchronizeClientVisibility(this.room);
        }
        if (RoomHanditemBlockSupport.isControllerItem(item)) {
            RoomHanditemBlockSupport.sendState(this.room);
        }

        for (RoomTile occupiedTile : occupiedTiles) {
            this.room.updateHabbosAt(occupiedTile.x, occupiedTile.y);
            this.room.updateBotsAt(occupiedTile.x, occupiedTile.y);
        }
        Emulator.getThreading().runHabboItemPersistence(item);
        return FurnitureMovementError.NONE;
    }

    FurnitureMovementError placeWallFurniture(HabboItem item, String wallPosition, Habbo owner) {
        if (this.isYoutubeInteractionLimitReached(item)) {
            return FurnitureMovementError.YOUTUBE_ALREADY_IN_ROOM;
        }

        if (!(this.room.hasRights(owner)
                || this.room.getGuildRightLevel(owner).isEqualOrGreaterThan(RoomRightLevels.GUILD_RIGHTS)
                || BuildersClubRoomSupport.canPlaceInRoom(owner, this.room))) {
            return FurnitureMovementError.NO_RIGHTS;
        }

        if (Emulator.getPluginManager().isRegistered(FurniturePlacedEvent.class, true)) {
            Event furniturePlacedEvent = new FurniturePlacedEvent(item, owner, null);
            Emulator.getPluginManager().fireEvent(furniturePlacedEvent);
            if (furniturePlacedEvent.isCancelled()) {
                return FurnitureMovementError.CANCEL_PLUGIN_PLACE;
            }
        }

        item.setWallPosition(wallPosition);
        this.ensureOwnerName(item, owner);
        this.room.sendComposer(
                new AddWallItemComposer(item, this.facade.getFurniOwnerName(item.getUserId())).compose());
        item.needsUpdate(true);
        this.facade.addHabboItem(item);
        item.setRoomId(this.room.getId());
        item.onPlace(this.room);
        RoomAreaHideSupport.synchronizeClientVisibility(this.room);
        Emulator.getThreading().runHabboItemPersistence(item);
        return FurnitureMovementError.NONE;
    }

    private boolean isYoutubeInteractionLimitReached(HabboItem item) {
        if (!(item instanceof InteractionYoutubeTV)) return false;

        for (HabboItem roomItem : this.facade.getFloorItems()) {
            if (roomItem != item && roomItem instanceof InteractionYoutubeTV) return true;
        }
        for (HabboItem roomItem : this.facade.getWallItems()) {
            if (roomItem != item && roomItem instanceof InteractionYoutubeTV) return true;
        }

        return false;
    }

    boolean isStackPlacementBypassItem(HabboItem item) {
        return item instanceof InteractionStackHelper
                || item instanceof InteractionTileWalkMagic
                || item instanceof InteractionStackWalkHelper;
    }

    boolean shouldPinStackHelperToFloor(HabboItem item) {
        return item instanceof InteractionStackHelper || item instanceof InteractionTileWalkMagic;
    }

    double resolveStackWalkHelperHeight(HabboItem item, RoomTile tile, Set<RoomTile> occupiedTiles) {
        HabboItem helper = this.findStackHeightHelperAt(tile, item);
        if (helper != null) {
            return Math.max(helper.getZ(), this.getMinimumTileHeight(occupiedTiles));
        }

        return Math.max(item.getZ(), this.getMinimumTileHeight(occupiedTiles));
    }

    HabboItem findStackHeightHelperAt(RoomTile tile, HabboItem exclude) {
        if (tile == null) {
            return null;
        }
        for (HabboItem helper : this.facade.getItemsAt(tile)) {
            if (helper != exclude && this.isStackPlacementBypassItem(helper)) {
                return helper;
            }
        }
        return null;
    }

    private double getMinimumTileHeight(Set<RoomTile> occupiedTiles) {
        double minimumHeight = 0.0D;
        for (RoomTile occupiedTile : occupiedTiles) {
            minimumHeight =
                    Math.max(minimumHeight, this.room.getLayout().getHeightAtSquare(occupiedTile.x, occupiedTile.y));
        }
        return minimumHeight;
    }

    private void ensureOwnerName(HabboItem item, Habbo owner) {
        if (!this.index.ownerNames().containsKey(item.getUserId()) && owner != null) {
            this.index
                    .ownerNames()
                    .put(
                            item.getUserId(),
                            item.getUserId() == BuildersClubRoomSupport.VIRTUAL_OWNER_ID
                                    ? BuildersClubRoomSupport.DISPLAY_OWNER_NAME
                                    : owner.getHabboInfo().getUsername());
        }
    }
}
