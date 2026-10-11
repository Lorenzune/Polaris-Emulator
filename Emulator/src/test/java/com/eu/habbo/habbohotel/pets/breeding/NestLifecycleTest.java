package com.eu.habbo.habbohotel.pets.breeding;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.RETURNS_DEEP_STUBS;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.eu.habbo.habbohotel.items.Item;
import com.eu.habbo.habbohotel.items.interactions.pets.InteractionPetBreedingNest;
import com.eu.habbo.habbohotel.pets.Pet;
import com.eu.habbo.habbohotel.rooms.Room;
import com.eu.habbo.threading.ThreadPooling;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import org.junit.jupiter.api.Test;

class NestLifecycleTest {
    @Test
    void wrongSpeciesAndDuplicatePetsAreRejectedAndLeavingFreesTheWaitingPet() throws Exception {
        Room room = mock(Room.class);
        Item item = mock(Item.class);
        when(item.getName()).thenReturn("pet_breeding_dog");
        Pet dog = mock(Pet.class, RETURNS_DEEP_STUBS);
        when(dog.getRoom()).thenReturn(room);
        when(dog.getPetData().getName()).thenReturn("Dog");
        when(dog.getPetData().getOffspringType()).thenReturn(29);
        when(dog.getUserId()).thenReturn(10);
        Pet cat = mock(Pet.class, RETURNS_DEEP_STUBS);
        when(cat.getRoom()).thenReturn(room);
        when(cat.getPetData().getName()).thenReturn("Cat");
        when(cat.getPetData().getOffspringType()).thenReturn(28);
        ThreadPooling threading = mock(ThreadPooling.class);
        var scheduler = mock(ScheduledExecutorService.class);
        when(threading.getService()).thenReturn(scheduler);
        var future = mock(ScheduledFuture.class);
        doReturn(future).when(scheduler).schedule(any(Runnable.class), eq(60L), any());
        Class.forName("com.eu.habbo.Emulator");
        var previous = PetBreedingPlatform.services();
        PetBreedingPlatform.install(new PetBreedingPlatform.Services(null, null, null, () -> threading, null));
        try {
            var nest = new InteractionPetBreedingNest(99, 10, item, "0", 0, 0);
            when(room.getHabboItem(99)).thenReturn(nest);
            assertFalse(nest.addPet(cat));
            assertTrue(nest.addPet(dog), "database species name is capitalized");
            assertFalse(nest.addPet(dog));
            verify(dog.getRoomUnit()).setCanWalk(false);
            nest.onOwnerLeave(room, 20);
            assertSame(dog, nest.petOne);
            nest.onOwnerLeave(room, 10);
            assertNull(nest.petOne);
            verify(dog.getRoomUnit()).setCanWalk(true);
            verify(future).cancel(false);
        } finally {
            PetBreedingPlatform.install(previous);
        }
    }
}
