package com.eu.habbo.messages.incoming.wired;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.eu.habbo.Emulator;
import com.eu.habbo.habbohotel.gameclients.GameClient;
import com.eu.habbo.habbohotel.items.Item;
import com.eu.habbo.habbohotel.items.ItemInteraction;
import com.eu.habbo.habbohotel.items.interactions.InteractionWiredLabel;
import com.eu.habbo.habbohotel.rooms.Room;
import com.eu.habbo.habbohotel.users.Habbo;
import com.eu.habbo.habbohotel.users.HabboInfo;
import com.eu.habbo.messages.ClientMessage;
import com.eu.habbo.messages.incoming.MessageHandler;
import com.eu.habbo.threading.ThreadPooling;
import org.junit.jupiter.api.Test;

class WiredLabelCardEventTest {
    private final Room room = mock(Room.class);
    private final Habbo habbo = mock(Habbo.class);
    private final GameClient client = mock(GameClient.class);
    private final InteractionWiredLabel label;

    WiredLabelCardEventTest() {
        HabboInfo info = mock(HabboInfo.class);
        when(client.getHabbo()).thenReturn(habbo);
        when(habbo.getHabboInfo()).thenReturn(info);
        when(info.getCurrentRoom()).thenReturn(room);
        when(room.getId()).thenReturn(259);
        Item base = mock(Item.class);
        when(base.getInteractionType()).thenReturn(new ItemInteraction("wf_label", InteractionWiredLabel.class));
        label = new InteractionWiredLabel(12, 7, base, "1", 0, 0);
        when(room.getHabboItem(12)).thenReturn(label);
    }

    private void packet(MessageHandler handler, int roomId, int color) {
        handler.client = client;
        handler.packet = mock(ClientMessage.class);
        when(handler.packet.bytesAvailable()).thenReturn(20, 4);
        when(handler.packet.readInt()).thenReturn(roomId, 12, color);
        when(handler.packet.readString()).thenReturn("Testo aggiornato");
    }

    @Test
    void editingChecksPermissionsAgainAndNeverOverwritesWiredState() throws Exception {
        SaveWiredLabelCardEvent save = new SaveWiredLabelCardEvent();
        packet(save, 259, 0xff00aa);
        save.handle();
        assertEquals("", label.getLabelText());
        when(room.canModifyWired(habbo, label)).thenReturn(true);
        ThreadPooling pool = mock(ThreadPooling.class);
        try (var emulator = mockStatic(Emulator.class)) {
            emulator.when(Emulator::getThreading).thenReturn(pool);
            packet(save, 259, 0xff00aa);
            save.handle();
            assertEquals("Testo aggiornato", label.getLabelText());
            assertEquals(0xff00aa, label.getCardColor());
            assertEquals("1", label.getExtradata());
            verify(room, never()).updateItemState(label);
            verify(pool).run(label);
        }
    }

    @Test
    void forgedRoomAndInvalidColorDoNotWrite() throws Exception {
        when(room.canModifyWired(habbo, label)).thenReturn(true);
        SaveWiredLabelCardEvent save = new SaveWiredLabelCardEvent();
        packet(save, 260, 0xff00aa);
        save.handle();
        packet(save, 259, -1);
        save.handle();
        assertEquals("", label.getLabelText());
        assertEquals(0x5865f2, label.getCardColor());
    }

    @Test
    void inlineEditorIsOnlyGrantedByItemSpecificWiredPermissions() throws Exception {
        RequestWiredLabelEditorEvent request = new RequestWiredLabelEditorEvent();
        request.client = client;
        request.packet = mock(ClientMessage.class);
        when(request.packet.bytesAvailable()).thenReturn(8);
        when(request.packet.readInt()).thenReturn(259, 12);
        request.handle();
        verify(client, never()).sendResponse(any(com.eu.habbo.messages.outgoing.MessageComposer.class));
        when(room.canModifyWired(habbo, label)).thenReturn(true);
        when(request.packet.readInt()).thenReturn(259, 12);
        request.handle();
        verify(client).sendResponse(any(com.eu.habbo.messages.outgoing.MessageComposer.class));
    }
}
