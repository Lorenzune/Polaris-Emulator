package com.eu.habbo.messages.incoming.rooms.pets;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyShort;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.RETURNS_DEEP_STUBS;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.eu.habbo.Emulator;
import com.eu.habbo.core.ConfigurationManager;
import com.eu.habbo.habbohotel.GameEnvironment;
import com.eu.habbo.habbohotel.gameclients.GameClient;
import com.eu.habbo.habbohotel.pets.Pet;
import com.eu.habbo.habbohotel.pets.PetManager;
import com.eu.habbo.habbohotel.rooms.Room;
import com.eu.habbo.habbohotel.rooms.RoomTile;
import com.eu.habbo.habbohotel.rooms.RoomTileState;
import com.eu.habbo.habbohotel.users.HabboItem;
import com.eu.habbo.messages.ClientMessage;
import com.eu.habbo.messages.incoming.catalog.CheckPetNameEvent;
import com.eu.habbo.threading.ThreadPooling;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class PetPackageOpeningTest {
    @ParameterizedTest
    @CsvSource({"newbie_cat_package,1", "newbie_crocodile_package,2", "newbie_present_2014,0"})
    void opensTheCorrectPetAndCannotConsumeTheSameRoomItemTwice(String name, int type) throws Exception {
        exercise(name, type, 42, "Buddy", true);
    }

    @Test
    void anotherUserCannotConsumeThePackage() throws Exception {
        exercise("newbie_cat_package", 1, 99, "Buddy", false);
    }

    @Test
    void anInvalidNameDoesNotConsumeThePackage() throws Exception {
        exercise("newbie_present_2014", 0, 42, "a!", false);
    }

    private void exercise(String itemName, int petType, int itemOwner, String petName, boolean succeeds)
            throws Exception {
        var event = new PetPackageNameEvent();
        event.client = mock(GameClient.class, RETURNS_DEEP_STUBS);
        event.packet = mock(ClientMessage.class);
        Room room = mock(Room.class, RETURNS_DEEP_STUBS);
        HabboItem item = mock(HabboItem.class, RETURNS_DEEP_STUBS);
        Pet pet = mock(Pet.class, RETURNS_DEEP_STUBS);
        PetManager pets = mock(PetManager.class);
        GameEnvironment environment = mock(GameEnvironment.class);
        ConfigurationManager config = mock(ConfigurationManager.class);
        ThreadPooling threading = mock(ThreadPooling.class);
        when(event.client.getHabbo().getHabboInfo().getCurrentRoom()).thenReturn(room);
        when(event.client.getHabbo().getHabboInfo().getId()).thenReturn(42);
        when(event.packet.readInt()).thenReturn(123);
        when(event.packet.readString()).thenReturn(petName);
        when(room.getHabboItem(123)).thenReturn(item, null);
        when(item.getId()).thenReturn(123);
        when(item.getUserId()).thenReturn(itemOwner);
        when(item.getBaseItem().getName()).thenReturn(itemName);
        var tile = new RoomTile((short) 0, (short) 0, (short) 0, RoomTileState.OPEN, true);
        when(room.getLayout().getTile(anyShort(), anyShort())).thenReturn(tile);
        when(environment.getPetManager()).thenReturn(pets);
        when(pets.createPet(petType, petName, event.client)).thenReturn(pet);
        when(config.getInt("hotel.pets.name.length.min")).thenReturn(3);
        when(config.getInt("hotel.pets.name.length.max")).thenReturn(15);
        try (var emulator = mockStatic(Emulator.class)) {
            emulator.when(Emulator::getGameEnvironment).thenReturn(environment);
            emulator.when(Emulator::getConfig).thenReturn(config);
            emulator.when(Emulator::getThreading).thenReturn(threading);
            int previousMin = CheckPetNameEvent.PET_NAME_LENGTH_MINIMUM;
            int previousMax = CheckPetNameEvent.PET_NAME_LENGTH_MAXIMUM;
            CheckPetNameEvent.PET_NAME_LENGTH_MINIMUM = 3;
            CheckPetNameEvent.PET_NAME_LENGTH_MAXIMUM = 15;
            try {
                event.handle();
                event.handle();
                if (succeeds) {
                    verify(pets, times(1)).createPet(petType, petName, event.client);
                    verify(room, times(1)).removeHabboItem(item);
                    verify(pet).setUserId(42);
                    verify(threading, times(1)).runPersistence(any());
                } else {
                    verify(pets, never()).createPet(anyInt(), anyString(), any());
                    verify(room, never()).removeHabboItem(any());
                    verify(threading, never()).runPersistence(any());
                }
            } finally {
                CheckPetNameEvent.PET_NAME_LENGTH_MINIMUM = previousMin;
                CheckPetNameEvent.PET_NAME_LENGTH_MAXIMUM = previousMax;
            }
        }
    }
}
