package com.eu.habbo.habbohotel.rooms;

import com.eu.habbo.Emulator;
import com.eu.habbo.habbohotel.items.FurnitureType;
import com.eu.habbo.habbohotel.items.interactions.InteractionWiredCondition;
import com.eu.habbo.habbohotel.items.interactions.InteractionWiredEffect;
import com.eu.habbo.habbohotel.items.interactions.InteractionWiredExtra;
import com.eu.habbo.habbohotel.items.interactions.InteractionWiredTrigger;
import com.eu.habbo.habbohotel.users.HabboItem;
import com.eu.habbo.habbohotel.wired.core.WiredManager;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Session baseline used by Wired's room-state rollback.
 *
 * <p>The baseline is captured only after a room has completed loading. It deliberately records
 * existing item IDs instead of a room inventory: items placed later stay where they are and items
 * removed later are never recreated.</p>
 */
final class RoomRollbackState {

    private final Room room;
    private Baseline baseline;

    RoomRollbackState(Room room) {
        this.room = room;
    }

    synchronized void capture() {
        Map<Integer, ItemState> itemStates = new HashMap<>();

        captureItems(this.room.getFloorItems(), itemStates);
        captureItems(this.room.getWallItems(), itemStates);

        Set<Integer> itemIds = Set.copyOf(itemStates.keySet());
        this.baseline = new Baseline(
                Map.copyOf(itemStates),
                itemIds,
                this.room.getFurniVariableManager().createRollbackSnapshot(itemIds),
                this.room.getWiredRuntime().createRollbackSnapshot(itemIds));
    }

    synchronized boolean restore() {
        if (!this.room.isLoaded() || this.baseline == null) {
            return false;
        }

        RoomLayout layout = this.room.getLayout();
        if (layout == null) {
            return false;
        }

        Set<RoomTile> affectedTiles = new HashSet<>();
        List<HabboItem> restoredItems = new ArrayList<>();

        for (ItemState state : this.baseline.itemStates().values()) {
            HabboItem item = this.room.getHabboItem(state.itemId());
            if (!isCurrentItem(item, state)) {
                continue;
            }

            boolean changed = state.restore(this.room, layout, item, affectedTiles);
            if (changed) {
                restoredItems.add(item);
            }
        }

        this.room.getFurniVariableManager().restoreRollbackSnapshot(
                this.baseline.furniVariables(), this.baseline.itemIds());
        this.room.getWiredRuntime().restoreRollbackSnapshot(this.baseline.runtime(), this.baseline.itemIds());

        if (!affectedTiles.isEmpty()) {
            this.room.updateTiles(affectedTiles);
            for (RoomTile tile : affectedTiles) {
                this.room.updateHabbosAt(tile.x, tile.y, this.room.getHabbosAt(tile.x, tile.y));
                this.room.updateBotsAt(tile.x, tile.y);
            }
        }

        for (HabboItem item : restoredItems) {
            item.needsUpdate(true);
            Emulator.getThreading().run(item);
            this.room.updateItem(item);
        }

        return true;
    }

    private void captureItems(Iterable<HabboItem> items, Map<Integer, ItemState> itemStates) {
        for (HabboItem item : items) {
            if (item == null || item.getBaseItem() == null || item.getRoomId() != this.room.getId()) {
                continue;
            }

            itemStates.put(item.getId(), ItemState.capture(item));
        }
    }

    private boolean isCurrentItem(HabboItem item, ItemState state) {
        return item != null
                && item.getRoomId() == this.room.getId()
                && item.getBaseItem() != null
                && item.getBaseItem().getType() == state.type();
    }

    private record Baseline(
            Map<Integer, ItemState> itemStates,
            Set<Integer> itemIds,
            RoomFurniVariableManager.RollbackSnapshot furniVariables,
            RoomWiredRuntime.RollbackSnapshot runtime) {}

    private record ItemState(
            int itemId,
            FurnitureType type,
            short x,
            short y,
            double z,
            int rotation,
            String wallPosition,
            String extraData) {

        private static ItemState capture(HabboItem item) {
            return new ItemState(
                    item.getId(),
                    item.getBaseItem().getType(),
                    item.getX(),
                    item.getY(),
                    item.getZ(),
                    item.getRotation(),
                    item.getWallPosition(),
                    item.getExtradata());
        }

        private boolean restore(Room room, RoomLayout layout, HabboItem item, Set<RoomTile> affectedTiles) {
            if (this.type == FurnitureType.WALL) {
                boolean changed = !java.util.Objects.equals(item.getWallPosition(), this.wallPosition)
                        || !java.util.Objects.equals(item.getExtradata(), this.extraData);
                if (changed) {
                    item.setWallPosition(this.wallPosition);
                    item.setExtradata(this.extraData);
                }
                return changed;
            }

            short oldX = item.getX();
            short oldY = item.getY();
            int oldRotation = item.getRotation();
            boolean moved = oldX != this.x || oldY != this.y;
            boolean changed = oldX != this.x
                    || oldY != this.y
                    || oldRotation != this.rotation
                    || Double.compare(item.getZ(), this.z) != 0
                    || !java.util.Objects.equals(item.getExtradata(), this.extraData);
            if (!changed) {
                return false;
            }

            addFootprint(layout, item, oldX, oldY, oldRotation, affectedTiles);
            addFootprint(layout, item, this.x, this.y, this.rotation, affectedTiles);

            item.setX(this.x);
            item.setY(this.y);
            item.setZ(this.z);
            item.setRotation(this.rotation);
            item.setExtradata(this.extraData);

            if (moved) {
                updateWiredLocation(room, item, oldX, oldY);
            }
            return true;
        }

        private static void addFootprint(
                RoomLayout layout, HabboItem item, short x, short y, int rotation, Set<RoomTile> affectedTiles) {
            RoomTile origin = layout.getTile(x, y);
            if (origin == null) {
                return;
            }

            affectedTiles.addAll(layout.getTilesAt(
                    origin, item.getBaseItem().getWidth(), item.getBaseItem().getLength(), rotation));
        }

        private static void updateWiredLocation(Room room, HabboItem item, int oldX, int oldY) {
            if (item instanceof InteractionWiredTrigger trigger) {
                room.getRoomSpecialTypes().updateTriggerLocation(trigger, oldX, oldY);
            } else if (item instanceof InteractionWiredEffect effect) {
                room.getRoomSpecialTypes().updateEffectLocation(effect, oldX, oldY);
            } else if (item instanceof InteractionWiredCondition condition) {
                room.getRoomSpecialTypes().updateConditionLocation(condition, oldX, oldY);
            } else if (item instanceof InteractionWiredExtra extra) {
                room.getRoomSpecialTypes().updateExtraLocation(extra, oldX, oldY);
            } else {
                return;
            }

            WiredManager.invalidateRoom(room);
        }
    }
}
