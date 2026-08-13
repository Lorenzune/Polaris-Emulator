package com.eu.habbo.habbohotel.rooms;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.eu.habbo.habbohotel.items.FurnitureType;
import com.eu.habbo.habbohotel.items.Item;
import com.eu.habbo.habbohotel.items.interactions.InteractionAreaHideControl;
import com.eu.habbo.habbohotel.users.HabboItem;
import org.junit.jupiter.api.Test;

class RoomAreaHideSupportTest {

    @Test
    void floorItemIsHiddenWhenItsFootprintIntersectsTheConfiguredArea() {
        InteractionAreaHideControl controller = controller(5, 5, 1, 1, false, false);
        HabboItem overlapping = item(2, FurnitureType.FLOOR, 4, 5, 2, 1);
        HabboItem outside = item(3, FurnitureType.FLOOR, 8, 5, 1, 1);

        assertTrue(RoomAreaHideSupport.shouldHideFloorItem(overlapping, controller));
        assertFalse(RoomAreaHideSupport.shouldHideFloorItem(outside, controller));
        assertFalse(RoomAreaHideSupport.shouldHideFloorItem(controller, controller));
    }

    @Test
    void invertedAreaHidesOnlyItemsOutsideItsBounds() {
        InteractionAreaHideControl controller = controller(5, 5, 2, 2, true, false);
        HabboItem inside = item(2, FurnitureType.FLOOR, 5, 5, 1, 1);
        HabboItem outside = item(3, FurnitureType.FLOOR, 9, 9, 1, 1);

        assertFalse(RoomAreaHideSupport.shouldHideFloorItem(inside, controller));
        assertTrue(RoomAreaHideSupport.shouldHideFloorItem(outside, controller));
    }

    @Test
    void wallItemsFollowTheDedicatedOptionAndInvisibilityState() {
        InteractionAreaHideControl controller = controller(0, 0, 1, 1, false, true);
        HabboItem wallItem = item(2, FurnitureType.WALL, 0, 0, 1, 1);

        assertTrue(RoomAreaHideSupport.shouldHideWallItem(wallItem, controller));

        controller.values.put("wallItems", "0");
        assertFalse(RoomAreaHideSupport.shouldHideWallItem(wallItem, controller));

        controller.values.put("wallItems", "1");
        controller.values.put("invisibility", "0");
        assertTrue(RoomAreaHideSupport.shouldHideWallItem(wallItem, controller));
    }

    @Test
    void invisibilityOptionOnlyControlsTheControllerFurni() {
        InteractionAreaHideControl controller = controller(0, 0, 1, 1, false, false);
        HabboItem floorItem = item(2, FurnitureType.FLOOR, 0, 0, 1, 1);

        controller.values.put("invisibility", "0");
        assertTrue(RoomAreaHideSupport.shouldHideFloorItem(floorItem, controller));
        assertFalse(RoomAreaHideSupport.shouldHideController(controller, true));

        controller.values.put("invisibility", "1");
        assertFalse(RoomAreaHideSupport.shouldHideController(controller, false));
        assertTrue(RoomAreaHideSupport.shouldHideController(controller, true));
    }

    private static InteractionAreaHideControl controller(
            int rootX, int rootY, int width, int length, boolean inverted, boolean wallItems) {
        Item baseItem = baseItem(FurnitureType.FLOOR, 1, 1);
        InteractionAreaHideControl controller = new InteractionAreaHideControl(1, 1, baseItem, "", 0, 0);
        controller.values.put("state", "1");
        controller.values.put("rootX", Integer.toString(rootX));
        controller.values.put("rootY", Integer.toString(rootY));
        controller.values.put("width", Integer.toString(width));
        controller.values.put("length", Integer.toString(length));
        controller.values.put("invisibility", "1");
        controller.values.put("wallItems", wallItems ? "1" : "0");
        controller.values.put("invert", inverted ? "1" : "0");
        return controller;
    }

    private static HabboItem item(
            int itemId, FurnitureType type, int x, int y, int width, int length) {
        HabboItem item = mock(HabboItem.class);
        Item baseItem = baseItem(type, width, length);
        when(item.getId()).thenReturn(itemId);
        when(item.getBaseItem()).thenReturn(baseItem);
        when(item.getX()).thenReturn((short) x);
        when(item.getY()).thenReturn((short) y);
        when(item.getRotation()).thenReturn(0);
        return item;
    }

    private static Item baseItem(FurnitureType type, int width, int length) {
        Item baseItem = mock(Item.class);
        when(baseItem.getType()).thenReturn(type);
        when(baseItem.getWidth()).thenReturn(width);
        when(baseItem.getLength()).thenReturn(length);
        return baseItem;
    }
}
