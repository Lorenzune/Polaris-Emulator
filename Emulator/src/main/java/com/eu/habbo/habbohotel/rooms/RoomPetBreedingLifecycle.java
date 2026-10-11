package com.eu.habbo.habbohotel.rooms;

import com.eu.habbo.habbohotel.items.interactions.pets.InteractionPetBreedingNest;
import com.eu.habbo.habbohotel.pets.Pet;
import com.eu.habbo.habbohotel.pets.breeding.MonsterplantBreedingRequests;
import com.eu.habbo.habbohotel.users.Habbo;
import com.eu.habbo.habbohotel.users.HabboItem;
import com.eu.habbo.messages.outgoing.rooms.pets.breeding.PetBreedingStartComposer;

final class RoomPetBreedingLifecycle {
    private RoomPetBreedingLifecycle() {}
    static void leaving(Room room, Habbo habbo) {
        if (habbo == null) return;
        synchronized (room) {
            int owner = habbo.getHabboInfo().getId();
            for (HabboItem item : room.getRoomSpecialTypes().getItemsOfType(InteractionPetBreedingNest.class)) {
                ((InteractionPetBreedingNest) item).onOwnerLeave(room, owner);
            }
            var requests = MonsterplantBreedingRequests.getInstance();
            for (var request : requests.cancelOwner(owner, room.getId(), id -> {
                Pet pet = room.getPet(id); return pet == null ? 0 : pet.getUserId();
            })) {
                Pet asked = room.getPet(request.askedPetId());
                int[] owners = {request.requesterId(), asked == null ? 0 : asked.getUserId()};
                for (int id : owners) {
                    Habbo participant = room.getHabbo(id);
                    if (participant != null && participant.getClient() != null)
                        participant.getClient().sendResponse(new PetBreedingStartComposer(1, 0, 0));
                }
            }
        }
    }
}
