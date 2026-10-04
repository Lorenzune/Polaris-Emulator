package com.eu.habbo.messages.incoming.camera;

import com.eu.habbo.habbohotel.items.Item;
import org.junit.jupiter.api.Test;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class CameraPurchaseItemTest {
    @Test void acceptsLegacySpriteIdWhenBaseItemIdChanged() {
        Item photo = mock(Item.class, RETURNS_DEEP_STUBS);
        when(photo.getSpriteId()).thenReturn(45970);
        when(photo.getInteractionType().getName()).thenReturn("external_image");
        assertSame(photo, CameraPurchaseEvent.resolveCameraItem(null, List.of(photo), 45970));
    }
    @Test void keepsExplicitBaseIdAndRejectsUnrelatedFurniture() {
        Item explicit = mock(Item.class);
        assertSame(explicit, CameraPurchaseEvent.resolveCameraItem(explicit, List.of(), 42));
        Item unrelated = mock(Item.class, RETURNS_DEEP_STUBS);
        when(unrelated.getSpriteId()).thenReturn(45970);
        when(unrelated.getInteractionType().getName()).thenReturn("default");
        assertNull(CameraPurchaseEvent.resolveCameraItem(null, List.of(unrelated), 45970));
    }
}
