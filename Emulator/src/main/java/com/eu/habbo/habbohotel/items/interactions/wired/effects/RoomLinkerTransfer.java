package com.eu.habbo.habbohotel.items.interactions.wired.effects;

import com.eu.habbo.habbohotel.rooms.*;
import com.eu.habbo.habbohotel.users.Habbo;
import com.eu.habbo.messages.outgoing.hotelview.HotelViewComposer;
import java.util.function.Consumer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Failure recovery is scoped to this transfer; it never starts a second room entry. */
final class RoomLinkerTransfer {
    private static final Logger LOGGER = LoggerFactory.getLogger(RoomLinkerTransfer.class);
    private RoomLinkerTransfer() { }
    static void start(RoomManager manager, Habbo user, Room source, Room target, RoomTile tile, Consumer<Runnable> scheduleRecovery) {
        var client = user.getClient();
        try {
            manager.leaveRoom(user, source, false);
            manager.enterRoom(user, target.getId(), "", true, tile, true);
        } catch (RuntimeException failure) {
            LOGGER.warn("Room linker transfer to room {} failed for user {}", target.getId(), user.getHabboInfo().getId(), failure);
            if (user.getHabboInfo().getCurrentRoom() == null || (user.getHabboInfo().getCurrentRoom() == target && !user.getRoomUnit().isEntrySnapshotReady())) abort(manager, user, target);
            return;
        }
        // A denied/cancelled entry may return without setting loadingRoom or restoring a view.
        if (user.getHabboInfo().getCurrentRoom() == null && user.getHabboInfo().getLoadingRoom() == 0) {
            abort(manager, user, target);
            return;
        }
        var pendingUnit = user.getRoomUnit();
        scheduleRecovery.accept(() -> {
            synchronized (user) {
                if (user.getClient() != client || user.getRoomUnit() != pendingUnit
                        || (pendingUnit != null && pendingUnit.isEntrySnapshotReady())) return;
                Room current = user.getHabboInfo().getCurrentRoom();
                boolean pending = current == null && user.getHabboInfo().getLoadingRoom() == target.getId();
                boolean incomplete = current == target && user.getHabboInfo().getLoadingRoom() == 0;
                if (!pending && !incomplete) return;
                LOGGER.warn("Room linker entry timed out for user {} in room {}", user.getHabboInfo().getId(), target.getId());
                abort(manager, user, target);
            }
        });
    }
    private static void abort(RoomManager manager, Habbo user, Room target) {
        if (user.getHabboInfo().getCurrentRoom() == target) {
            try { manager.leaveRoom(user, target, false); }
            catch (RuntimeException failure) { LOGGER.warn("Could not clean up failed room linker entry", failure); }
            finally { user.getHabboInfo().setCurrentRoom(null); }
        }
        user.getHabboInfo().setLoadingRoom(0);
        if (user.getRoomUnit() != null) {
            user.getRoomUnit().isTeleporting = false;
            user.getRoomUnit().setCanLeaveRoomByDoor(true);
            user.getRoomUnit().setInRoom(false);
            user.getRoomUnit().setPathFinderRoom(null);
        }
        if (user.getClient() != null) user.getClient().sendResponse(new HotelViewComposer());
    }
}
