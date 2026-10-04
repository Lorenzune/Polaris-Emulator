package com.eu.habbo.habbohotel.items.interactions.wired.effects;

import com.eu.habbo.habbohotel.rooms.*;
import com.eu.habbo.habbohotel.users.Habbo;
import com.eu.habbo.messages.outgoing.hotelview.HotelViewComposer;
import org.junit.jupiter.api.Test;
import java.util.ArrayList;
import java.util.List;
import static org.mockito.Mockito.*;
import static org.junit.jupiter.api.Assertions.*;

class RoomLinkerTransferTest {
    private static final class Fixture {
        final Habbo user = mock(Habbo.class, RETURNS_DEEP_STUBS);
        final RoomManager manager = mock(RoomManager.class);
        final Room source = mock(Room.class), target = mock(Room.class);
        final RoomTile tile = mock(RoomTile.class);
        final List<Runnable> timers = new ArrayList<>();
        Fixture() {
            when(target.getId()).thenReturn(42);
            when(user.getHabboInfo().getCurrentRoom()).thenReturn(source);
            doAnswer(call -> { when(user.getHabboInfo().getCurrentRoom()).thenReturn(null); return null; }).when(manager).leaveRoom(user, source, false);
            doAnswer(call -> { when(user.getHabboInfo().getLoadingRoom()).thenReturn(42); return null; }).when(manager).enterRoom(user, 42, "", true, tile, true);
        }
        void start() { RoomLinkerTransfer.start(manager, user, source, target, tile, timers::add); }
    }
    @Test void deniedEntryAlwaysRestoresAVisibleHotelView() {
        var f = new Fixture(); doNothing().when(f.manager).enterRoom(f.user, 42, "", true, f.tile, true);
        f.start();
        verify(f.user.getClient()).sendResponse(isA(HotelViewComposer.class));
        verify(f.user.getHabboInfo()).setLoadingRoom(0);
        assertTrue(f.timers.isEmpty());
    }
    @Test void exceptionAfterLeavingDoesNotStrandTheUser() {
        var f = new Fixture(); doThrow(new IllegalStateException("failed room load")).when(f.manager).enterRoom(f.user, 42, "", true, f.tile, true);
        f.start();
        verify(f.user.getClient()).sendResponse(isA(HotelViewComposer.class));
        assertTrue(f.timers.isEmpty());
    }
    @Test void pendingHandshakeTimeoutClearsThePendingEntryWithoutStartingAnotherOne() {
        var f = new Fixture(); f.start(); assertEquals(1, f.timers.size());
        f.timers.getFirst().run();
        verify(f.user.getClient()).sendResponse(isA(HotelViewComposer.class));
        verify(f.user.getHabboInfo()).setLoadingRoom(0);
        verify(f.manager, times(1)).enterRoom(f.user, 42, "", true, f.tile, true);
    }
    @Test void successfulEntryCannotBeUndoneByItsOldTimer() {
        var f = new Fixture(); f.start(); when(f.user.getHabboInfo().getCurrentRoom()).thenReturn(f.target); when(f.user.getHabboInfo().getLoadingRoom()).thenReturn(0); when(f.user.getRoomUnit().isEntrySnapshotReady()).thenReturn(true);
        f.timers.getFirst().run(); verifyNoInteractions(f.user.getClient());
    }
    @Test void partialSnapshotFailureRemovesThePartialAvatarAndRestoresAVisibleView() {
        var f = new Fixture(); f.start();
        when(f.user.getHabboInfo().getCurrentRoom()).thenReturn(f.target);
        when(f.user.getHabboInfo().getLoadingRoom()).thenReturn(0);
        f.timers.getFirst().run();
        verify(f.manager).leaveRoom(f.user, f.target, false);
        verify(f.user.getHabboInfo()).setCurrentRoom(null);
        verify(f.user.getClient()).sendResponse(isA(HotelViewComposer.class));
    }
    @Test void timerDoesNotInterruptAnotherDestination() {
        var f = new Fixture(); f.start(); when(f.user.getHabboInfo().getLoadingRoom()).thenReturn(99);
        f.timers.getFirst().run(); verifyNoInteractions(f.user.getClient());
    }
    @Test void timerDoesNotInterruptANewerRoomUnitEvenWithTheSameDestination() {
        var f = new Fixture(); f.start(); when(f.user.getRoomUnit()).thenReturn(mock(RoomUnit.class));
        f.timers.getFirst().run(); verifyNoInteractions(f.user.getClient());
    }
    @Test void failureBeforeLeavingKeepsTheSourceRoomVisible() {
        var f = new Fixture(); doThrow(new IllegalStateException("leave failed")).when(f.manager).leaveRoom(f.user, f.source, false);
        f.start(); verifyNoInteractions(f.user.getClient());
        verify(f.manager, never()).enterRoom(any(), anyInt(), anyString(), anyBoolean(), any(RoomTile.class), anyBoolean());
    }
}
