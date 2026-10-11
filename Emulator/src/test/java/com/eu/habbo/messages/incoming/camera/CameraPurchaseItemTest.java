package com.eu.habbo.messages.incoming.camera;

import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.Mockito.RETURNS_DEEP_STUBS;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.eu.habbo.habbohotel.items.Item;
import java.util.List;
import org.junit.jupiter.api.Test;

class CameraPurchaseItemTest {
    @Test
    void acceptsLegacySpriteIdWhenBaseItemIdChanged() {
        Item photo = mock(Item.class, RETURNS_DEEP_STUBS);
        when(photo.getSpriteId()).thenReturn(45970);
        when(photo.getInteractionType().getName()).thenReturn("external_image");
        assertSame(photo, CameraPurchaseEvent.resolveCameraItem(null, List.of(photo), 45970));
    }

    @Test
    void keepsExplicitBaseIdAndRejectsUnrelatedFurniture() {
        Item explicit = mock(Item.class);
        assertSame(explicit, CameraPurchaseEvent.resolveCameraItem(explicit, List.of(), 42));
        Item unrelated = mock(Item.class, RETURNS_DEEP_STUBS);
        when(unrelated.getSpriteId()).thenReturn(45970);
        when(unrelated.getInteractionType().getName()).thenReturn("default");
        assertNull(CameraPurchaseEvent.resolveCameraItem(null, List.of(unrelated), 45970));
    }
}
