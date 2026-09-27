package com.eu.habbo.habbohotel.rooms.pathfinding.impl;

import com.eu.habbo.habbohotel.rooms.Room;
import com.eu.habbo.habbohotel.rooms.RoomAreaHideSupport;
import com.eu.habbo.habbohotel.rooms.RoomTile;
import com.eu.habbo.habbohotel.rooms.RoomUnit;
import java.util.HashMap;
import java.util.Map;

public class PathfinderContext {

  private final Room room;
  private final RoomTile newTile;
  private RoomTile goalLocation;
  private RoomUnit roomUnit;
  private final boolean isWalkthroughRetry;
  private final boolean canMoveDiagonally;
  private final RoomTile doorTile;
  private final boolean allowWalkthrough;
  private final Map<Long, RoomTile> tileCache;
  private final Map<Long, Boolean> hiddenAreaCache = new HashMap<>();

  public PathfinderContext(Room room, RoomTile newTile, RoomTile goalLocation, RoomUnit roomUnit,
      boolean isWalkthroughRetry, boolean canMoveDiagonally, RoomTile doorTile,
      boolean allowWalkthrough, Map<Long, RoomTile> tileCache) {
    this.newTile = newTile;
    this.goalLocation = goalLocation;
    this.roomUnit = roomUnit;
    this.isWalkthroughRetry = isWalkthroughRetry;
    this.canMoveDiagonally = canMoveDiagonally;
    this.doorTile = doorTile;
    this.allowWalkthrough = allowWalkthrough;
    this.tileCache = tileCache;
    this.room = room;
  }

  public static PathfinderContext buildContext(Room room, RoomTile newTile, RoomTile goalLocation,
      RoomUnit roomUnit, boolean isWalkthroughRetry) {
    return new PathfinderContext(room, newTile, goalLocation, roomUnit, isWalkthroughRetry,
        room.moveDiagonally(), room.getLayout().getDoorTile(), room.isAllowWalkthrough(),
        new HashMap<>());
  }

  public RoomTile getNewTile() {
    return this.newTile;
  }

  public RoomTile getGoalLocation() {
    return this.goalLocation;
  }

  public RoomUnit getRoomUnit() {
    return this.roomUnit;
  }

  public boolean isWalkthroughRetry() {
    return this.isWalkthroughRetry;
  }

  public boolean isCanMoveDiagonally() {
    return this.canMoveDiagonally;
  }

  public RoomTile getDoorTile() {
    return this.doorTile;
  }

  public boolean isAllowWalkthrough() {
    return this.allowWalkthrough;
  }

  public Map<Long, RoomTile> getTileCache() {
    return this.tileCache;
  }

  public boolean isTileHidden(RoomTile tile) {
    if (tile == null) {
      return false;
    }

    long key = ((long) tile.getX() << 32) | (tile.getY() & 0xFFFFFFFFL);
    return this.hiddenAreaCache.computeIfAbsent(key,
        ignored -> RoomAreaHideSupport.isTileHidden(this.room, tile.getX(), tile.getY()));
  }

  public void setGoalLocation(RoomTile goalLocation) {
    this.goalLocation = goalLocation;
  }

  public void setRoomUnit(RoomUnit roomUnit) {
    this.roomUnit = roomUnit;
  }

  public Room getRoom() {
    return room;
  }
}
