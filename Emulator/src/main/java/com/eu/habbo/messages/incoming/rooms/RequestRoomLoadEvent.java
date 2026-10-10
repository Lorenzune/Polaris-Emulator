package com.eu.habbo.messages.incoming.rooms;

import com.eu.habbo.Emulator;
import com.eu.habbo.habbohotel.rooms.Room;
import com.eu.habbo.habbohotel.rooms.RoomTile;
import com.eu.habbo.messages.incoming.MessageHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class RequestRoomLoadEvent extends MessageHandler {

    private static final Logger LOGGER = LoggerFactory.getLogger(RequestRoomLoadEvent.class);

    @Override
    public void handle() throws Exception {
        int roomId = this.packet.readInt();
        String password = this.packet.readString();

        // Consume legacy coordinates for wire compatibility; only the server chooses the spawn.
        int spawnX = -1;
        int spawnY = -1;

        try {
            int remaining = this.packet.getBuffer().readableBytes();
            if (remaining >= 8) {
                this.packet.readInt();
                this.packet.readInt();
            }
        } catch (Exception e) {
            spawnX = -1;
            spawnY = -1;
        }

        long nowMillis = System.currentTimeMillis();
        // Reset stale loadingRoom if timestamp has expired (indicates failed/stuck load)
        if (this.client.getHabbo().getHabboInfo().getLoadingRoom() != 0
                && RoomLoadTiming.expired(this.client.getHabbo().getHabboInfo().getLoadingRoomStartedAt(), nowMillis)) {
            this.client.getHabbo().getHabboInfo().setLoadingRoom(0);
        }

        if (RoomLoadTiming.canStart(
                this.client.getHabbo().getHabboInfo().getLoadingRoom(),
                this.client.getHabbo().getHabboStats().roomEnterTimestamp,
                nowMillis)) {

            Room room = this.client.getHabbo().getHabboInfo().getCurrentRoom();
            if (room != null) {
                // If re-entering the same room (session resume / reconnect), capture
                // the user's current position before removal so we can respawn there.
                if (room.getId() == roomId
                        && spawnX < 0
                        && spawnY < 0
                        && this.client.getHabbo().getRoomUnit() != null
                        && this.client.getHabbo().getRoomUnit().getCurrentLocation() != null) {
                    RoomTile currentLoc = this.client.getHabbo().getRoomUnit().getCurrentLocation();
                    spawnX = currentLoc.x;
                    spawnY = currentLoc.y;
                    LOGGER.info(
                            "[RequestRoomLoadEvent] Re-entering same room {} — preserving position ({}, {})",
                            roomId,
                            spawnX,
                            spawnY);
                }

                Emulator.getGameEnvironment().getRoomManager().logExit(this.client.getHabbo());

                room.removeHabbo(this.client.getHabbo(), true);

                this.client.getHabbo().getHabboInfo().setCurrentRoom(null);
            }

            if (this.client.getHabbo().getRoomUnit() != null
                    && this.client.getHabbo().getRoomUnit().isTeleporting) {
                this.client.getHabbo().getRoomUnit().isTeleporting = false;
            }

            // Resolve spawn tile from coordinates (from the saved server position above)
            RoomTile spawnTile = null;

            if (spawnX >= 0 && spawnY >= 0) {
                Room targetRoom = Emulator.getGameEnvironment().getRoomManager().getRoom(roomId);
                if (targetRoom == null) {
                    targetRoom = Emulator.getGameEnvironment().getRoomManager().loadRoom(roomId);
                }
                if (targetRoom != null && targetRoom.getLayout() != null) {
                    RoomTile tile = targetRoom.getLayout().getTile((short) spawnX, (short) spawnY);
                    if (tile != null && tile.isWalkable()) {
                        spawnTile = tile;
                    }
                }
            }

            boolean isReconnect = spawnTile != null;
            LOGGER.debug(
                    "[RequestRoomLoadEvent] Entering room {} (spawnTile={}, isReconnect={})",
                    roomId,
                    spawnTile != null ? "(" + spawnTile.x + "," + spawnTile.y + ")" : "door",
                    isReconnect);
            Emulator.getGameEnvironment()
                    .getRoomManager()
                    .enterRoom(this.client.getHabbo(), roomId, password, false, spawnTile, isReconnect);
        }
    }
}
