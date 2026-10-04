package com.eu.habbo.habbohotel.items.interactions.wired.effects;

import com.eu.habbo.Emulator;
import com.eu.habbo.habbohotel.GameEnvironment;
import com.eu.habbo.habbohotel.items.Item;
import com.eu.habbo.habbohotel.items.ItemManager;
import com.eu.habbo.habbohotel.items.interactions.InteractionRoomLinker;
import com.eu.habbo.habbohotel.items.interactions.InteractionTeleport;
import com.eu.habbo.habbohotel.items.interactions.wired.WiredSettings;
import com.eu.habbo.habbohotel.rooms.*;
import com.eu.habbo.habbohotel.users.Habbo;
import com.eu.habbo.habbohotel.wired.core.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import java.util.List;

class WiredEffectForwardUserToRoomTest {
    @Test void acceptsExactlyOneSelectedEndpointAndIgnoresTheOldRoomId() {
        var effect = new WiredEffectForwardUserToRoom(1, 2, mock(Item.class), "", 0, 0);
        assertFalse(effect.saveData(new WiredSettings(new int[]{0}, "42", new int[]{}, 0), null));
        assertFalse(effect.saveData(new WiredSettings(new int[]{0}, "42", new int[]{19, 20}, 0), null));
        assertTrue(effect.saveData(new WiredSettings(new int[]{0}, "42", new int[]{19}, 0), null));
        assertFalse(effect.getWiredData().contains("roomIdText"));
        assertTrue(InteractionTeleport.class.isAssignableFrom(InteractionRoomLinker.class));
    }
    @Test void sameRoomMovesTheExistingAvatarRatherThanCreatingAnotherEntry() { transfer(0, false, true, true, true); }
    @Test void missingOrPickedUpPartnerDoesNotRemoveTheUser() { transfer(0, false, false, false, false); }
    @Test void teleportStartsOneCoordinateEntryWithoutLeavingTheAvatarInTeleportAnimation() { transfer(0, false, true, false, true); }
    @Test void pendingEntryDoesNotStartAnotherTeleport() { transfer(42, false, false, false, true); }
    @Test void incompleteDestinationNeverRemovesTheUserFromTheSource() { transfer(-2, false, false, false, true); }
    @Test void activeFurnitureTeleportIsNotInterrupted() { transfer(-1, false, false, false, true); }
    @Test void staleStackCannotTransferAUserWhoHasAlreadyLeft() { transfer(0, true, false, false, true); }
    private void transfer(int loadingRoom, boolean stale, boolean shouldTransfer, boolean sameRoom, boolean partnerPlaced) {
        var effect = new WiredEffectForwardUserToRoom(1, 2, mock(Item.class), "", 0, 0);
        assertTrue(effect.saveData(new WiredSettings(new int[]{0}, "", new int[]{19}, 0), null));
        var ctx = mock(WiredContext.class); var source = mock(Room.class); when(source.getId()).thenReturn(7); when(ctx.room()).thenReturn(source);
        var user = mock(Habbo.class, RETURNS_DEEP_STUBS); var unit = mock(RoomUnit.class);
        when(source.getHabbo(unit)).thenReturn(user); when(user.getHabboInfo().getId()).thenReturn(3);
        when(user.getHabboInfo().getCurrentRoom()).thenReturn(stale ? mock(Room.class) : source);
        when(user.getHabboInfo().getLoadingRoom()).thenReturn(Math.max(0, loadingRoom));
        user.getRoomUnit().isTeleporting = loadingRoom == -1;
        var environment = mock(GameEnvironment.class); var manager = mock(RoomManager.class);
        when(environment.getRoomManager()).thenReturn(manager);
        var items = mock(ItemManager.class); when(environment.getItemManager()).thenReturn(items);
        var linker = mock(InteractionRoomLinker.class); when(source.getHabboItem(19)).thenReturn(linker);
        when(items.getTargetTeleportRoomId(linker)).thenReturn(partnerPlaced ? new int[]{sameRoom ? 7 : 42, 20} : new int[]{0, 20});
        var target = sameRoom ? source : mock(Room.class); when(manager.loadRoom(42, true)).thenReturn(target); when(target.getId()).thenReturn(sameRoom ? 7 : 42); when(target.isLoaded()).thenReturn(loadingRoom != -2);
        var destination = mock(InteractionRoomLinker.class); when(target.getHabboItem(20)).thenReturn(destination);
        when(destination.getX()).thenReturn((short)3); when(destination.getY()).thenReturn((short)4);
        var layout = mock(RoomLayout.class); when(target.getLayout()).thenReturn(layout);
        var tile = mock(RoomTile.class); when(layout.getTile((short)3, (short)4)).thenReturn(tile);
        doAnswer(call -> { when(user.getHabboInfo().getCurrentRoom()).thenReturn(null); return null; }).when(manager).leaveRoom(user, source, false);
        doAnswer(call -> { when(user.getHabboInfo().getLoadingRoom()).thenReturn(42); return null; }).when(manager).enterRoom(user, 42, "", true, tile, true);
        try (var emulator = mockStatic(Emulator.class); var sources = mockStatic(WiredSourceUtil.class)) {
            emulator.when(Emulator::getGameEnvironment).thenReturn(environment);
            emulator.when(Emulator::getThreading).thenReturn(mock(com.eu.habbo.threading.ThreadPooling.class));
            sources.when(() -> WiredSourceUtil.resolveUsers(ctx, 0)).thenReturn(List.of(unit, unit));
            effect.execute(ctx);
            if (shouldTransfer && sameRoom) {
                verify(source).teleportHabboToItem(user, destination);
                verify(manager, never()).enterRoom(any(), anyInt(), anyString(), anyBoolean(), any(RoomTile.class), anyBoolean());
            } else if (shouldTransfer) {
                var order = inOrder(manager);
                order.verify(manager).leaveRoom(user, source, false);
                order.verify(manager).enterRoom(user, 42, "", true, tile, true);
            } else {
                verify(manager, never()).leaveRoom(any(), any(), anyBoolean());
                verify(manager, never()).enterRoom(any(), anyInt(), anyString(), anyBoolean(), any(RoomTile.class), anyBoolean());
            }
            verifyNoInteractions(user.getClient());
        }
    }
}
