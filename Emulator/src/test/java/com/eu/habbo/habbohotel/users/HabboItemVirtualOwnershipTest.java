package com.eu.habbo.habbohotel.users;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.eu.habbo.habbohotel.items.Item;
import com.eu.habbo.habbohotel.rooms.Room;
import com.eu.habbo.habbohotel.rooms.RoomUnit;
import org.junit.jupiter.api.Test;

class HabboItemVirtualOwnershipTest {

    @Test
    void virtualFurnitureCreditsItsRealOwnerInAPrivateOwnedRoom() {
        TestItem item = new TestItem(1001, 77);
        item.setVirtualUserId(1);
        Room room = mock(Room.class);
        when(room.hasGuild()).thenReturn(false);
        when(room.getOwnerId()).thenReturn(77);

        assertEquals(1, item.getUserId());
        assertEquals(77, item.getDatabaseUserId());
        assertEquals(77, item.getAchievementOwnerId(room));
    }

    @Test
    void virtualFurnitureDoesNotCreditAnyoneInAGroupRoom() {
        TestItem item = new TestItem(1001, 77);
        item.setVirtualUserId(1);
        Room room = mock(Room.class);
        when(room.hasGuild()).thenReturn(true);

        assertEquals(0, item.getAchievementOwnerId(room));
    }

    @Test
    void virtualFurnitureDoesNotCreditAnUnrelatedPrivateRoomOwner() {
        TestItem item = new TestItem(1001, 77);
        item.setVirtualUserId(1);
        Room room = mock(Room.class);
        when(room.hasGuild()).thenReturn(false);
        when(room.getOwnerId()).thenReturn(88);

        assertEquals(0, item.getAchievementOwnerId(room));
    }

    private static final class TestItem extends HabboItem {
        private TestItem(int id, int ownerId) {
            super(id, ownerId, (Item) null, "0", 0, 0);
        }

        @Override
        public boolean canWalkOn(RoomUnit roomUnit, Room room, Object[] objects) {
            return false;
        }

        @Override
        public boolean isWalkable() {
            return false;
        }

        @Override
        public void onWalk(RoomUnit roomUnit, Room room, Object[] objects) {}
    }
}
