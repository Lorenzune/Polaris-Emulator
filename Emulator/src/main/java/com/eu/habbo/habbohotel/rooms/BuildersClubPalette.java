package com.eu.habbo.habbohotel.rooms;

import com.eu.habbo.habbohotel.catalog.BuildersBlocksCatalogAccessPage;
import com.eu.habbo.habbohotel.users.HabboItem;

/** Octane's color variable contract, backed by modern block instance colors. */
public final class BuildersClubPalette {
    public static final String RGB = "~recolorable_furni.color.rgb";
    public static final String RED = "~recolorable_furni.color.rgb.r";
    public static final String GREEN = "~recolorable_furni.color.rgb.g";
    public static final String BLUE = "~recolorable_furni.color.rgb.b";

    private BuildersClubPalette() {}

    public static boolean isKey(String key) {
        return RGB.equals(key) || RED.equals(key) || GREEN.equals(key) || BLUE.equals(key);
    }

    public static boolean hasColor(HabboItem item) {
        return item != null && BuildersBlocksCatalogAccessPage.isRecolorableBlock(item.getBaseItem());
    }

    public static boolean validValue(String key, int value) {
        return isKey(key) && value >= 0 && value <= (RGB.equals(key) ? 0xffffff : 255);
    }

    public static int withComponent(int rgb, String key, int value) {
        if (!validValue(key, value)) throw new IllegalArgumentException("Invalid block color component");
        if (RED.equals(key)) return (rgb & 0x00ffff) | (value << 16);
        if (GREEN.equals(key)) return (rgb & 0xff00ff) | (value << 8);
        if (BLUE.equals(key)) return (rgb & 0xffff00) | value;
        return value;
    }

    public static boolean write(Room room, HabboItem item, String key, int value) {
        return room != null
                && hasColor(item)
                && validValue(key, value)
                && item.isBuildersClubItem()
                && room.getHabboItem(item.getId()) == item
                && room.getWiredRuntime().writeBuildersBlockColor(item, key, value);
    }
}
