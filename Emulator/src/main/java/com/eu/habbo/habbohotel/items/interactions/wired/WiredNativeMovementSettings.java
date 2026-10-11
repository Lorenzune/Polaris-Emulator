package com.eu.habbo.habbohotel.items.interactions.wired;

import com.eu.habbo.Emulator;
import com.eu.habbo.habbohotel.items.interactions.InteractionWired;
import com.eu.habbo.habbohotel.items.interactions.wired.effects.WiredEffectBotTeleport;
import com.eu.habbo.habbohotel.items.interactions.wired.effects.WiredEffectBotWalkToFurni;
import com.eu.habbo.habbohotel.items.interactions.wired.effects.WiredEffectChangeFurniDirection;
import com.eu.habbo.habbohotel.items.interactions.wired.effects.WiredEffectForwardUserToRoom;
import com.eu.habbo.habbohotel.items.interactions.wired.effects.WiredEffectFurniToFurni;
import com.eu.habbo.habbohotel.items.interactions.wired.effects.WiredEffectFurniToUser;
import com.eu.habbo.habbohotel.items.interactions.wired.effects.WiredEffectMoveFurniAsGroup;
import com.eu.habbo.habbohotel.items.interactions.wired.effects.WiredEffectMoveFurniAway;
import com.eu.habbo.habbohotel.items.interactions.wired.effects.WiredEffectMoveFurniTo;
import com.eu.habbo.habbohotel.items.interactions.wired.effects.WiredEffectMoveFurniTowards;
import com.eu.habbo.habbohotel.items.interactions.wired.effects.WiredEffectMoveRotateFurni;
import com.eu.habbo.habbohotel.items.interactions.wired.effects.WiredEffectMoveRotateUser;
import com.eu.habbo.habbohotel.items.interactions.wired.effects.WiredEffectSetAltitude;
import com.eu.habbo.habbohotel.items.interactions.wired.effects.WiredEffectTeleport;
import com.eu.habbo.habbohotel.items.interactions.wired.effects.WiredEffectUserToFurni;
import com.eu.habbo.habbohotel.items.interactions.wired.extra.WiredExtraAnimationTime;
import com.eu.habbo.habbohotel.items.interactions.wired.extra.WiredExtraMoveCarryUsers;
import com.eu.habbo.habbohotel.items.interactions.wired.extra.WiredExtraMoveNoAnimation;
import com.eu.habbo.habbohotel.items.interactions.wired.extra.WiredExtraMovePhysics;
import com.eu.habbo.habbohotel.rooms.Room;
import java.math.BigDecimal;

/** Separates SWF fields and origins without reinterpreting existing local saves. */
public final class WiredNativeMovementSettings {
    private WiredNativeMovementSettings() {}

    public static WiredSettings normalize(InteractionWired item, WiredSettings s) {
        if (!s.isNativeVariableFormat()) return s;
        if (item instanceof WiredExtraAnimationTime
                || item instanceof WiredExtraMoveCarryUsers
                || item instanceof WiredExtraMoveNoAnimation
                || item instanceof WiredExtraMovePhysics) return normalizeExtra(item, s);
        int[] p = s.getIntParams();
        if (s.getVariableTokens().length != 0 || s.getQuantifier() != 0 || s.isFilterExisting() || s.isInvert())
            throw new IllegalArgumentException("Unexpected movement fields");
        boolean bot = item instanceof WiredEffectBotTeleport || item instanceof WiredEffectBotWalkToFurni;
        boolean user = item instanceof WiredEffectTeleport
                || item instanceof WiredEffectMoveRotateUser
                || item instanceof WiredEffectForwardUserToRoom
                || bot;
        boolean group = item instanceof WiredEffectMoveFurniAsGroup;
        boolean pair = item instanceof WiredEffectFurniToFurni;
        user |= item instanceof WiredEffectFurniToUser || item instanceof WiredEffectUserToFurni;
        int fc = item instanceof WiredEffectMoveRotateUser ? 0 : group || pair ? 2 : 1;
        int uc = user || group ? 1 : 0;
        require(s.getFurniSourceTypes(), fc);
        require(s.getUserSourceTypes(), uc);
        for (int i = 0; i < fc; i++)
            source(i == 1 && s.getFurniSourceTypes()[i] == 101 ? 100 : s.getFurniSourceTypes()[i], false, false);
        for (int source : s.getUserSourceTypes()) source(source, true, bot);
        if (!group && !pair && s.getFurniIds2().length != 0)
            throw new IllegalArgumentException("Unexpected secondary selection");
        if (!bot && !s.getStringParam().isEmpty()) throw new IllegalArgumentException("Unexpected movement text");
        int f = fc > 0 ? s.getFurniSourceTypes()[0] : 0;
        int u = uc > 0 ? s.getUserSourceTypes()[0] : 0;
        int[] result;
        String text = s.getStringParam();
        if (item instanceof WiredEffectMoveRotateFurni) {
            require(p, 2);
            range(p[0], 0, 11);
            range(p[1], 0, 3);
            result = new int[] {p[0], p[1], f};
        } else if (item instanceof WiredEffectChangeFurniDirection) {
            require(p, 3);
            range(p[0], 0, 7);
            range(p[1], 0, 6);
            range(p[2], 0, 1);
            result = new int[] {p[0], p[1], f, p[2]};
        } else if (item instanceof WiredEffectMoveFurniTo) {
            require(p, 2);
            range(p[0], 0, 6);
            if (p[0] % 2 != 0) throw new IllegalArgumentException("Invalid direction");
            range(p[1], 1, 5);
            result = new int[] {p[0], p[1], f};
        } else if (item instanceof WiredEffectSetAltitude) {
            require(p, 2);
            range(p[0], 0, 8000);
            range(p[1], 0, 2);
            text = BigDecimal.valueOf(p[0], 2).toPlainString();
            result = new int[] {p[1], f};
        } else if (item instanceof WiredEffectTeleport) {
            require(p, 1);
            range(p[0], 0, 1);
            result = new int[] {p[0], f, u};
        } else if (item instanceof WiredEffectUserToFurni) {
            require(p, 1);
            range(p[0], 0, 2);
            result = new int[] {f, u, p[0]};
        } else if (item instanceof WiredEffectFurniToUser) {
            require(p, 0);
            result = new int[] {f, u};
        } else if (pair) {
            require(p, 0);
            result = new int[] {f, s.getFurniSourceTypes()[1]};
            text = java.util.Arrays.stream(s.getFurniSourceTypes()[1] == 100 ? s.getFurniIds() : s.getFurniIds2())
                    .mapToObj(String::valueOf)
                    .collect(java.util.stream.Collectors.joining(";"));
        } else if (item instanceof WiredEffectMoveRotateUser) {
            require(p, 2);
            range(p[0], -1, 7);
            range(p[1], -1, 10);
            if (p[1] == 8) throw new IllegalArgumentException("Invalid SWF rotation");
            result = new int[] {p[0], p[1] == 9 ? 8 : p[1] == 10 ? 9 : p[1], u};
        } else if (group) {
            require(p, 3);
            range(p[0], 0, 1);
            range(p[1], -64, 64);
            range(p[2], -64, 64);
            result = new int[] {
                p[0], p[1], p[2], f, s.getFurniSourceTypes()[1] == 101 ? 100 : s.getFurniSourceTypes()[1], u
            };
        } else if (item instanceof WiredEffectForwardUserToRoom) {
            require(p, 0);
            if (f != 100) throw new IllegalArgumentException("Select a room linker");
            result = new int[] {u};
        } else if (bot) {
            require(p, 0);
            result = new int[] {f, u};
        } else if (item instanceof WiredEffectMoveFurniTowards || item instanceof WiredEffectMoveFurniAway) {
            require(p, 0);
            result = new int[] {f};
        } else throw new IllegalArgumentException("Unsupported movement");
        Room room = Emulator.getGameEnvironment().getRoomManager().getRoom(item.getRoomId());
        int maxItems = Emulator.getConfig().getInt("hotel.wired.furni.selection.count", 50);
        if (s.getDelay() < 0
                || s.getDelay() > Emulator.getConfig().getInt("hotel.wired.max_delay", 20)
                || s.getFurniIds().length > maxItems
                || s.getFurniIds2().length > maxItems)
            throw new IllegalArgumentException("Invalid movement selection/delay");
        if (room == null) throw new IllegalArgumentException("Room unavailable");
        if (item instanceof WiredEffectForwardUserToRoom
                && (s.getFurniIds().length != 1
                        || !(room.getHabboItem(s.getFurniIds()[0])
                                instanceof com.eu.habbo.habbohotel.items.interactions.InteractionRoomLinker)))
            throw new IllegalArgumentException("Select one room linker");
        for (int id : s.getFurniIds())
            if (room.getHabboItem(id) == null) throw new IllegalArgumentException("Missing selected furni");
        for (int id : s.getFurniIds2())
            if (room.getHabboItem(id) == null) throw new IllegalArgumentException("Missing secondary furni");
        return new WiredSettings(
                result,
                text,
                f == 100 ? s.getFurniIds() : new int[0],
                s.getFurniIds2(),
                new int[0],
                new int[0],
                new int[0],
                s.getStuffTypeSelectionCode(),
                s.getDelay());
    }

    private static void require(int[] values, int count) {
        if (values.length != count) throw new IllegalArgumentException("Invalid movement field count");
    }

    private static WiredSettings normalizeExtra(InteractionWired item, WiredSettings s) {
        if (!s.getStringParam().isEmpty()
                || s.getFurniIds().length != 0
                || s.getFurniIds2().length != 0
                || s.getVariableTokens().length != 0
                || s.getQuantifier() != 0
                || s.isFilterExisting()
                || s.isInvert()
                || s.getDelay() != 0) throw new IllegalArgumentException("Unexpected movement accessory fields");
        int[] p = s.getIntParams(), f = s.getFurniSourceTypes(), u = s.getUserSourceTypes(), translated;
        boolean physics = item instanceof WiredExtraMovePhysics, carry = item instanceof WiredExtraMoveCarryUsers;
        require(f, physics ? 2 : 0);
        require(u, physics || carry ? 1 : 0);
        for (int source : f) extraSource(source);
        for (int source : u) extraSource(source);
        if (physics) {
            require(p, 4);
            for (int flag : p) range(flag, 0, 1);
            translated = new int[] {p[0], p[1], p[2], p[3], f[0], f[1], u[0]};
        } else if (carry) {
            require(p, 1);
            range(p[0], 0, 1);
            translated = new int[] {p[0], u[0]};
        } else if (item instanceof WiredExtraAnimationTime) {
            require(p, 1);
            range(p[0], 50, 2000);
            if (p[0] % 50 != 0) throw new IllegalArgumentException("Invalid animation step");
            translated = p;
        } else {
            require(p, 0);
            translated = p;
        }
        return new WiredSettings(translated, "", new int[0], s.getStuffTypeSelectionCode(), 0);
    }

    private static void extraSource(int value) {
        if (value != 0 && value != 200 && value != 201 && value != 900)
            throw new IllegalArgumentException("Invalid movement accessory origin");
    }

    private static void range(int v, int min, int max) {
        if (v < min || v > max) throw new IllegalArgumentException("Movement field out of range");
    }

    private static void source(int v, boolean user, boolean bot) {
        if (v != 0 && v != 200 && v != 201 && (user ? (!(!bot && v == 11) && !(bot && v == 100)) : v != 100))
            throw new IllegalArgumentException("Invalid movement origin");
    }
}
