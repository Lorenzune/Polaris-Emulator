package com.eu.habbo.habbohotel.rooms;

import com.eu.habbo.Emulator;
import com.eu.habbo.habbohotel.gameclients.GameClientFlushBatch;
import com.eu.habbo.habbohotel.users.HabboItem;
import com.eu.habbo.messages.ServerMessage;
import com.eu.habbo.messages.outgoing.rooms.RoomRelativeMapComposer;
import com.eu.habbo.messages.outgoing.rooms.items.FloorItemUpdateComposer;
import com.eu.habbo.messages.outgoing.rooms.items.WallItemUpdateComposer;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Deque;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Replays the native item-update packets for occupied rooms after an item
 * definition reload. Work is split into small batches so a large hotel does
 * not turn a single {@code :update_items} into a network/CPU spike.
 */
public final class RoomItemLiveRefreshTask implements Runnable {

    private static final Logger LOGGER = LoggerFactory.getLogger(RoomItemLiveRefreshTask.class);

    private static final int MAX_COMPOSERS_PER_SLICE = 48;
    private static final long SLICE_DELAY_MILLIS = 5L;

    private final Deque<RoomRefresh> pendingRooms = new ArrayDeque<>();
    private final Runnable onComplete;
    private boolean finished;

    private RoomItemLiveRefreshTask(Collection<Room> rooms, Runnable onComplete) {
        if (rooms != null) {
            for (Room room : rooms) {
                if (room != null && room.isLoaded() && room.getUserCount() > 0 && room.getLayout() != null) {
                    this.pendingRooms.addLast(new RoomRefresh(room));
                }
            }
        }

        this.onComplete = onComplete;
    }

    /**
     * Starts a refresh for the supplied room snapshot.
     *
     * @return {@code true} when the work was scheduled, or {@code false} when
     *         the scheduler is unavailable.
     */
    public static boolean schedule(Collection<Room> rooms, Runnable onComplete) {
        RoomItemLiveRefreshTask task = new RoomItemLiveRefreshTask(rooms, onComplete);
        if (task.pendingRooms.isEmpty()) {
            task.finish();
            return true;
        }

        return Emulator.getThreading().run(task) != null;
    }

    @Override
    public void run() {
        if (this.finished || Emulator.isShuttingDown) {
            this.finish();
            return;
        }

        int remaining = MAX_COMPOSERS_PER_SLICE;

        try (GameClientFlushBatch ignored = GameClientFlushBatch.open()) {
            while (remaining > 0 && !this.pendingRooms.isEmpty()) {
                RoomRefresh refresh = this.pendingRooms.peekFirst();
                int sent = refresh.sendNext(remaining);
                remaining -= sent;

                if (refresh.isComplete()) {
                    this.pendingRooms.removeFirst();
                }

                // A room can be empty or be unloaded while the task waits in
                // the scheduler. Move to the next room instead of spinning.
                if (sent == 0 && !refresh.isComplete()) {
                    this.pendingRooms.removeFirst();
                }
            }
        } catch (Throwable throwable) {
            LOGGER.error("Room item refresh -> Failed while sending a batch", throwable);
            if (!this.pendingRooms.isEmpty()) {
                this.pendingRooms.removeFirst();
            }
        }

        if (this.pendingRooms.isEmpty() || Emulator.isShuttingDown) {
            this.finish();
            return;
        }

        if (Emulator.getThreading().run(this, SLICE_DELAY_MILLIS) == null) {
            LOGGER.warn("Room item refresh -> Scheduler became unavailable before completion");
            this.finish();
        }
    }

    private void finish() {
        if (this.finished) {
            return;
        }

        this.finished = true;
        if (this.onComplete != null) {
            this.onComplete.run();
        }
    }

    private static final class RoomRefresh {
        private final Room room;
        private List<HabboItem> floorItems;
        private List<HabboItem> wallItems;
        private boolean prepared;
        private boolean sentMap;
        private int floorIndex;
        private int wallIndex;

        private RoomRefresh(Room room) {
            this.room = room;
        }

        private int sendNext(int limit) {
            if (!this.isStillRelevant() || limit <= 0) {
                return 0;
            }

            if (!this.prepared) {
                this.floorItems = new ArrayList<>(this.room.getFloorItems());
                this.wallItems = new ArrayList<>(this.room.getWallItems());
                this.prepared = true;
            }

            List<ServerMessage> messages = new ArrayList<>(limit);
            if (!this.sentMap) {
                messages.add(new RoomRelativeMapComposer(this.room).compose());
                this.sentMap = true;
            }

            while (messages.size() < limit && this.floorIndex < this.floorItems.size()) {
                HabboItem item = this.floorItems.get(this.floorIndex++);
                if (this.isCurrentItem(item)) {
                    messages.add(new FloorItemUpdateComposer(item).compose());
                }
            }

            while (messages.size() < limit && this.floorIndex >= this.floorItems.size()
                    && this.wallIndex < this.wallItems.size()) {
                HabboItem item = this.wallItems.get(this.wallIndex++);
                if (this.isCurrentItem(item)) {
                    messages.add(new WallItemUpdateComposer(item).compose());
                }
            }

            if (!messages.isEmpty()) {
                this.room.sendComposers(messages);
            }

            return messages.size();
        }

        private boolean isStillRelevant() {
            return this.room.isLoaded() && this.room.getUserCount() > 0 && this.room.getLayout() != null;
        }

        private boolean isCurrentItem(HabboItem item) {
            return item != null
                    && item.getRoomId() == this.room.getId()
                    && item.getBaseItem() != null
                    && this.room.getHabboItem(item.getId()) == item;
        }

        private boolean isComplete() {
            return !this.isStillRelevant()
                    || (this.prepared && this.sentMap && this.floorIndex >= this.floorItems.size()
                            && this.wallIndex >= this.wallItems.size());
        }
    }
}
