package com.eu.habbo.habbohotel.items.interactions;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.eu.habbo.habbohotel.gameclients.GameClient;
import com.eu.habbo.habbohotel.items.Item;
import com.eu.habbo.habbohotel.rooms.Room;
import com.eu.habbo.habbohotel.users.Habbo;
import com.eu.habbo.messages.ServerMessage;
import org.junit.jupiter.api.Test;

class InteractionWiredLabelTest {
    private InteractionWiredLabel item(String data) {
        return new InteractionWiredLabel(12, 7, mock(Item.class), data, 0, 0);
    }

    @Test
    void reloadPreservesPlainTextAndNumericWiredState() {
        InteractionWiredLabel original = item("1");
        String text = "Trigger: sali sul furni\n<test> \"ciao\" 😃";
        original.setLabelText(text);
        original.setCardColor(0xff00aa);
        InteractionWiredLabel loaded = item(original.getDatabaseExtraData());
        assertEquals(text, loaded.getLabelText());
        assertEquals(0xff00aa, loaded.getCardColor());
        assertEquals("1", loaded.getExtradata());
        assertFalse(loaded.consumeWiredStateChange());
        loaded.setExtradata("0");
        assertEquals(text, item(loaded.getDatabaseExtraData()).getLabelText());
        assertEquals("0", item(loaded.getDatabaseExtraData()).getExtradata());
    }

    @Test
    void legacyAndMalformedDataRemainUsable() {
        assertEquals("", item("0").getLabelText());
        assertEquals("0", item("{bad").getExtradata());
        assertFalse(InteractionWiredLabel.validText("x".repeat(2001)));
        assertFalse(InteractionWiredLabel.validText("line\n".repeat(33)));
        assertFalse(InteractionWiredLabel.validText("x\u0000"));
        assertThrows(IllegalArgumentException.class, () -> item("0").setLabelText("x".repeat(2001)));
    }

    @Test
    void textChangesDoNotFireStateChangesAndSerializeAsMap() {
        InteractionWiredLabel label = item("1");
        label.setLabelText("test");
        assertFalse(label.consumeWiredStateChange());
        ServerMessage message = mock(ServerMessage.class);
        label.serializeExtradata(message);
        var ordered = inOrder(message);
        ordered.verify(message).appendInt(1);
        ordered.verify(message).appendInt(3);
        ordered.verify(message).appendString("state");
        ordered.verify(message).appendString("1");
        ordered.verify(message).appendString("text");
        ordered.verify(message).appendString("test");
        ordered.verify(message).appendString("color");
        ordered.verify(message).appendString(Integer.toString(InteractionWiredLabel.DEFAULT_CARD_COLOR));
    }

    @Test
    void useUsesItemSpecificWiredPermissionsAndOnlyChangesVisibility() throws Exception {
        InteractionWiredLabel label = item("0");
        GameClient client = mock(GameClient.class);
        Habbo habbo = mock(Habbo.class);
        Room room = mock(Room.class);
        when(client.getHabbo()).thenReturn(habbo);
        label.onClick(client, room, new Object[] {1});
        assertEquals("0", label.getExtradata());
        when(room.canModifyWired(habbo, label)).thenReturn(true);
        com.eu.habbo.threading.ThreadPooling threading = mock(com.eu.habbo.threading.ThreadPooling.class);
        try (var emulator = org.mockito.Mockito.mockStatic(com.eu.habbo.Emulator.class)) {
            emulator.when(com.eu.habbo.Emulator::getThreading).thenReturn(threading);
            label.setLabelText("Documentazione");
            label.onClick(client, room, new Object[] {1});
            assertEquals("1", label.getExtradata());
            assertEquals("Documentazione", label.getLabelText());
            label.onClick(client, room, new Object[] {0});
            assertEquals("0", label.getExtradata());
        }
        verify(client, never()).sendResponse(any(com.eu.habbo.messages.outgoing.MessageComposer.class));
    }

    @Test
    void olderDocumentationDefaultsToDiscordColor() {
        assertEquals(0x5865f2, item("{\"state\":\"1\",\"text\":\"doc\"}").getCardColor());
        assertFalse(InteractionWiredLabel.validColor(-1));
        assertFalse(InteractionWiredLabel.validColor(0x1000000));
    }
}
