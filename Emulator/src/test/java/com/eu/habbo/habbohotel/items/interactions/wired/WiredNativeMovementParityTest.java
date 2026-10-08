package com.eu.habbo.habbohotel.items.interactions.wired;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.RETURNS_DEEP_STUBS;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.anyCollection;
import static org.mockito.Mockito.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

import com.eu.habbo.Emulator;
import com.eu.habbo.core.ConfigurationManager;
import com.eu.habbo.habbohotel.GameEnvironment;
import com.eu.habbo.habbohotel.items.Item;
import com.eu.habbo.habbohotel.items.interactions.InteractionWiredEffect;
import com.eu.habbo.habbohotel.items.interactions.InteractionWiredExtra;
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
import com.eu.habbo.habbohotel.rooms.FurnitureMovementError;
import com.eu.habbo.habbohotel.rooms.Room;
import com.eu.habbo.habbohotel.rooms.RoomLayout;
import com.eu.habbo.habbohotel.rooms.RoomTile;
import com.eu.habbo.habbohotel.rooms.RoomTileState;
import com.eu.habbo.habbohotel.rooms.RoomUnit;
import com.eu.habbo.habbohotel.users.HabboItem;
import com.eu.habbo.habbohotel.wired.core.WiredContext;
import com.eu.habbo.habbohotel.wired.core.WiredEvent;
import com.eu.habbo.habbohotel.wired.core.WiredMoveCarryHelper;
import com.eu.habbo.habbohotel.wired.core.WiredServices;
import com.eu.habbo.habbohotel.wired.core.WiredSourceUtil;
import com.eu.habbo.habbohotel.wired.core.WiredState;
import java.sql.ResultSet;
import java.util.List;
import org.junit.jupiter.api.Test;

class WiredNativeMovementParityTest {
    @Test
    void movementAccessoriesValidateNativeFieldsAndPreserveTheirOriginsOnReload() throws Exception {
        var physics = new WiredExtraMovePhysics(1, 1, mock(Item.class), "", 0, 0);
        var carry = new WiredExtraMoveCarryUsers(2, 1, mock(Item.class), "", 0, 0);
        var animation = new WiredExtraAnimationTime(3, 1, mock(Item.class), "", 0, 0);
        var noAnimation = new WiredExtraMoveNoAnimation(4, 1, mock(Item.class), "", 0, 0);
        InteractionWiredExtra[] extras = {physics, carry, animation, noAnimation};
        int[][] params = {{1, 1, 1, 1}, {1}, {750}, {}};
        for (int i = 0; i < extras.length; i++) {
            var s = nativeSettings(
                    params[i],
                    i == 0 ? new int[] {900, 201} : new int[0],
                    i == 0 ? new int[] {200} : i == 1 ? new int[] {900} : new int[0]);
            s.setDelay(0);
            assertTrue(extras[i].saveData(s, null));
            String saved = extras[i].getWiredData();
            var rs = mock(ResultSet.class);
            when(rs.getString("wired_data")).thenReturn(saved);
            extras[i].onPickUp();
            extras[i].loadWiredData(rs, mock(Room.class));
            assertEquals(saved, extras[i].getWiredData());
        }
        assertEquals(900, physics.getMoveThroughFurniSource());
        assertEquals(201, physics.getBlockByFurniSource());
        assertEquals(200, physics.getMoveThroughUsersSource());
        assertEquals(750, animation.getDurationMs());
        var invalid = nativeSettings(new int[] {751}, new int[0], new int[0]);
        invalid.setDelay(0);
        assertThrows(IllegalArgumentException.class, () -> animation.saveData(invalid, null));
        assertEquals(750, animation.getDurationMs());
        animation.onPickUp();
        assertEquals(50, animation.getDurationMs());
    }

    private static WiredSettings nativeSettings(int[] p, int[] f, int[] u) {
        var s = new WiredSettings(p, "", new int[0], 0, 2);
        s.setNativeVariableFormat(true);
        s.setFurniSourceTypes(f);
        s.setUserSourceTypes(u);
        return s;
    }

    private static WiredContext context(Room room) {
        return new WiredContext(
                WiredEvent.builder(WiredEvent.Type.CUSTOM, room).build(),
                null,
                mock(WiredServices.class),
                new WiredState(100));
    }

    private static final class Fixture {
        final GameEnvironment env = mock(GameEnvironment.class, RETURNS_DEEP_STUBS);
        final ConfigurationManager config = mock(ConfigurationManager.class);
        final Room room = mock(Room.class, RETURNS_DEEP_STUBS);

        Fixture() {
            when(env.getRoomManager().getRoom(8)).thenReturn(room);
            when(room.getId()).thenReturn(8);
            when(room.getHabboItem(404)).thenReturn(null);
            when(config.getInt("hotel.wired.furni.selection.count")).thenReturn(50);
            when(config.getInt("hotel.wired.furni.selection.count", 50)).thenReturn(50);
            when(config.getInt("hotel.wired.furni.selection.count", 5)).thenReturn(50);
            when(config.getInt("hotel.wired.max_delay", 20)).thenReturn(20);
        }

        HabboItem item(int id, int x, int y) {
            var i = mock(HabboItem.class);
            when(i.getId()).thenReturn(id);
            when(i.getRoomId()).thenReturn(8);
            when(i.getX()).thenReturn((short) x);
            when(i.getY()).thenReturn((short) y);
            when(room.getHabboItem(id)).thenReturn(i);
            return i;
        }
    }

    @Test
    void convertsEveryMovementFieldAndPreservesSavedJson() throws Exception {
        var f = new Fixture();
        try (var e = mockStatic(Emulator.class)) {
            e.when(Emulator::getGameEnvironment).thenReturn(f.env);
            e.when(Emulator::getConfig).thenReturn(f.config);
            InteractionWiredEffect[] effects = {
                new WiredEffectMoveRotateFurni(1, 1, mock(Item.class), "", 0, 0),
                        new WiredEffectTeleport(2, 1, mock(Item.class), "", 0, 0),
                new WiredEffectMoveFurniTowards(3, 1, mock(Item.class), "", 0, 0),
                        new WiredEffectMoveFurniAway(4, 1, mock(Item.class), "", 0, 0),
                new WiredEffectChangeFurniDirection(5, 1, mock(Item.class), "", 0, 0),
                        new WiredEffectMoveFurniTo(6, 1, mock(Item.class), "", 0, 0),
                new WiredEffectSetAltitude(7, 1, mock(Item.class), "", 0, 0),
                        new WiredEffectMoveRotateUser(8, 1, mock(Item.class), "", 0, 0),
                new WiredEffectBotTeleport(9, 1, mock(Item.class), "", 0, 0),
                        new WiredEffectBotWalkToFurni(10, 1, mock(Item.class), "", 0, 0),
                new WiredEffectUserToFurni(11, 1, mock(Item.class), "", 0, 0),
                        new WiredEffectFurniToUser(12, 1, mock(Item.class), "", 0, 0)
            };
            int[][] ints = {{11, 3}, {1}, {}, {}, {7, 6, 1}, {6, 5}, {7999, 2}, {7, 10}, {}, {}, {2}, {}};
            int[][] expected = {
                {11, 3, 200},
                {1, 200, 201},
                {200},
                {200},
                {7, 6, 200, 1},
                {6, 5, 200},
                {2, 200},
                {7, 9, 201},
                {200, 201},
                {200, 201},
                {200, 201, 2},
                {200, 201}
            };
            for (int i = 0; i < effects.length; i++) {
                var effect = effects[i];
                effect.setRoomId(8);
                boolean user = i == 1 || i >= 7;
                var s = nativeSettings(
                        ints[i], i == 7 ? new int[0] : new int[] {200}, user ? new int[] {201} : new int[0]);
                assertArrayEquals(
                        expected[i],
                        WiredNativeMovementSettings.normalize(effect, s).getIntParams());
                assertTrue(effect.saveData(s, null));
                String saved = effect.getWiredData();
                var rs = mock(ResultSet.class);
                when(rs.getString("wired_data")).thenReturn(saved);
                effect.onPickUp();
                effect.loadWiredData(rs, f.room);
                assertEquals(saved, effect.getWiredData(), effect.getClass().getSimpleName());
            }
        }
    }

    @Test
    void rejectsInvalidNativeSaveWithoutChangingExistingState() throws Exception {
        var f = new Fixture();
        var effect = new WiredEffectMoveRotateFurni(1, 1, mock(Item.class), "", 0, 0);
        effect.setRoomId(8);
        try (var e = mockStatic(Emulator.class)) {
            e.when(Emulator::getGameEnvironment).thenReturn(f.env);
            e.when(Emulator::getConfig).thenReturn(f.config);
            var s = nativeSettings(new int[] {4, 1}, new int[] {200}, new int[0]);
            assertTrue(effect.saveData(s, null));
            String before = effect.getWiredData();
            s.setIntParams(new int[] {12, 1});
            assertThrows(IllegalArgumentException.class, () -> effect.saveData(s, null));
            assertEquals(before, effect.getWiredData());
            s.setIntParams(new int[] {4, 1});
            s.setDelay(-1);
            assertThrows(IllegalArgumentException.class, () -> effect.saveData(s, null));
            assertEquals(before, effect.getWiredData());
            s.setDelay(2);
            s.setFurniIds(new int[] {404});
            assertThrows(IllegalArgumentException.class, () -> effect.saveData(s, null));
            assertEquals(before, effect.getWiredData());
        }
    }

    @Test
    void groupUsesDestinationOffsetsKeepsRelativePositionsAndReloadsBothSelections() throws Exception {
        var f = new Fixture();
        var a = f.item(10, 2, 2);
        var b = f.item(11, 3, 2);
        var target = f.item(20, 10, 10);
        var effect = new WiredEffectMoveFurniAsGroup(1, 1, mock(Item.class), "", 0, 0);
        effect.setRoomId(8);
        var ctx = context(f.room);
        var fromA = new RoomTile((short) 2, (short) 2, (short) 0, RoomTileState.OPEN, true);
        var fromB = new RoomTile((short) 3, (short) 2, (short) 0, RoomTileState.OPEN, true);
        var dest = new RoomTile((short) 10, (short) 10, (short) 0, RoomTileState.OPEN, true);
        var toA = new RoomTile((short) 11, (short) 9, (short) 0, RoomTileState.OPEN, true);
        var toB = new RoomTile((short) 12, (short) 9, (short) 0, RoomTileState.OPEN, true);
        when(f.room.getLayout().getTile((short) 2, (short) 2)).thenReturn(fromA);
        when(f.room.getLayout().getTile((short) 3, (short) 2)).thenReturn(fromB);
        when(f.room.getLayout().getTile((short) 10, (short) 10)).thenReturn(dest);
        when(f.room.getLayout().getTile((short) 11, (short) 9)).thenReturn(toA);
        when(f.room.getLayout().getTile((short) 12, (short) 9)).thenReturn(toB);
        try (var e = mockStatic(Emulator.class);
                var sources = mockStatic(WiredSourceUtil.class);
                var move = mockStatic(WiredMoveCarryHelper.class)) {
            e.when(Emulator::getGameEnvironment).thenReturn(f.env);
            e.when(Emulator::getConfig).thenReturn(f.config);
            var s = nativeSettings(new int[] {0, 1, -1}, new int[] {100, 101}, new int[] {0});
            s.setFurniIds(new int[] {10, 11});
            s.setFurniIds2(new int[] {20});
            assertTrue(effect.saveData(s, null));
            String saved = effect.getWiredData();
            var rs = mock(ResultSet.class);
            when(rs.getString("wired_data")).thenReturn(saved);
            effect.onPickUp();
            effect.loadWiredData(rs, f.room);
            assertEquals(saved, effect.getWiredData());
            sources.when(() -> WiredSourceUtil.resolveItems(eq(ctx), eq(100), anyCollection()))
                    .thenAnswer(call -> {
                        var items = (java.util.Collection<HabboItem>) call.getArgument(2);
                        return new java.util.ArrayList<>(items);
                    });
            effect.execute(ctx);
            move.verify(() -> WiredMoveCarryHelper.moveFurni(f.room, effect, a, toA, 0, null, false, ctx));
            move.verify(() -> WiredMoveCarryHelper.moveFurni(f.room, effect, b, toB, 0, null, false, ctx));
        }
    }

    @Test
    void altitudeAcceptsEightyAndRetainsHundredths() throws Exception {
        var f = new Fixture();
        var effect = new WiredEffectSetAltitude(1, 1, mock(Item.class), "", 0, 0);
        effect.setRoomId(8);
        try (var e = mockStatic(Emulator.class)) {
            e.when(Emulator::getGameEnvironment).thenReturn(f.env);
            e.when(Emulator::getConfig).thenReturn(f.config);
            assertTrue(effect.saveData(nativeSettings(new int[] {8000, 2}, new int[] {200}, new int[0]), null));
            assertTrue(effect.getWiredData().contains("\"altitude\":\"80\""));
            assertEquals(80d, Room.MAXIMUM_FURNI_HEIGHT);
            assertThrows(
                    IllegalArgumentException.class,
                    () -> effect.saveData(nativeSettings(new int[] {8001, 2}, new int[] {200}, new int[0]), null));
        }
    }

    @Test
    void eightDirectionButtonsAgreeWithTheRoomLayoutForFurniAndUsers() throws Exception {
        var f = new Fixture();
        var item = f.item(10, 2, 2);
        var unit = mock(RoomUnit.class);
        var rs = mock(ResultSet.class);
        when(rs.getString("heightmap")).thenReturn("00000\r00000\r00000\r00000\r00000");
        try (var e = mockStatic(Emulator.class);
                var source = mockStatic(WiredSourceUtil.class);
                var move = mockStatic(WiredMoveCarryHelper.class)) {
            e.when(Emulator::getGameEnvironment).thenReturn(f.env);
            e.when(Emulator::getConfig).thenReturn(f.config);
            var layout = new RoomLayout(rs, f.room);
            when(f.room.getLayout()).thenReturn(layout);
            var current = layout.getTile((short) 2, (short) 2);
            when(unit.getCurrentLocation()).thenReturn(current);
            var ctx = context(f.room);
            source.when(() -> WiredSourceUtil.resolveItems(eq(ctx), eq(100), anyCollection()))
                    .thenReturn(List.of(item));
            move.when(() -> WiredMoveCarryHelper.getMovementError(eq(f.room), any(), eq(item), any(), eq(0), eq(ctx)))
                    .thenReturn(FurnitureMovementError.NONE);
            int[] buttons = {4, 8, 5, 9, 6, 10, 7, 11};
            var user = new WiredEffectMoveRotateUser(9, 1, mock(Item.class), "", 0, 0);
            var getTarget = WiredEffectMoveRotateUser.class.getDeclaredMethod(
                    "getTargetTile", Room.class, RoomUnit.class, int.class);
            getTarget.setAccessible(true);
            for (int direction = 0; direction < 8; direction++) {
                var effect = new WiredEffectMoveRotateFurni(direction + 1, 1, mock(Item.class), "", 0, 0);
                effect.setRoomId(8);
                var s = nativeSettings(new int[] {buttons[direction], 0}, new int[] {100}, new int[0]);
                s.setFurniIds(new int[] {10});
                assertTrue(effect.saveData(s, null));
                var expected = layout.getTileInFront(current, direction);
                effect.execute(ctx);
                move.verify(() -> WiredMoveCarryHelper.moveFurni(f.room, effect, item, expected, 0, null, false, ctx));
                assertSame(expected, getTarget.invoke(user, f.room, unit, direction));
            }
        }
    }

    @Test
    void signalMoverUsesPrimarySelectedTargetAndPreservesItAfterReload() throws Exception {
        var f = new Fixture();
        f.item(20, 2, 2);
        var pair = new WiredEffectFurniToFurni(1, 1, mock(Item.class), "", 0, 0);
        pair.setRoomId(8);
        try (var emulator = mockStatic(Emulator.class)) {
            emulator.when(Emulator::getGameEnvironment).thenReturn(f.env);
            emulator.when(Emulator::getConfig).thenReturn(f.config);
            var settings = nativeSettings(new int[0], new int[]{201, 100}, new int[0]);
            settings.setFurniIds(new int[]{20});
            assertTrue(pair.saveData(settings, null));
            String saved = pair.getWiredData();
            var data = com.google.gson.JsonParser.parseString(saved).getAsJsonObject();
            assertEquals(20, data.getAsJsonArray("targetItemIds").get(0).getAsInt());
            assertTrue(data.getAsJsonArray("itemIds").isEmpty());
            var row = mock(ResultSet.class);when(row.getString("wired_data")).thenReturn(saved);
            pair.loadWiredData(row, f.room);assertEquals(saved, pair.getWiredData());
            var sameSelection = nativeSettings(new int[0], new int[]{100, 100}, new int[0]);
            sameSelection.setFurniIds(new int[]{20});assertTrue(pair.saveData(sameSelection, null));
            data = com.google.gson.JsonParser.parseString(pair.getWiredData()).getAsJsonObject();
            assertEquals(20, data.getAsJsonArray("itemIds").get(0).getAsInt());
            assertEquals(20, data.getAsJsonArray("targetItemIds").get(0).getAsInt());
        }
    }

    @Test
    void roomLinkerAndFurniToFurniRejectInvalidTargetsAndRestoreValidSelections() throws Exception {
        var f = new Fixture();
        f.item(10, 1, 1);
        f.item(20, 2, 2);
        var linker = mock(com.eu.habbo.habbohotel.items.interactions.InteractionRoomLinker.class);
        when(f.room.getHabboItem(30)).thenReturn(linker);
        var pair = new WiredEffectFurniToFurni(1, 1, mock(Item.class), "", 0, 0);
        pair.setRoomId(8);
        var teleport = new WiredEffectForwardUserToRoom(2, 1, mock(Item.class), "", 0, 0);
        teleport.setRoomId(8);
        try (var e = mockStatic(Emulator.class)) {
            e.when(Emulator::getGameEnvironment).thenReturn(f.env);
            e.when(Emulator::getConfig).thenReturn(f.config);
            var s = nativeSettings(new int[0], new int[] {100, 101}, new int[0]);
            s.setFurniIds(new int[] {10});
            s.setFurniIds2(new int[] {20});
            assertTrue(pair.saveData(s, null));
            String saved = pair.getWiredData();
            var rs = mock(ResultSet.class);
            when(rs.getString("wired_data")).thenReturn(saved);
            pair.onPickUp();
            pair.loadWiredData(rs, f.room);
            assertEquals(saved, pair.getWiredData());
            var t = nativeSettings(new int[0], new int[] {100}, new int[] {201});
            t.setFurniIds(new int[] {30});
            assertTrue(teleport.saveData(t, null));
            saved = teleport.getWiredData();
            when(rs.getString("wired_data")).thenReturn(saved);
            teleport.onPickUp();
            teleport.loadWiredData(rs, f.room);
            assertEquals(saved, teleport.getWiredData());
            t.setFurniIds(new int[] {10});
            assertThrows(IllegalArgumentException.class, () -> teleport.saveData(t, null));
            assertEquals(saved, teleport.getWiredData());
        }
    }

    @Test
    void malformedMovementDataCannotBreakRoomLoadingAndLegacyGroupDirectionIsRetained() throws Exception {
        var f = new Fixture();
        var rs = mock(ResultSet.class);
        when(rs.getString("wired_data")).thenReturn("{invalid");
        try (var e = mockStatic(Emulator.class)) {
            e.when(Emulator::getGameEnvironment).thenReturn(f.env);
            e.when(Emulator::getConfig).thenReturn(f.config);
            InteractionWiredEffect[] effects = {
                new WiredEffectMoveRotateFurni(1, 1, mock(Item.class), "", 0, 0),
                new WiredEffectMoveFurniTo(2, 1, mock(Item.class), "", 0, 0),
                new WiredEffectChangeFurniDirection(3, 1, mock(Item.class), "", 0, 0),
                new WiredEffectUserToFurni(4, 1, mock(Item.class), "", 0, 0),
                new WiredEffectFurniToUser(5, 1, mock(Item.class), "", 0, 0),
                new WiredEffectFurniToFurni(6, 1, mock(Item.class), "", 0, 0),
                new WiredEffectForwardUserToRoom(7, 1, mock(Item.class), "", 0, 0)
            };
            for (var effect : effects) {
                effect.setRoomId(8);
                assertDoesNotThrow(() -> effect.loadWiredData(rs, f.room));
            }
            var group = new WiredEffectMoveFurniAsGroup(9, 1, mock(Item.class), "", 0, 0);
            group.setRoomId(8);
            when(rs.getString("wired_data"))
                    .thenReturn("{\"direction\":2,\"delay\":1,\"itemIds\":[],\"furniSource\":200}");
            group.loadWiredData(rs, f.room);
            assertTrue(group.getWiredData().contains("\"destinationMode\":false"));
            assertTrue(group.getWiredData().contains("\"direction\":2"));
        }
    }
}
