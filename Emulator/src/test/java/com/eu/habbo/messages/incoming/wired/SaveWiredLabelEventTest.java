package com.eu.habbo.messages.incoming.wired;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.eu.habbo.WiredPlatform;
import com.eu.habbo.habbohotel.gameclients.GameClient;
import com.eu.habbo.habbohotel.items.Item;
import com.eu.habbo.habbohotel.items.ItemInteraction;
import com.eu.habbo.habbohotel.items.interactions.InteractionWiredLabel;
import com.eu.habbo.habbohotel.rooms.Room;
import com.eu.habbo.habbohotel.users.Habbo;
import com.eu.habbo.habbohotel.users.HabboInfo;
import com.eu.habbo.messages.ClientMessage;
import com.eu.habbo.threading.ThreadPooling;
import org.junit.jupiter.api.Test;

class SaveWiredLabelEventTest {
    @Test
    void authorizedSaveBroadcastsDocumentationAndSchedulesPersistence() throws Exception {
        SaveWiredLabelEvent event = new SaveWiredLabelEvent();
        event.client = mock(GameClient.class);
        Habbo habbo = mock(Habbo.class);
        HabboInfo info = mock(HabboInfo.class);
        Room room = mock(Room.class);
        when(event.client.getHabbo()).thenReturn(habbo);
        when(habbo.getHabboInfo()).thenReturn(info);
        when(info.getCurrentRoom()).thenReturn(room);
        when(room.getId()).thenReturn(259);
        Item baseItem = mock(Item.class);
        when(baseItem.getInteractionType()).thenReturn(new ItemInteraction("wf_label", InteractionWiredLabel.class));
        InteractionWiredLabel label = new InteractionWiredLabel(12, 7, baseItem, "0", 0, 0);
        when(room.getHabboItem(12)).thenReturn(label);
        when(room.canModifyWired(habbo, label)).thenReturn(true);
        event.packet = mock(ClientMessage.class);
        when(event.packet.bytesAvailable()).thenReturn(20, 4);
        when(event.packet.readInt()).thenReturn(259, 12, 1);
        when(event.packet.readString()).thenReturn("Trigger: sali sul furni");
        ThreadPooling threading = mock(ThreadPooling.class);
        try (var emulator = mockStatic(WiredPlatform.class)) {
            emulator.when(WiredPlatform::threading).thenReturn(threading);
            event.handle();
            assertEquals("Trigger: sali sul furni", label.getLabelText());
            assertEquals("1", label.getExtradata());
            verify(room).updateItemState(label);
            verify(room).sendComposer(any(com.eu.habbo.messages.ServerMessage.class));
            verify(threading).run(label);
        }
    }

    @Test
    void forgedRoomPermissionsStateAndOversizeTextCannotMutate() throws Exception {
        for (int scenario = 0; scenario < 4; scenario++) {
            SaveWiredLabelEvent event = new SaveWiredLabelEvent();
            event.client = mock(GameClient.class);
            Habbo habbo = mock(Habbo.class);
            HabboInfo info = mock(HabboInfo.class);
            Room room = mock(Room.class);
            when(event.client.getHabbo()).thenReturn(habbo);
            when(habbo.getHabboInfo()).thenReturn(info);
            when(info.getCurrentRoom()).thenReturn(room);
            when(room.getId()).thenReturn(259);
            InteractionWiredLabel label = new InteractionWiredLabel(12, 7, mock(Item.class), "0", 0, 0);
            when(room.getHabboItem(12)).thenReturn(label);
            when(room.canModifyWired(habbo, label)).thenReturn(scenario != 1);
            event.packet = mock(ClientMessage.class);
            when(event.packet.bytesAvailable()).thenReturn(20, 4);
            when(event.packet.readInt()).thenReturn(scenario == 0 ? 260 : 259, 12, scenario == 2 ? 2 : 1);
            when(event.packet.readString()).thenReturn(scenario == 3 ? "x".repeat(2001) : "changed");
            event.handle();
            assertEquals("", label.getLabelText());
            assertEquals("0", label.getExtradata());
            verify(room, never()).sendComposer(any(com.eu.habbo.messages.ServerMessage.class));
        }
    }
}
