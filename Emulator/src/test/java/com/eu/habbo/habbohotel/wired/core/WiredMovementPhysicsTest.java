package com.eu.habbo.habbohotel.wired.core;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.eu.habbo.habbohotel.users.HabboItem;
import java.util.Set;
import org.junit.jupiter.api.Test;

class WiredMovementPhysicsTest {
    @Test
    void excludesMovingFurniWithoutChangingSharedPhysicsSets() {
        HabboItem moving = mock(HabboItem.class);
        HabboItem other = mock(HabboItem.class);
        HabboItem blocking = mock(HabboItem.class);
        when(moving.getId()).thenReturn(11);
        when(other.getId()).thenReturn(12);
        when(blocking.getId()).thenReturn(13);
        WiredMovementPhysics physics = new WiredMovementPhysics(false, Set.of(11, 12), Set.of(), Set.of(11, 13));

        WiredMovementPhysics forMoving = physics.withoutFurni(11);

        assertFalse(forMoving.shouldIgnoreFurni(moving));
        assertFalse(forMoving.isBlockingFurni(moving));
        assertTrue(forMoving.shouldIgnoreFurni(other));
        assertTrue(forMoving.hasBlockingFurni());
        assertTrue(forMoving.isBlockingFurni(blocking));
        assertFalse(physics.shouldIgnoreFurni(moving));
        assertTrue(physics.isBlockingFurni(moving));
    }

    @Test
    void physicsContainingOnlyTheMovingFurniBecomesInactiveAfterExclusion() {
        HabboItem moving = mock(HabboItem.class);
        when(moving.getId()).thenReturn(21);
        WiredMovementPhysics physics = new WiredMovementPhysics(false, Set.of(21), Set.of(), Set.of(21));

        WiredMovementPhysics forMoving = physics.withoutFurni(21);

        assertFalse(forMoving.isActive());
        assertFalse(forMoving.hasBlockingFurni());
    }
}
