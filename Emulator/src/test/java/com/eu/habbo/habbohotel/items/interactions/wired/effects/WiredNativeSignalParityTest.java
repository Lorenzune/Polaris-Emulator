package com.eu.habbo.habbohotel.items.interactions.wired.effects;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.RETURNS_DEEP_STUBS;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

import com.eu.habbo.Emulator;
import com.eu.habbo.core.ConfigurationManager;
import com.eu.habbo.habbohotel.GameEnvironment;
import com.eu.habbo.habbohotel.items.Item;
import com.eu.habbo.habbohotel.items.interactions.wired.WiredSettings;
import com.eu.habbo.habbohotel.items.interactions.wired.triggers.WiredTriggerReceiveSignal;
import com.eu.habbo.habbohotel.rooms.Room;
import com.eu.habbo.habbohotel.rooms.RoomTile;
import com.eu.habbo.habbohotel.rooms.RoomTileState;
import com.eu.habbo.habbohotel.rooms.RoomUnit;
import com.eu.habbo.habbohotel.users.HabboItem;
import com.eu.habbo.habbohotel.wired.core.WiredContext;
import com.eu.habbo.habbohotel.wired.core.WiredEvent;
import com.eu.habbo.habbohotel.wired.core.WiredManager;
import com.eu.habbo.habbohotel.wired.core.WiredServices;
import com.eu.habbo.habbohotel.wired.core.WiredSourceUtil;
import com.eu.habbo.habbohotel.wired.core.WiredState;
import com.eu.habbo.messages.incoming.wired.WiredSaveException;
import java.sql.ResultSet;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class WiredNativeSignalParityTest {
    static class Sender extends WiredEffectSendSignal {
        final List<WiredEvent> sent = new ArrayList<>();

        Sender() {
            super(50, 2, mock(Item.class), "", 0, 0);
            setRoomId(8);
        }

        @Override
        protected boolean dispatchSignalEvent(WiredEvent event) {
            sent.add(event);
            return true;
        }
    }

    static HabboItem item(int id, boolean antenna) {
        var item = mock(HabboItem.class, RETURNS_DEEP_STUBS);
        when(item.getId()).thenReturn(id);
        when(item.getRoomId()).thenReturn(8);
        when(item.getBaseItem().getInteractionType().getName()).thenReturn(antenna ? "antenna" : "default");
        return item;
    }

    static RoomUnit unit(int id) {
        var unit = mock(RoomUnit.class);
        when(unit.getId()).thenReturn(id);
        return unit;
    }

    static WiredSettings settings(
            int furniSplit, int userSplit, int antennaSource, int itemSource, int userSource, int[] forwarded) {
        var settings = new WiredSettings(new int[] {furniSplit, userSplit}, "", new int[] {10}, 0, 2);
        settings.setNativeVariableFormat(true);
        settings.setFurniIds2(forwarded);
        settings.setFurniSourceTypes(new int[] {antennaSource, itemSource});
        settings.setUserSourceTypes(new int[] {userSource});
        return settings;
    }

    @Test
    void splitUsesOnlyForwardedUsersAndIndependentContextCopiesAfterReload() throws Exception {
        var room = mock(Room.class, RETURNS_DEEP_STUBS);
        var environment = mock(GameEnvironment.class, RETURNS_DEEP_STUBS);
        var config = mock(ConfigurationManager.class);
        when(environment.getRoomManager().getRoom(8)).thenReturn(room);
        when(config.getInt("hotel.wired.furni.selection.count")).thenReturn(20);
        when(config.getInt("hotel.wired.max_delay", 20)).thenReturn(20);
        var antenna = item(10, true);
        var a = item(11, false);
        var b = item(12, false);
        var items = Map.of(10, antenna, 11, a, 12, b);
        when(room.getHabboItem(org.mockito.ArgumentMatchers.anyInt())).thenAnswer(inv -> items.get(inv.getArgument(0)));
        when(room.getLayout().getTile((short) 0, (short) 0))
                .thenReturn(new RoomTile((short) 0, (short) 0, (short) 0, RoomTileState.OPEN, true));
        var origin = unit(1);
        var u = unit(2);
        var v = unit(3);
        try (var emulator = mockStatic(Emulator.class)) {
            emulator.when(Emulator::getGameEnvironment).thenReturn(environment);
            emulator.when(Emulator::getConfig).thenReturn(config);
            var sender = new Sender();
            sender.saveData(settings(1, 1, 100, 101, 200, new int[] {11, 12}), null);
            var rows = mock(ResultSet.class);
            var saved = sender.getWiredData();
            when(rows.getString("wired_data")).thenReturn(saved);
            sender = new Sender();
            sender.loadWiredData(rows, room);
            assertEquals(2, sender.getDelay());
            var ctx = new WiredContext(
                    WiredEvent.builder(WiredEvent.Type.USER_SAYS, room)
                            .actor(origin)
                            .build(),
                    null,
                    mock(WiredServices.class),
                    new WiredState(100));
            ctx.targets().setUsers(List.of(u, v));
            ctx.contextVariables().assignValue(9, 7, true);
            sender.execute(ctx);
            assertEquals(4, sender.sent.size());
            for (var event : sender.sent) {
                assertTrue(event.getActor().orElseThrow() != origin);
                assertEquals(origin, event.getOriginActor().orElseThrow());
                assertEquals(1, event.getSignalUserCount());
                assertEquals(1, event.getSignalFurniCount());
                var child = new WiredContext(event, null, mock(WiredServices.class), new WiredState(100));
                assertEquals(List.of(event.getActor().orElseThrow()), WiredSourceUtil.resolveUsersRaw(child, 201));
                assertEquals(
                        List.of(event.getSourceItem().orElseThrow()),
                        WiredSourceUtil.resolveItemsRaw(child, 201, List.of()));
                assertEquals(7, event.getContextVariableScope().getValue(9));
            }
            sender.sent.getFirst().getContextVariableScope().assignValue(9, 99, true);
            assertEquals(7, sender.sent.get(1).getContextVariableScope().getValue(9));
            assertEquals(7, ctx.contextVariables().getValue(9));
            WiredEvent relayInput = sender.sent.get(1);
            sender.sent.clear();
            sender.saveData(settings(0, 0, 100, 101, 11, new int[] {11}), null);
            var clicked = new WiredContext(
                    WiredEvent.builder(WiredEvent.Type.USER_CLICKS_USER, room)
                            .actor(origin)
                            .targetUnit(u)
                            .build(),
                    null,
                    mock(WiredServices.class),
                    new WiredState(100));
            sender.execute(clicked);
            assertEquals(List.of(u), sender.sent.getFirst().getSignalSourceUsers());
            sender.sent.clear();
            sender.saveData(settings(0, 0, 200, 201, 201, new int[0]), null);
            String relaySaved = sender.getWiredData();
            when(rows.getString("wired_data")).thenReturn(relaySaved);
            sender.loadWiredData(rows, room);
            var relay = new WiredContext(relayInput, null, mock(WiredServices.class), new WiredState(100));
            relay.targets().setItems(List.of(antenna, a));
            sender.execute(relay);
            assertEquals(1, sender.sent.size());
            assertEquals(
                    relayInput.getSignalSourceItems(), sender.sent.getFirst().getSignalSourceItems());
            assertEquals(
                    relayInput.getSignalSourceUsers(), sender.sent.getFirst().getSignalSourceUsers());
            assertEquals(7, sender.sent.getFirst().getContextVariableScope().getValue(9));
        }
    }

    @Test
    void emptyExplicitPayloadDoesNotInventTheTriggeringUserAndEmptySplitEmitsNothing() throws Exception {
        var room = mock(Room.class, RETURNS_DEEP_STUBS);
        var environment = mock(GameEnvironment.class, RETURNS_DEEP_STUBS);
        var config = mock(ConfigurationManager.class);
        when(environment.getRoomManager().getRoom(8)).thenReturn(room);
        when(config.getInt("hotel.wired.furni.selection.count")).thenReturn(20);
        when(config.getInt("hotel.wired.max_delay", 20)).thenReturn(20);
        var antenna = item(10, true);
        when(room.getHabboItem(10)).thenReturn(antenna);
        when(room.getLayout().getTile((short) 0, (short) 0))
                .thenReturn(new RoomTile((short) 0, (short) 0, (short) 0, RoomTileState.OPEN, true));
        var ctx = new WiredContext(
                WiredEvent.builder(WiredEvent.Type.USER_SAYS, room)
                        .actor(unit(1))
                        .build(),
                null,
                mock(WiredServices.class),
                new WiredState(100));
        ctx.targets().setUsers(List.of());
        try (var emulator = mockStatic(Emulator.class)) {
            emulator.when(Emulator::getGameEnvironment).thenReturn(environment);
            emulator.when(Emulator::getConfig).thenReturn(config);
            var sender = new Sender();
            sender.saveData(settings(0, 0, 100, 101, 200, new int[0]), null);
            sender.execute(ctx);
            assertEquals(1, sender.sent.size());
            var event = sender.sent.getFirst();
            var child = new WiredContext(event, null, mock(WiredServices.class), new WiredState(100));
            assertEquals(List.of(), WiredSourceUtil.resolveUsersRaw(child, 201));
            assertEquals(0, event.getSignalUserCount());
            assertEquals(List.of(), WiredSourceUtil.resolveItemsRaw(child, 201, List.of()));
            sender.sent.clear();
            sender.saveData(settings(0, 1, 100, 101, 200, new int[0]), null);
            sender.execute(ctx);
            assertTrue(sender.sent.isEmpty());
            assertThrows(
                    WiredSaveException.class, () -> sender.saveData(settings(0, 0, 100, 100, 200, new int[0]), null));
        }
    }

    @Test
    void aggregatedSignalKeepsClickedSourceSeparateFromForwardedBoard() throws Exception {
        var room = mock(Room.class, RETURNS_DEEP_STUBS);
        var environment = mock(GameEnvironment.class, RETURNS_DEEP_STUBS);
        var config = mock(ConfigurationManager.class);
        when(environment.getRoomManager().getRoom(8)).thenReturn(room);
        when(config.getInt("hotel.wired.furni.selection.count")).thenReturn(20);
        when(config.getInt("hotel.wired.max_delay", 20)).thenReturn(20);
        var antenna = item(10, true);
        var first = item(11, false);
        var clicked = item(12, false);
        var items = Map.of(10, antenna, 11, first, 12, clicked);
        when(room.getHabboItem(org.mockito.ArgumentMatchers.anyInt())).thenAnswer(i -> items.get(i.getArgument(0)));
        when(room.getLayout().getTile((short) 0, (short) 0))
                .thenReturn(new RoomTile((short) 0, (short) 0, (short) 0, RoomTileState.OPEN, true));
        try (var emulator = mockStatic(Emulator.class)) {
            emulator.when(Emulator::getGameEnvironment).thenReturn(environment);
            emulator.when(Emulator::getConfig).thenReturn(config);
            var sender = new Sender();
            sender.saveData(settings(0, 0, 100, 101, 200, new int[] {11, 12}), null);
            var ctx = new WiredContext(
                    WiredEvent.builder(WiredEvent.Type.USER_CLICKS_FURNI, room)
                            .sourceItem(clicked)
                            .build(),
                    null,
                    mock(WiredServices.class),
                    new WiredState(100));
            sender.execute(ctx);
            var event = sender.sent.getFirst();
            var receiver = new WiredContext(event, null, mock(WiredServices.class), new WiredState(100));
            assertEquals(List.of(clicked), WiredSourceUtil.resolveItemsRaw(receiver, 0, List.of()));
            assertEquals(List.of(first, clicked), WiredSourceUtil.resolveItemsRaw(receiver, 201, List.of()));
        }
    }

    @Test
    void longNativeRelayStillAllowsFinalCleanupCall() throws Exception {
        var room = mock(Room.class, RETURNS_DEEP_STUBS);
        var environment = mock(GameEnvironment.class, RETURNS_DEEP_STUBS);
        var config = mock(ConfigurationManager.class);
        when(environment.getRoomManager().getRoom(8)).thenReturn(room);
        when(config.getInt("hotel.wired.furni.selection.count")).thenReturn(20);
        when(config.getInt("hotel.wired.max_delay", 20)).thenReturn(20);
        var antenna = item(10, true);
        var piece = item(11, false);
        when(room.getHabboItem(10)).thenReturn(antenna);
        when(room.getHabboItem(11)).thenReturn(piece);
        var tile = new RoomTile((short) 0, (short) 0, (short) 0, RoomTileState.OPEN, true);
        when(room.getLayout().getTile((short) 0, (short) 0)).thenReturn(tile);
        try (var emulator = mockStatic(Emulator.class)) {
            emulator.when(Emulator::getGameEnvironment).thenReturn(environment);
            emulator.when(Emulator::getConfig).thenReturn(config);
            var sender = new Sender();
            sender.saveData(settings(0, 0, 100, 101, 200, new int[] {11}), null);
            var event = WiredEvent.builder(WiredEvent.Type.USER_CLICKS_FURNI, room)
                    .sourceItem(piece)
                    .callStackDepth(9)
                    .build();
            for (int relay = 0; relay < 24; relay++) {
                var context = new WiredContext(event, null, mock(WiredServices.class), new WiredState(100));
                context.contextVariables().assignValue(9, 42, true);
                sender.sent.clear();
                sender.execute(context);
                assertEquals(1, sender.sent.size());
                event = sender.sent.getFirst();
                assertEquals(0, event.getCallStackDepth());
                assertEquals(List.of(piece), event.getSignalSourceItems());
                assertEquals(42, event.getContextVariableScope().getValue(9));
            }
            var cleanup = new WiredEffectTriggerStacks(22, 2, mock(Item.class), "", 0, 0);
            cleanup.items.add(antenna);
            cleanup.furniSource = WiredSourceUtil.SOURCE_SELECTED;
            var context = new WiredContext(event, null, mock(WiredServices.class), new WiredState(100));
            try (var manager = mockStatic(WiredManager.class)) {
                cleanup.execute(context);
                manager.verify(() -> WiredManager.executeEffectsAtTiles(
                        org.mockito.ArgumentMatchers.eq(java.util.Set.of(tile)),
                        org.mockito.ArgumentMatchers.isNull(),
                        org.mockito.ArgumentMatchers.eq(room),
                        org.mockito.ArgumentMatchers.eq(1),
                        org.mockito.ArgumentMatchers.eq(context)));
            }
        }
    }

    @Test
    void negativeSenderDoesNotInvertReceiverConditions() {
        try (var manager = mockStatic(WiredManager.class)) {
            var event = WiredEvent.builder(WiredEvent.Type.SIGNAL_RECEIVED, mock(Room.class))
                    .build();
            var sender = new WiredEffectNegativeSendSignal(1, 2, mock(Item.class), "", 0, 0);
            sender.dispatchSignalEvent(event);
            manager.verify(() -> WiredManager.dispatchEffectTriggeredEvent(event));
            manager.verifyNoMoreInteractions();
        }
    }

    @Test
    void receiveNativeHasNoChannelFieldAndDoesNotFallBackToStaleChannel() throws Exception {
        var room = mock(Room.class, RETURNS_DEEP_STUBS);
        var environment = mock(GameEnvironment.class, RETURNS_DEEP_STUBS);
        var config = mock(ConfigurationManager.class);
        when(environment.getRoomManager().getRoom(8)).thenReturn(room);
        when(config.getInt("hotel.wired.furni.selection.count")).thenReturn(20);
        var antenna = item(10, true);
        when(room.getHabboItem(10)).thenReturn(antenna);
        try (var emulator = mockStatic(Emulator.class)) {
            emulator.when(Emulator::getGameEnvironment).thenReturn(environment);
            emulator.when(Emulator::getConfig).thenReturn(config);
            var receiver = new WiredTriggerReceiveSignal(1, 2, mock(Item.class), "", 0, 0);
            receiver.setRoomId(8);
            var settings = new WiredSettings(new int[0], "", new int[] {10}, 0);
            settings.setNativeVariableFormat(true);
            settings.setFurniSourceTypes(new int[] {100});
            receiver.saveData(settings, null);
            var rows = mock(ResultSet.class);
            var saved = receiver.getWiredData();
            when(rows.getString("wired_data")).thenReturn(saved);
            receiver = new WiredTriggerReceiveSignal(1, 2, mock(Item.class), "", 0, 0);
            receiver.setRoomId(8);
            receiver.loadWiredData(rows, room);
            var event = WiredEvent.builder(WiredEvent.Type.SIGNAL_RECEIVED, room)
                    .signalChannel(10)
                    .build();
            assertTrue(receiver.matches(null, event));
            when(room.getHabboItem(10)).thenReturn(null);
            receiver.unlinkAntenna(10);
            assertEquals(false, receiver.matches(null, event));
        }
    }
}
