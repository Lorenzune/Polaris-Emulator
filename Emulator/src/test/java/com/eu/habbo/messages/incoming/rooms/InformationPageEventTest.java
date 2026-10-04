package com.eu.habbo.messages.incoming.rooms;
import com.eu.habbo.habbohotel.gameclients.GameClient;
import com.eu.habbo.habbohotel.items.InformationPageRepository;
import com.eu.habbo.habbohotel.items.ItemManager;
import com.eu.habbo.habbohotel.items.interactions.InteractionInformationTerminal;
import com.eu.habbo.habbohotel.rooms.Room;
import com.eu.habbo.messages.ClientMessage;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import static org.mockito.Mockito.*;

class InformationPageEventTest {
    private InformationPageEvent handler(boolean permission, boolean owner, boolean rights) {
        var handler = new InformationPageEvent();
        handler.client = mock(GameClient.class, RETURNS_DEEP_STUBS);
        handler.packet = mock(ClientMessage.class);
        when(handler.packet.readInt()).thenReturn(45, 1);
        var room = mock(Room.class);
        when(handler.client.getHabbo().getHabboInfo().getCurrentRoom()).thenReturn(room);
        when(handler.client.getHabbo().getHabboInfo().getId()).thenReturn(7);
        when(handler.client.getHabbo().hasPermission("acc_information_terminal_edit")).thenReturn(permission);
        when(room.hasRights(handler.client.getHabbo())).thenReturn(rights);
        var terminal = mock(InteractionInformationTerminal.class);
        when(terminal.getUserId()).thenReturn(owner ? 7 : 8);
        when(room.getHabboItem(45)).thenReturn(terminal);
        return handler;
    }
    @Test void rejectsWritesWithoutPermissionEvenForOwner() throws Exception {
        var event = handler(false, true, true);
        try (MockedStatic<InformationPageRepository> repository = mockStatic(InformationPageRepository.class)) {
            event.handle(); repository.verifyNoInteractions(); verify(event.packet, never()).readString();
        }
    }
    @Test void allowsPermissionWithoutOwnershipOrRoomRights() throws Exception {
        var event = handler(true, false, false);
        when(event.packet.readString()).thenReturn("Titolo", "<p>Testo</p>");
        try (MockedStatic<InformationPageRepository> repository = mockStatic(InformationPageRepository.class)) {
            repository.when(() -> InformationPageRepository.clean("<p>Testo</p>")).thenReturn("<p>Testo</p>");
            var pages = mock(InformationPageRepository.class);
            when(pages.find(45)).thenReturn(new InformationPageRepository.Page("Titolo", "<p>Testo</p>"));
            try (MockedStatic<ItemManager> items = mockStatic(ItemManager.class)) {
                items.when(ItemManager::informationPages).thenReturn(pages);
                event.handle();
                verify(pages).save(45, "Titolo", "<p>Testo</p>", 7);
            }
        }
    }
    @Test void rejectsItemsOutsideCurrentRoom() throws Exception {
        var event = handler(true, true, true);
        when(event.client.getHabbo().getHabboInfo().getCurrentRoom().getHabboItem(45)).thenReturn(null);
        try (MockedStatic<InformationPageRepository> repository = mockStatic(InformationPageRepository.class)) {
            event.handle(); repository.verifyNoInteractions();
        }
    }
}
